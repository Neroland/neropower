package za.co.neroland.neropower.beam;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.worldgen.SpaceTags;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.data.NeroPowerErasureState;

/**
 * Beam Transmitter (Stage 6) — takes NE in on every face and beams it, line of sight, to one
 * linked target every {@code beamCheckIntervalTicks}: a {@link BeamReceiverBlockEntity}, an
 * {@link OrbitalReceiverBlockEntity} or a {@link BeamRelayBlockEntity} (which forwards on to its
 * own target in the same pass, up to {@code beamMaxHops} deep). Distance costs
 * {@code beamLossPermillePerBlock} per block ({@link BeamMath}); anything solid in the beam
 * ({@link BeamPath}) blocks it outright; anything alive in it takes {@code beamDamage} a pass.
 *
 * <p><b>Link hygiene</b> (as NeroTech's wireless node): the target is validated on every pass and
 * dropped when the block is gone or is no longer a beam endpoint. An unloaded target simply skips
 * the pass — never force-loaded. Cross-dimension is allowed only onto an orbital receiver in a
 * space dimension, and only with {@code beamCrossDimension}.
 *
 * <p><b>Personal data</b> (POPIA/GDPR): besides the world-data target, the transmitter keeps the
 * <i>linking</i> player's UUID in its own {@code LinkOwner} NBT field — the authority for unlinking
 * — exposed as {@link #linkOwner()} / {@link #clearLinkOwner()}. Stage 8 erasure clears it through
 * {@link NeroPowerErasureState}: the eraser lists the UUID (30-day retention) and bumps an epoch,
 * and {@link #checkLinkOwnerErasure} drops a listed owner on the next tick (loaded) or the first
 * tick after load (unloaded) — the same epoch pattern NeroTech's base uses for {@code owner()}.
 * It is compared, never logged or synced.
 */
public class BeamTransmitterBlockEntity extends BeamEndpointBlockEntity {

    @Nullable
    private BeamTarget target;

    /** The linking player's UUID ({@code LinkOwner}); null when unlinked or never linked. */
    @Nullable
    private UUID linkOwner;

    /**
     * The {@link NeroPowerErasureState#erasureEpoch()} this block last checked its link owner
     * against; {@code -1} = never since (re)load, so a re-loaded chunk re-checks against erasures
     * that happened while it was unloaded.
     */
    private int linkOwnerErasureEpoch = -1;

    /** Spreads passes across ticks so a bank of transmitters never all fire on the same tick. */
    private final int phase = Math.floorMod(System.identityHashCode(this), 40);

    public BeamTransmitterBlockEntity(BlockPos pos, BlockState state) {
        this(BeamContent.BEAM_TRANSMITTER_BE.get(), pos, state);
    }

    protected BeamTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        // Pure sink on every face: the beam is the only way out.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_INPUT)
                .build());
    }

    /** Relay variant: explicit small pass-through buffer. */
    protected BeamTransmitterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
            int energyCapacity, int maxTransfer) {
        super(type, pos, state, energyCapacity, maxTransfer);
    }

    // --- link state --------------------------------------------------------------------------------

    /** The linked target, or null. */
    @Nullable
    public BeamTarget target() {
        return this.target;
    }

    /** A transmitter is a source only — nothing may be aimed at it (the relay overrides). */
    @Override
    public boolean acceptsBeam() {
        return false;
    }

    @Override
    public boolean linked() {
        return this.target != null;
    }

    /** The UUID of the player who made the current link — the unlink authority. */
    public Optional<UUID> linkOwner() {
        return Optional.ofNullable(this.linkOwner);
    }

    /** Forget the linking player (POPIA/GDPR erasure); the link itself stays. */
    public void clearLinkOwner() {
        if (this.linkOwner != null) {
            this.linkOwner = null;
            setChanged();
        }
    }

    /** Aim at {@code newTarget} on behalf of {@code player} (replaces any previous link). */
    public void link(BeamTarget newTarget, UUID player) {
        this.target = newTarget;
        this.linkOwner = player;
        this.distance = 0;
        this.lossPermille = 0;
        this.lastPass = 0L;
        setChanged();
    }

    /** Drop the link and its owner. */
    public void unlink() {
        this.target = null;
        this.linkOwner = null;
        this.distance = 0;
        this.lossPermille = 0;
        this.lastPass = 0L;
        setChanged();
    }

    // --- ticking -----------------------------------------------------------------------------------

    /**
     * Link-owner erasure (POPIA/GDPR): on the first server tick after (re)load, and again whenever an
     * erase request lands while this block is loaded, check the link owner against the pending set
     * and drop it if listed. Per tick this is one static int compare — the store lookup happens only
     * when the epoch moved and an owner is recorded.
     */
    private void checkLinkOwnerErasure(Level level) {
        int epoch = NeroPowerErasureState.erasureEpoch();
        if (this.linkOwnerErasureEpoch == epoch) {
            return;
        }
        this.linkOwnerErasureEpoch = epoch;
        if (this.linkOwner != null && level instanceof ServerLevel serverLevel
                && NeroPowerErasureState.isPending(serverLevel.getServer(), this.linkOwner)) {
            clearLinkOwner();
        }
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        checkLinkOwnerErasure(level);
        int interval = BeamConfig.beamCheckIntervalTicks();
        if ((level.getGameTime() + this.phase) % interval == 0) {
            transmit(level, pos, new HashSet<>(), 0);
        }
        syncLinkedState(level, pos, state);
        setActive(this.status == BeamStatus.TRANSMITTING);
    }

    /** Mirror the link onto the block state ({@code linked=true/false}) for the model swap. */
    protected void syncLinkedState(Level level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BeamTransmitterBlock.LINKED)
                && state.getValue(BeamTransmitterBlock.LINKED) != linked()) {
            level.setBlock(pos, state.setValue(BeamTransmitterBlock.LINKED, linked()), Block.UPDATE_ALL);
        }
    }

    /**
     * One beam pass from this block to its target. {@code visited} is the set of endpoints that have
     * already sent during this pass (loop guard across relays) and {@code hops} the chain depth.
     *
     * @return NE the target accepted
     */
    protected long transmit(Level level, BlockPos pos, Set<BlockPos> visited, int hops) {
        if (!NeroPowerConfig.transmissionEnabled()) {
            setStatus(BeamStatus.IDLE);
            return 0L;
        }
        BeamTarget aim = this.target;
        if (aim == null) {
            setStatus(BeamStatus.UNLINKED);
            return 0L;
        }
        if (hops >= BeamConfig.beamMaxHops() || !visited.add(pos.immutable())) {
            setStatus(BeamStatus.LOOP);
            return 0L;
        }

        // Resolve the target level: same dimension, or an orbital hop into a space dimension.
        boolean sameDimension = aim.sameDimension(level);
        Level targetLevel = level;
        if (!sameDimension) {
            if (!BeamConfig.beamCrossDimension() || !(level instanceof ServerLevel serverLevel)) {
                setStatus(BeamStatus.DENIED);
                return 0L;
            }
            ServerLevel other = serverLevel.getServer()
                    .getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(aim.dimension())));
            if (other == null) {
                setStatus(BeamStatus.TARGET_MISSING);
                return 0L;
            }
            if (!SpaceTags.isSpace(other)) {
                setStatus(BeamStatus.DENIED);
                return 0L;
            }
            targetLevel = other;
        }

        BlockPos targetPos = aim.pos();
        if (!targetLevel.hasChunkAt(targetPos)) {
            setStatus(BeamStatus.TARGET_ASLEEP); // never force-load
            return 0L;
        }
        if (!(targetLevel.getBlockEntity(targetPos) instanceof BeamEndpointBlockEntity endpoint)
                || !endpoint.acceptsBeam()) {
            unlink(); // target broken or replaced — the link is dead
            setStatus(BeamStatus.TARGET_MISSING);
            return 0L;
        }
        if (!sameDimension && !endpoint.orbital()) {
            setStatus(BeamStatus.DENIED);
            return 0L;
        }
        if (visited.contains(targetPos)) {
            setStatus(BeamStatus.LOOP); // the chain came back on itself this pass
            return 0L;
        }

        // Geometry: range + line of sight + distance loss in-dimension; a flat hop loss orbital.
        double factor;
        if (sameDimension) {
            if (!BeamMath.withinRange(pos.getX(), pos.getY(), pos.getZ(), targetPos.getX(), targetPos.getY(),
                    targetPos.getZ(), BeamConfig.beamRange())) {
                setStatus(BeamStatus.OUT_OF_RANGE);
                return 0L;
            }
            double blocks = BeamMath.distance(pos.getX(), pos.getY(), pos.getZ(), targetPos.getX(),
                    targetPos.getY(), targetPos.getZ());
            this.distance = (int) Math.round(blocks);
            if (!pathClear(level, pos, targetPos)) {
                this.lossPermille = BeamMath.PERMILLE;
                setStatus(BeamStatus.BLOCKED);
                return 0L;
            }
            factor = BeamMath.lossFactor(blocks, BeamConfig.beamLossPermillePerBlock());
            hurtEntitiesInBeam(level, pos, targetPos);
        } else {
            this.distance = 0;
            factor = BeamMath.orbitalFactor(BeamConfig.beamOrbitalHopLossPermille());
        }
        this.lossPermille = BeamMath.lossPermille(factor);

        // Energy: offer up to the per-pass budget, deliver what survives the loss, charge the source
        // for what the target actually kept.
        long offer = energyBuffer().extract(BeamMath.budget(BeamConfig.beamTransferPerPass(),
                energyBuffer().getAmount()), true);
        long deliverable = BeamMath.delivered(offer, factor);
        if (deliverable <= 0L) {
            this.lastPass = 0L;
            setStatus(BeamStatus.IDLE);
            return 0L;
        }
        NeroEnergyStorage sink = EnergyLookup.INSTANCE.find(targetLevel, targetPos, null);
        if (sink == null) {
            sink = endpoint.getEnergy();
        }
        long accepted = sink.insert(deliverable, false);
        if (accepted <= 0L) {
            this.lastPass = 0L;
            setStatus(BeamStatus.TARGET_FULL);
            return 0L;
        }
        energyBuffer().extract(BeamMath.sourceCost(accepted, factor, offer), false);
        this.lastPass = accepted;
        setStatus(BeamStatus.TRANSMITTING);
        endpoint.onBeamReceived(accepted, visited, hops + 1);
        return accepted;
    }

    /**
     * Whether every cell strictly between the two endpoints is air or another beam endpoint (a
     * relay in the line does not shadow a beam passing it). Unloaded cells are not inspected —
     * checking would load the chunk, which the beam never does.
     */
    private static boolean pathClear(Level level, BlockPos from, BlockPos to) {
        List<BeamPath.Cell> cells = BeamPath.cellsBetween(from.getX(), from.getY(), from.getZ(),
                to.getX(), to.getY(), to.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (BeamPath.Cell cell : cells) {
            cursor.set(cell.x(), cell.y(), cell.z());
            if (!level.hasChunkAt(cursor)) {
                continue;
            }
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) {
                continue;
            }
            if (level.getBlockEntity(cursor) instanceof BeamEndpointBlockEntity) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * The beam hazard: every living entity whose bounding box meets the segment's box (thickened by
     * half a block) takes {@code beamDamage} as magic damage. {@code 0} disables it. The box is an
     * approximation — a long diagonal beam's box is generous — which is the documented trade-off.
     */
    private static void hurtEntitiesInBeam(Level level, BlockPos from, BlockPos to) {
        double damage = BeamConfig.beamDamage();
        if (damage <= 0.0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        double ax = from.getX() + 0.5;
        double ay = from.getY() + 0.5;
        double az = from.getZ() + 0.5;
        double bx = to.getX() + 0.5;
        double by = to.getY() + 0.5;
        double bz = to.getZ() + 0.5;
        AABB box = new AABB(Math.min(ax, bx), Math.min(ay, by), Math.min(az, bz),
                Math.max(ax, bx), Math.max(ay, by), Math.max(az, bz)).inflate(0.5);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            entity.hurtServer(serverLevel, serverLevel.damageSources().magic(), (float) damage);
        }
    }

    // --- persistence: a block position + dimension id, and the linking player's UUID ---------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        BeamTarget.save(output, this.target);
        output.putLong("LinkOwnerMost", this.linkOwner == null ? 0L : this.linkOwner.getMostSignificantBits());
        output.putLong("LinkOwnerLeast", this.linkOwner == null ? 0L : this.linkOwner.getLeastSignificantBits());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.target = BeamTarget.load(input);
        long most = input.getLongOr("LinkOwnerMost", 0L);
        long least = input.getLongOr("LinkOwnerLeast", 0L);
        this.linkOwner = (most == 0L && least == 0L) ? null : new UUID(most, least);
        this.linkOwnerErasureEpoch = -1; // re-check against erasures that landed while unloaded
    }
}

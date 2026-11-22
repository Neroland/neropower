package za.co.neroland.neropower.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
//?}
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
//? if >=1.21.6 {
import net.minecraft.world.level.storage.TagValueInput;
//?}
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.beam.BeamSplitterBlockEntity;
import za.co.neroland.neropower.beam.BeamTarget;
import za.co.neroland.neropower.beam.BeamTransmitterBlockEntity;
import za.co.neroland.neropower.beam.PhasedArrayEmitterBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.fission.ReactorControlComputerBlockEntity;
import za.co.neroland.neropower.fission.ReactorMode;
import za.co.neroland.neropower.grid.CircuitBreakerBlockEntity;
import za.co.neroland.neropower.grid.LoadBankBlockEntity;
import za.co.neroland.neropower.machine.Exhibits;
import za.co.neroland.neropower.registry.ModTags;
import za.co.neroland.neropower.storage.BankControllerBlockEntity;
import za.co.neroland.neropower.storage.SmesControllerBlockEntity;
import za.co.neroland.neropower.turbine.TurbineHelmBlockEntity;

/**
 * Places {@link GalleryPlan} in a world, clears it again, and runs the tour. Creative and gamemaster checks
 * are the command's; this class acts at the origin it is given and records no player: beams are linked under
 * {@link NeroPowerCommands#GALLERY_LINK_TOKEN} (splitters and phased arrays keep no owner at all), and every
 * machine at a planned position is marked an {@linkplain Exhibits exhibit}, so it has no owner or operator,
 * credits no gate, trophy or quest, sends no alert or NeroLink event, gives off no radiation and never runs a
 * destructive failure action. The command never writes contamination; {@link #clear} removes only what the
 * gallery placed.
 */
public final class GalleryStation {

    /** Ticks after placement before the setup steps run (the structures form on their first ticks). */
    static final int SETUP_DELAY_TICKS = 5;
    /** Ticks after setup before the formed check reports. */
    static final int CHECK_DELAY_TICKS = 20;

    private GalleryStation() {
    }

    /** Whether every chunk the clear box touches is loaded. */
    public static boolean boxLoaded(ServerLevel level, BlockPos origin) {
        for (int x = origin.getX() + GalleryPlan.CLEAR_MIN_DX; x <= origin.getX() + GalleryPlan.CLEAR_MAX_DX + 15; x += 16) {
            for (int z = origin.getZ() + GalleryPlan.CLEAR_MIN_DZ; z <= origin.getZ() + GalleryPlan.CLEAR_MAX_DZ + 15; z += 16) {
                int cx = Math.min(x, origin.getX() + GalleryPlan.CLEAR_MAX_DX);
                int cz = Math.min(z, origin.getZ() + GalleryPlan.CLEAR_MAX_DZ);
                if (!level.isLoaded(new BlockPos(cx, origin.getY(), cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether Unit 1 gets six rods: the overworld's air cools a Steam Condenser well; elsewhere two. */
    static boolean fullRods(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD;
    }

    /**
     * Build the station at {@code origin}. Clears the box first (so a second build never stacks), places the
     * parts, then the controllers, marks every machine an exhibit, and queues the setup and the check.
     *
     * @param report receives one line per structure that did not form, after the check
     */
    public static int build(ServerLevel level, BlockPos origin, java.util.function.Consumer<Component> report) {
        clear(level, origin);
        GalleryPlan plan = GalleryPlan.create(fullRods(level));
        int placed = 0;
        for (int phase = GalleryPlan.PHASE_PARTS; phase <= GalleryPlan.PHASE_CONTROLLERS; phase++) {
            for (GalleryPlan.Place p : plan.places()) {
                if (p.phase() != phase) {
                    continue;
                }
                BlockState state = stateOf(p.id(), p.props());
                if (state == null) {
                    continue; // another mod's prop that is not installed
                }
                BlockPos pos = origin.offset(p.dx(), p.dy(), p.dz());
                level.setBlock(pos, state, Block.UPDATE_ALL);
                placed++;
            }
            markExhibits(level, origin, plan);
        }
        settleCoilShapes(level, origin, plan);
        for (GalleryPlan.Label label : plan.labelList()) {
            spawnLabel(level, origin, label);
        }
        GalleryTasks.schedule(level, SETUP_DELAY_TICKS, () -> {
            markExhibits(level, origin, plan);
            for (GalleryPlan.Setup s : plan.setupList()) {
                try {
                    apply(level, origin, s);
                } catch (RuntimeException ex) {
                    // One exhibit failing to set up must not stop the rest (no player data in this log line).
                    za.co.neroland.neropower.NeroPowerCommon.LOGGER.warn("[NeroPower] gallery setup {} failed: {}",
                            s.kind(), ex.toString());
                }
            }
        });
        GalleryTasks.schedule(level, SETUP_DELAY_TICKS + CHECK_DELAY_TICKS, () -> {
            for (GalleryPlan.Controller c : plan.controllerList()) {
                if (!formed(level.getBlockEntity(origin.offset(c.dx(), c.dy(), c.dz())))) {
                    report.accept(Component.translatable("command.neropower.gallery.not_formed", c.what()));
                }
            }
            for (GalleryPlan.Setup s : plan.setupList()) {
                if (s.kind() == GalleryPlan.Kind.SEALED
                        && barrierFaces(level, origin.offset(s.dx(), s.dy(), s.dz())) < GalleryPlan.SEALED_FACES) {
                    report.accept(Component.translatable("command.neropower.gallery.not_sealed"));
                }
            }
        });
        return placed;
    }

    /**
     * Remove the station built at {@code origin}, and only what it placed: at every position the plan
     * fills, a block is removed only while it is still the planned block and, for a machine, still an
     * exhibit ({@link GalleryPlan#removable}); anything else stays. Containers are emptied first (so no rod
     * or residue ever drops), blocks go to air without neighbour updates, and only the gallery's own
     * labels (invisible marker stands named with a plan label) are discarded. Contamination is left alone:
     * the gallery never writes any, so there is none of its own to remove.
     *
     * @return {@code {blocks cleared, entities removed}}
     */
    public static int[] clear(ServerLevel level, BlockPos origin) {
        GalleryPlan plan = GalleryPlan.create(fullRods(level));
        List<BlockPos> remove = new ArrayList<>();
        for (GalleryPlan.Place p : plan.places()) {
            BlockState planned = stateOf(p.id(), p.props());
            if (planned == null || planned.isAir()) {
                continue;
            }
            BlockPos pos = origin.offset(p.dx(), p.dy(), p.dz());
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            boolean machine = be instanceof NeroTechMachineBlockEntity;
            if (GalleryPlan.removable(level.getBlockState(pos).is(planned.getBlock()), machine, Exhibits.is(be))) {
                remove.add(pos);
            }
        }
        for (BlockPos pos : remove) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                container.clearContent();
            }
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        int cleared = 0;
        for (BlockPos pos : remove) {
            // flag 2 = notify clients, skip the neighbour cascade (no drops, no water flow).
            level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            cleared++;
        }
        java.util.Set<String> labels = new java.util.HashSet<>();
        for (GalleryPlan.Label label : plan.labelList()) {
            labels.add(label.text());
        }
        AABB box = new AABB(origin.getX() + GalleryPlan.CLEAR_MIN_DX, origin.getY() + GalleryPlan.CLEAR_MIN_DY - 1,
                origin.getZ() + GalleryPlan.CLEAR_MIN_DZ, origin.getX() + GalleryPlan.CLEAR_MAX_DX + 1,
                origin.getY() + GalleryPlan.CLEAR_MAX_DY + 4, origin.getZ() + GalleryPlan.CLEAR_MAX_DZ + 1);
        int removed = 0;
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box,
                s -> s.isInvisible() && s.getCustomName() != null && labels.contains(s.getCustomName().getString()))) {
            stand.discard();
            removed++;
        }
        return new int[] {cleared, removed};
    }

    /**
     * Find a gallery near {@code near} whose origin is not remembered (after a restart): the loaded block
     * entities within {@code chunkRadius} chunks are searched for an exhibit that, taken as one of the plan's
     * controllers, puts every controller on an exhibit ({@link GalleryPlan#originConfirmed}). Never loads a
     * chunk. {@code null} when no gallery stands there.
     */
    @Nullable
    public static BlockPos locate(ServerLevel level, BlockPos near, int chunkRadius) {
        GalleryPlan plan = GalleryPlan.create(fullRods(level));
        int ccx = near.getX() >> 4;
        int ccz = near.getZ() >> 4;
        for (int cx = ccx - chunkRadius; cx <= ccx + chunkRadius; cx++) {
            for (int cz = ccz - chunkRadius; cz <= ccz + chunkRadius; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (!Exhibits.is(be)) {
                        continue;
                    }
                    BlockPos at = be.getBlockPos();
                    for (GalleryPlan.Controller c : plan.controllerList()) {
                        BlockPos origin = at.offset(-c.dx(), -c.dy(), -c.dz());
                        if (plan.originConfirmed(o -> Exhibits.at(level, origin.offset(o[0], o[1], o[2])))) {
                            return origin.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    // --- placement -----------------------------------------------------------------------------------

    @Nullable
    static BlockState stateOf(String id, Map<String, String> props) {
        Block block;
        if ("minecraft:air".equals(id)) {
            block = Blocks.AIR;
        } else {
            block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            if (block == null || block == Blocks.AIR) {
                return null;
            }
        }
        BlockState state = block.defaultBlockState();
        for (Map.Entry<String, String> e : props.entrySet()) {
            state = withProperty(state, e.getKey(), e.getValue());
        }
        return state;
    }

    private static BlockState withProperty(BlockState state, String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return state;
        }
        return setParsed(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState setParsed(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }

    /** Re-run each Storage Ring Coil's shape from its neighbours, now that the whole ring stands. */
    private static void settleCoilShapes(ServerLevel level, BlockPos origin, GalleryPlan plan) {
        for (GalleryPlan.Place p : plan.places()) {
            if (!p.id().endsWith(":smes_coil")) {
                continue;
            }
            BlockPos pos = origin.offset(p.dx(), p.dy(), p.dz());
            BlockState state = level.getBlockState(pos);
            BlockState settled = Block.updateFromNeighbourShapes(state, level, pos);
            if (settled != state) {
                level.setBlock(pos, settled, Block.UPDATE_ALL);
            }
        }
    }

    /** Mark every NeroTech-family machine in the box a gallery exhibit (drops any owner and operator). */
    /** Mark the machines at the plan's own positions as exhibits; a machine anywhere else is never touched. */
    static int markExhibits(ServerLevel level, BlockPos origin, GalleryPlan plan) {
        int marked = 0;
        for (GalleryPlan.Place p : plan.places()) {
            BlockEntity be = level.getBlockEntity(origin.offset(p.dx(), p.dy(), p.dz()));
            if (be != null && Exhibits.mark(be)) {
                marked++;
            }
        }
        return marked;
    }

    // --- setup ---------------------------------------------------------------------------------------

    private static void apply(ServerLevel level, BlockPos origin, GalleryPlan.Setup s) {
        BlockPos pos = origin.offset(s.dx(), s.dy(), s.dz());
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return;
        }
        switch (s.kind()) {
            case ITEMS -> insertItems(be, s.arg(), (int) s.count());
            case FLUID -> fillFluid(be, s.arg(), s.count());
            case STEAM_MODE -> {
                if (be instanceof FissionCoreBlockEntity core) {
                    core.requestMode(ReactorMode.STEAM);
                }
            }
            case AUTO_SCRAM -> {
                if (be instanceof ReactorControlComputerBlockEntity computer) {
                    computer.setAutoScram(true);
                    computer.setAutoScramAt((int) s.count());
                }
            }
            case CHARGE_PERMILLE -> {
                if (be instanceof NeroTechMachineBlockEntity machine) {
                    charge(machine.getEnergy(), s.count());
                }
            }
            case BEAM_LINK -> {
                if (be instanceof BeamTransmitterBlockEntity transmitter) {
                    transmitter.link(BeamTarget.of(target(origin, s), level), NeroPowerCommands.GALLERY_LINK_TOKEN);
                }
            }
            case SPLITTER_LINK -> {
                if (be instanceof BeamSplitterBlockEntity splitter && splitter.addLink(BeamTarget.of(target(origin, s), level))) {
                    splitter.setShare(splitter.links().size() - 1, (int) s.count());
                }
            }
            case ARRAY_LINK -> {
                if (be instanceof PhasedArrayEmitterBlockEntity array) {
                    array.addLink(BeamTarget.of(target(origin, s), level));
                }
            }
            case BREAKER_LIMIT -> {
                if (be instanceof CircuitBreakerBlockEntity breaker) {
                    breaker.setLimit(s.count());
                }
            }
            case LOAD_RATE -> {
                if (be instanceof LoadBankBlockEntity load) {
                    load.setRate(s.count());
                }
            }
            case SEALED -> {
                // checked after setup (see build)
            }
            default -> {
            }
        }
        be.setChanged();
    }

    private static BlockPos target(BlockPos origin, GalleryPlan.Setup s) {
        return origin.offset(s.target()[0], s.target()[1], s.target()[2]);
    }

    /** Put {@code count} of {@code id} into the first slots that accept it (stacking onto equal stacks). */
    static int insertItems(BlockEntity be, String id, int count) {
        if (!(be instanceof Container container)) {
            return 0;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null || item == Items.AIR) {
            return 0;
        }
        int left = count;
        for (int slot = 0; slot < container.getContainerSize() && left > 0; slot++) {
            ItemStack probe = new ItemStack(item);
            if (!container.canPlaceItem(slot, probe)) {
                continue;
            }
            ItemStack in = container.getItem(slot);
            int limit = Math.min(container.getMaxStackSize(), probe.getMaxStackSize());
            if (in.isEmpty()) {
                int n = Math.min(left, limit);
                container.setItem(slot, new ItemStack(item, n));
                left -= n;
            } else if (ItemStack.isSameItemSameComponents(in, probe) && in.getCount() < limit) {
                int n = Math.min(left, limit - in.getCount());
                ItemStack grown = in.copy();
                grown.grow(n);
                container.setItem(slot, grown);
                left -= n;
            }
        }
        return count - left;
    }

    private static void fillFluid(BlockEntity be, String id, long amount) {
        if (!(be instanceof NeroTechMachineBlockEntity machine)) {
            return;
        }
        Fluid fluid = fluidOf(id);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return;
        }
        NeroFluidStorage storage = machine.fluidStorage(null);
        if (storage == null) {
            return;
        }
        long left = amount;
        for (int i = 0; i < 64 && left > 0; i++) {
            long moved = storage.fill(fluid, left, false);
            if (moved <= 0) {
                break;
            }
            left -= moved;
        }
    }

    @Nullable
    private static Fluid fluidOf(String id) {
        if (!id.startsWith("#")) {
            return BuiltInRegistries.FLUID.getValue(Identifier.parse(id));
        }
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, Identifier.parse(id.substring(1)));
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid.defaultFluidState().isSource() && fluid.defaultFluidState().is(tag)) {
                return fluid;
            }
        }
        return null;
    }

    private static void charge(NeroEnergyStorage energy, long permille) {
        long goal = energy.getCapacity() / 1000L * Math.max(0, Math.min(1000, permille));
        for (int i = 0; i < 100_000 && energy.getAmount() < goal; i++) {
            if (energy.insert(goal - energy.getAmount(), false) <= 0) {
                break;
            }
        }
    }

    private static boolean formed(@Nullable BlockEntity be) {
        if (be instanceof FissionCoreBlockEntity core) {
            return core.formed();
        }
        if (be instanceof TurbineHelmBlockEntity helm) {
            return helm.formed();
        }
        if (be instanceof BankControllerBlockEntity bank) {
            return bank.formed();
        }
        if (be instanceof SmesControllerBlockEntity ring) {
            return ring.core().formed();
        }
        return false;
    }

    /** How many of a block's six neighbours are radiation barriers. */
    static int barrierFaces(Level level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(ModTags.RADIATION_BARRIER)) {
                n++;
            }
        }
        return n;
    }

    // --- labels --------------------------------------------------------------------------------------

    /** A floating marker label (NeroTech's label-stand recipe). */
    private static void spawnLabel(ServerLevel level, BlockPos origin, GalleryPlan.Label label) {
        double x = origin.getX() + label.dx() + 0.5;
        double y = origin.getY() + label.dy();
        double z = origin.getZ() + label.dz() + 0.5;
        ArmorStand stand = new ArmorStand(level, x, y, z);
        // Marker = zero-size bounding box, so an invisible stand never hijacks pick-block. setMarker is
        // private on 26.x, so the flag goes in through Entity#load.
        CompoundTag markerTag = new CompoundTag();
        markerTag.putBoolean("Marker", true);
        //? if >=1.21.6 {
        stand.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), markerTag));
        //?} else {
        /*stand.load(markerTag);
        *///?}
        stand.refreshDimensions();
        stand.setPos(x, y, z); // load() zeroed Pos
        stand.setCustomName(Component.literal(label.text()));
        stand.setCustomNameVisible(true);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        //? if >=26.3 {
        /*stand.setPermanentlyInvulnerable(true);
        *///?} else {
        stand.setInvulnerable(true);
        //?}
        level.addFreshEntity(stand);
    }
}

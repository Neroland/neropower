package za.co.neroland.neropower.turbine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.Explode;
import za.co.neroland.neropower.failure.FailureContext;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;
import za.co.neroland.neropower.fluid.NeroPowerFluids;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.LevelProbe;
import za.co.neroland.neropower.multiblock.MultiblockHost;
import za.co.neroland.neropower.multiblock.MultiblockIndex;
import za.co.neroland.neropower.multiblock.Probe;
import za.co.neroland.neropower.multiblock.ShapeResult;
import za.co.neroland.neropower.multiblock.TurbineShape;
import za.co.neroland.neropower.progression.MilestoneGates;
import za.co.neroland.neropower.progression.OperatorCredit;
import za.co.neroland.neropower.registry.content.TurbineContent;

/**
 * The steam turbine controller. Owns the whole turbine's state: the formed shape (validated on change
 * through {@link MultiblockIndex}, never on a cadence), the rotor speed, the latched trip and the energy
 * buffer. Each tick it draws live steam from its inlet ports, converts it at the vane sets' efficiency,
 * cuts the power to the stators' ceiling and the buffer's room, scales it by rotor speed
 * ({@link TurbineMath}), and sends the same mB of exhaust steam to its exhaust ports (or vents it).
 *
 * <p><b>Inertia.</b> The rotor spins up by at most {@code turbineSpinUpRpmPerTick} and coasts down by at
 * most {@code turbineCoastDownRpmPerTick}; steam spent below rated speed is partly wasted and a coasting
 * rotor makes nothing. <b>Overspeed.</b> When the stators or buffer cannot take the power the rotor runs
 * toward 2400 rpm; its overspeed stress climbs the shared failure ladder (telegraphed: alarm state,
 * sound, particles, NeroTech's failure bus, the owner's alert) and FAILURE trips the turbine
 * ({@code turbineFailureAction}). A Governor Module keeps the rotor at rated speed instead.
 *
 * <p>Privacy: no player data is stored; the spin-up record gate credits the operator in the moment.
 */
public class TurbineHelmBlockEntity extends NeroPowerMachineBlockEntity implements MultiblockHost {

    /** Record gate opened the first time a turbine delivers power at rated speed. */
    public static final Identifier GATE_TURBINE_SPINUP = MilestoneGates.TURBINE_SPINUP;

    /** The helm's own NE buffer and per-side push budget. */
    public static final int BUFFER_NE = 200_000;
    public static final int IO_NE = 16_000;

    /** Stall causes (synced, 0-15; the shared screen table). */
    public static final int STALL_NONE = 0;
    public static final int STALL_NOT_FORMED = 1;
    public static final int STALL_NO_STEAM = 3;
    public static final int STALL_EXHAUST_BLOCKED = 5;
    public static final int STALL_BUFFER_FULL = 8;
    /** Formed, but no slice can work: no vane set on an open slice, or no fully wound stator ring. */
    public static final int STALL_NO_WORKING_SLICE = 14;

    /** Machine-specific synced ints after NeroTech's seven ({@code P} = a hi/lo pair of two ints). */
    public static final int EXTRA_RPM = 0;
    public static final int EXTRA_TRIP_RPM = 1;
    public static final int EXTRA_WARN_RPM = 2;
    public static final int EXTRA_UNSTABLE_RPM = 3;
    public static final int EXTRA_TRIPPED = 4;
    public static final int EXTRA_FAILURE_STAGE = 5;
    public static final int EXTRA_FAILURE_ACTION = 6;
    public static final int EXTRA_STEAM_P = 7;
    public static final int EXTRA_STEAM_CAPACITY_P = 9;
    public static final int EXTRA_EXHAUST_P = 11;
    public static final int EXTRA_EXHAUST_CAPACITY_P = 13;
    public static final int EXTRA_STEAM_USED_P = 15;
    public static final int EXTRA_FLOW_CAP_P = 17;
    public static final int EXTRA_EFFICIENCY = 19;
    public static final int EXTRA_EXHAUST_FACTOR = 20;
    public static final int EXTRA_VENT_EFFICIENCY = 21;
    public static final int EXTRA_OUTPUT_P = 22;
    public static final int EXTRA_CEILING_P = 24;
    public static final int EXTRA_WOUND_STATORS = 26;
    public static final int EXTRA_GOVERNOR = 27;
    public static final int EXTRA_PORTS_INLET = 28;
    public static final int EXTRA_PORTS_EXHAUST = 29;
    public static final int EXTRA_PORTS_VENT = 30;
    public static final int EXTRA_SLICE_COUNT = 31;
    /** Three ints, five 3-bit slice codes each (see {@link #sliceCode}). */
    public static final int EXTRA_SLICE_CODES = 32;
    /** Three ints: vane flow per slice for steel, cindersteel, superconducting. */
    public static final int EXTRA_TIER_FLOW = 35;
    /** Three ints: vane efficiency permille per tier. */
    public static final int EXTRA_TIER_EFF = 38;
    /** Three ints: ceiling per wound stator for tiers 1-3. */
    public static final int EXTRA_TIER_CEILING = 41;
    public static final int EXTRA_FORMED = 44;
    public static final int EXTRA_READOUT = 45;
    public static final int EXTRA_STALL = 46;
    public static final int EXTRA_DATA = 47;

    /** Slice codes: 0 invalid / incomplete, 1-3 vane tier, 4-6 stator tier, 7 bare spindle. */
    public static final int SLICE_INVALID = 0;
    public static final int SLICE_BARE = 7;
    public static final int MAX_SLICES = TurbineShape.MAX_LENGTH - 2;

    private static final int SHORT_MAX = Short.MAX_VALUE;
    private static final int VENT_PARTICLE_TICKS = 10;

    // --- structure (rebuilt on change; not saved except the length) --------------------------------
    private boolean recheckDue = true;
    private int length;
    @Nullable
    private Bounds bounds;
    private final List<BlockPos> ports = new ArrayList<>();
    private final List<BlockPos> spindles = new ArrayList<>();
    private final Map<VaneTier, Integer> vaneSlices = new EnumMap<>(VaneTier.class);
    private final Map<WindingTier, Integer> windings = new EnumMap<>(WindingTier.class);
    private int statorSlices;
    private final int[] sliceCodes = new int[MAX_SLICES];
    private int portsInlet;
    private int portsExhaust;
    private int portsVent;
    private long steamStored;
    private long steamCapacity;
    private long exhaustStored;
    private long exhaustCapacity;

    // --- rotor ---------------------------------------------------------------------------------------
    private int rpm;
    private boolean tripped;
    private boolean lastRedstone;
    private TurbineMath.Plan lastPlan = TurbineMath.Plan.IDLE;
    private long lastOutput;
    private int stallCause = STALL_NOT_FORMED;
    private int readout = TurbineReadout.REST;
    /** The spin-up gate has been offered by this loaded helm (transient; the gate itself is idempotent). */
    private boolean spinUpRecorded;
    /** The operator this loaded helm last tried to credit with spin-up. Transient, equality only; never log it. */
    @Nullable
    private UUID spinUpTried;

    // --- render sync (compare-and-record) -------------------------------------------------------------
    private int syncedBucket = -1;
    private boolean syncedTripped;
    private int syncedReadout = -1;
    private int syncedStall = -1;
    @Nullable
    private Bounds syncedBounds;

    public TurbineHelmBlockEntity(BlockPos pos, BlockState state) {
        super(TurbineContent.TURBINE_HELM_TYPE.get(), pos, state, 0, BUFFER_NE, IO_NE);
        // GENERATOR preset: energy out on every face, auto-ejected.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    // --- structure ------------------------------------------------------------------------------------

    @Override
    public void markStructureDirty() {
        this.recheckDue = true;
    }

    /** The block fires this on every neighbour change: also a structure recheck. */
    @Override
    public void invalidateThermalLinks() {
        super.invalidateThermalLinks();
        this.recheckDue = true;
    }

    /** Whether the turbine is formed. */
    public boolean formed() {
        return this.bounds != null && this.length >= TurbineShape.MIN_LENGTH;
    }

    /** How the turbine shape sees a block state. */
    public static Cell cellOf(BlockState state) {
        if (state.isAir()) {
            return Cell.AIR;
        }
        if (state.getBlock() instanceof TurbineSpindleBlock) {
            return Cell.TURBINE_SPINDLE;
        }
        if (state.getBlock() instanceof TurbineStatorBlock) {
            return Cell.TURBINE_STATOR;
        }
        if (state.getBlock() instanceof TurbineSteamPortBlock) {
            return Cell.TURBINE_STEAM_PORT;
        }
        if (state.getBlock() instanceof TurbineHelmBlock) {
            return Cell.TURBINE_HELM;
        }
        if (state.is(TurbineContent.TURBINE_SHROUD.get())) {
            return Cell.TURBINE_SHROUD;
        }
        return Cell.OTHER;
    }

    private void revalidate(Level level, BlockPos pos, BlockState state) {
        if (!this.recheckDue || !MultiblockIndex.tryRevalidate(level.dimension(), level.getGameTime())) {
            return;
        }
        this.recheckDue = false;
        Direction body = state.getValue(NeroTechMachineBlock.FACING).getOpposite();
        Set<BlockPos> found = new LinkedHashSet<>();
        LevelProbe live = new LevelProbe(level, TurbineHelmBlockEntity::cellOf);
        Probe probe = new Probe() {
            @Override
            public Cell at(int x, int y, int z) {
                Cell cell = live.at(x, y, z);
                if (cell == Cell.TURBINE_STEAM_PORT) {
                    found.add(new BlockPos(x, y, z));
                }
                return cell;
            }

            @Override
            public boolean loaded(Bounds b) {
                return live.loaded(b);
            }
        };
        ShapeResult result = TurbineShape.evaluate(probe, pos.getX(), pos.getY(), pos.getZ(),
                body.getStepX(), body.getStepY(), body.getStepZ());

        Bounds before = this.bounds;
        int lengthBefore = this.length;
        this.ports.clear();
        this.spindles.clear();
        this.vaneSlices.clear();
        this.windings.clear();
        this.statorSlices = 0;
        java.util.Arrays.fill(this.sliceCodes, SLICE_INVALID);
        if (result.valid()) {
            this.bounds = result.bounds();
            this.length = result.size();
            this.ports.addAll(found);
            readSlices(level, pos, body);
            MultiblockIndex.register(level.dimension(), pos.asLong(), result.bounds(), this::markStructureDirty);
        } else {
            this.bounds = null;
            this.length = 0;
            MultiblockIndex.unregister(level.dimension(), pos.asLong());
        }
        if (this.length != lengthBefore || !java.util.Objects.equals(before, this.bounds)) {
            setChanged();
        }
    }

    /** Read each interior slice: the spindle's vanes (air ring) or the eight stators' windings. */
    private void readSlices(Level level, BlockPos helm, Direction body) {
        int[] u = body.getStepX() != 0 ? new int[] {0, 1, 0} : new int[] {1, 0, 0};
        int[] v = body.getStepX() != 0 ? new int[] {0, 0, 1} : new int[] {0, 1, 0};
        for (int k = 1; k < this.length - 1; k++) {
            BlockPos spindle = helm.relative(body, k);
            this.spindles.add(spindle);
            BlockState ring = level.getBlockState(spindle.offset(u[0], u[1], u[2]));
            VaneTier vanes = TurbineSpindleBlock.vanes(level.getBlockState(spindle));
            int code;
            if (ring.getBlock() instanceof TurbineStatorBlock) {
                this.statorSlices++;
                int lowest = WindingTier.TIER3.ordinal();
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i == 0 && j == 0) {
                            continue;
                        }
                        BlockState stator = level.getBlockState(spindle.offset(
                                u[0] * i + v[0] * j, u[1] * i + v[1] * j, u[2] * i + v[2] * j));
                        WindingTier winding = TurbineStatorBlock.winding(stator);
                        this.windings.merge(winding, 1, Integer::sum);
                        lowest = Math.min(lowest, winding.ordinal());
                    }
                }
                // A vaned spindle inside a stator ring is blocked; an unwound stator leaves the slice incomplete.
                code = vanes != VaneTier.NONE || lowest == 0 ? SLICE_INVALID : 3 + lowest;
            } else {
                // A vane slice: only vanes on an open ring turn steam.
                this.vaneSlices.merge(vanes, 1, Integer::sum);
                code = vanes == VaneTier.NONE ? SLICE_BARE : vanes.ordinal();
            }
            if (k - 1 < MAX_SLICES) {
                this.sliceCodes[k - 1] = code;
            }
        }
    }

    /** The rotor aggregate from the cached slices and the live config (hot-reload safe). */
    public TurbineMath.Rotor rotor() {
        if (!formed()) {
            return TurbineMath.Rotor.NONE;
        }
        List<Integer> flows = new ArrayList<>(4);
        List<Integer> effs = new ArrayList<>(4);
        for (Map.Entry<VaneTier, Integer> entry : this.vaneSlices.entrySet()) {
            if (entry.getKey() == VaneTier.NONE) {
                continue;
            }
            flows.add(TurbineConfig.vaneFlow(entry.getKey()) * entry.getValue());
            effs.add(TurbineConfig.vaneEfficiency(entry.getKey()));
        }
        long ceiling = 0L;
        for (Map.Entry<WindingTier, Integer> entry : this.windings.entrySet()) {
            ceiling += (long) TurbineConfig.statorCeiling(entry.getKey()) * entry.getValue();
        }
        int[] f = flows.stream().mapToInt(Integer::intValue).toArray();
        int[] e = effs.stream().mapToInt(Integer::intValue).toArray();
        return TurbineMath.Rotor.of(f, e, ceiling);
    }

    /** Whether a Governor Module sits in an upgrade slot. */
    public boolean governorFitted() {
        for (int i = 0; i < upgrades().slots(); i++) {
            if (GovernorModuleItem.is(upgrades().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    /** The helm's upgrade slots also take the Governor Module (Core's classifier does not know it). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return super.canPlaceItem(slot, stack) || (slot >= this.machineSlots && GovernorModuleItem.is(stack));
    }

    /** Inlet and exhaust ports served on the last tick: the members of the shared tanks. */
    private List<TurbineSteamPortBlockEntity> linkedInlets = List.of();
    private List<TurbineSteamPortBlockEntity> linkedExhausts = List.of();

    /** The ports sharing this turbine's steam-in ({@code INLET}) or exhaust ({@code EXHAUST}) tank. */
    public List<TurbineSteamPortBlockEntity> linkedPorts(PortMode mode) {
        return switch (mode) {
            case INLET -> this.linkedInlets;
            case EXHAUST -> this.linkedExhausts;
            case VENT -> List.of();
        };
    }

    // --- tick -------------------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        revalidate(level, pos, state);

        List<TurbineSteamPortBlockEntity> inlets = new ArrayList<>(4);
        List<TurbineSteamPortBlockEntity> exhausts = new ArrayList<>(4);
        List<TurbineSteamPortBlockEntity> vents = new ArrayList<>(2);
        long steam = 0L;
        long steamCapacity = 0L;
        long exhaustRoom = 0L;
        if (formed()) {
            for (BlockPos portPos : this.ports) {
                if (!(level.getBlockEntity(portPos) instanceof TurbineSteamPortBlockEntity port)) {
                    this.recheckDue = true;
                    continue;
                }
                port.link(this);
                switch (port.mode()) {
                    case INLET -> {
                        inlets.add(port);
                        steam += port.steamTank().getAmount();
                        steamCapacity += port.steamTank().getCapacity();
                    }
                    case EXHAUST -> {
                        exhausts.add(port);
                        exhaustRoom += port.exhaustTank().room();
                    }
                    case VENT -> vents.add(port);
                }
            }
        }
        this.linkedInlets = List.copyOf(inlets);
        this.linkedExhausts = List.copyOf(exhausts);
        this.portsInlet = inlets.size();
        this.portsExhaust = exhausts.size();
        this.portsVent = vents.size();
        this.steamStored = steam;
        this.steamCapacity = steamCapacity;
        long exhaustStored = 0L;
        long exhaustCapacity = 0L;
        for (TurbineSteamPortBlockEntity port : exhausts) {
            exhaustStored += port.exhaustTank().getAmount();
            exhaustCapacity += port.exhaustTank().getCapacity();
        }

        TurbineMath.Rotor rotor = rotor();
        long room = Math.max(0L, getEnergy().getCapacity() - getEnergy().getAmount());
        TurbineMath.Plan plan = formed() && rotor.runnable()
                ? TurbineMath.plan(rotor, steam, exhaustRoom, !vents.isEmpty(),
                        TurbineConfig.ventEfficiencyPermille(), TurbineConfig.nePerSteamMb(), room, governorFitted(),
                        this.tripped)
                : TurbineMath.Plan.IDLE;

        moveSteam(level, plan, inlets, exhausts, vents);
        this.exhaustStored = exhaustStored + plan.condensed();
        this.exhaustCapacity = exhaustCapacity;

        this.rpm = TurbineMath.stepRpm(this.rpm, plan.targetRpm(), TurbineConfig.spinUpRpmPerTick(),
                TurbineConfig.coastDownRpmPerTick());
        long output = TurbineMath.output(plan.extracted(), this.rpm, failureOutputPermille());
        if (output > 0L) {
            energyBuffer().generate((int) Math.min(Integer.MAX_VALUE, output));
        }
        this.lastPlan = plan;
        this.lastOutput = output;

        // The overspeed ladder. It always runs (a trip is an interlock, not a meltdown); with
        // overloadEnabled=false the destructive actions degrade to a plain trip.
        int stress = TurbineMath.overspeedPermille(this.rpm, TurbineConfig.tripRpm());
        FailureStage stage = failure().tick(stress, FailureThresholds.PERMILLE, true);
        if (stage == FailureStage.FAILURE && failure().justEntered() && level instanceof ServerLevel serverLevel) {
            if (onOverspeed(serverLevel, pos)) {
                return; // the shroud burst and took the helm with it
            }
        }

        boolean spunUp = OperatorCredit.spunUp(output, this.rpm, TurbineMath.RATED_RPM);
        if (spunUp && level instanceof ServerLevel serverLevel) {
            if (!this.spinUpRecorded) {
                this.spinUpRecorded = true;
                MilestoneGates.record(serverLevel, ownerId(), GATE_TURBINE_SPINUP);
            }
            UUID operator = lastOperator().orElse(null);
            if (OperatorCredit.shouldTry(true, operator, this.spinUpTried) && MilestoneGates.online(serverLevel, operator)) {
                this.spinUpTried = operator;
                MilestoneGates.credit(serverLevel, operator, GATE_TURBINE_SPINUP);
            }
        }

        updateReadout(steam, rotor, plan, output, stage);
        setActive(this.rpm > 0);
        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), Math.max(IO_NE, NeroTechConfig.machineMaxTransfer()),
                sideConfig());
    }

    /** Drain the passed steam from the inlets and send the exhaust to the exhaust ports or the air. */
    private void moveSteam(Level level, TurbineMath.Plan plan, List<TurbineSteamPortBlockEntity> inlets,
            List<TurbineSteamPortBlockEntity> exhausts, List<TurbineSteamPortBlockEntity> vents) {
        long draw = plan.steamPassed();
        for (TurbineSteamPortBlockEntity port : inlets) {
            if (draw <= 0L) {
                break;
            }
            long took = port.steamTank().drain(draw, false);
            if (took > 0L) {
                draw -= took;
                port.noteFlow();
            }
        }
        long out = plan.condensed();
        for (TurbineSteamPortBlockEntity port : exhausts) {
            if (out <= 0L) {
                break;
            }
            long put = port.exhaustTank().fill(NeroPowerFluids.EXHAUST_STEAM.get(), out, false);
            if (put > 0L) {
                out -= put;
                port.noteFlow();
            }
        }
        if (plan.vented() > 0L && !vents.isEmpty()) {
            boolean puff = level.getGameTime() % VENT_PARTICLE_TICKS == 0L && level instanceof ServerLevel;
            for (TurbineSteamPortBlockEntity port : vents) {
                port.noteFlow();
                if (puff) {
                    BlockPos p = port.getBlockPos();
                    ((ServerLevel) level).sendParticles(za.co.neroland.neropower.registry.ModParticles.STEAM_PLUME.get(),
                            p.getX() + 0.5D, p.getY() + 1.05D, p.getZ() + 0.5D, 3, 0.25D, 0.1D, 0.25D, 0.02D);
                    level.playSound(null, p, za.co.neroland.neropower.registry.ModSounds.STEAM_VENT.get(),
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.0F);
                }
            }
        }
    }

    /**
     * Overspeed FAILURE: the trip always latches; {@code shed_blades} also destroys every vane set;
     * {@code blast} also bursts the shroud. With overload disabled only the trip happens.
     *
     * @return true when this block entity is gone (blast)
     */
    private boolean onOverspeed(ServerLevel level, BlockPos pos) {
        trip();
        TurbineConfig.FailureMode mode = NeroPowerConfig.overloadEnabled() && !isGalleryExhibit()
                ? TurbineConfig.failureAction() : TurbineConfig.FailureMode.TRIP; // exhibits only ever trip
        switch (mode) {
            case SHED_VANES -> {
                for (BlockPos spindle : this.spindles) {
                    TurbineSpindleBlock.shed(level, spindle, level.getBlockState(spindle));
                }
                this.recheckDue = true;
                return false;
            }
            case BLAST -> {
                new Explode(3).apply(level, pos, failureContext());
                return level.getBlockEntity(pos) != this;
            }
            default -> {
                return false;
            }
        }
    }

    private void trip() {
        if (!this.tripped) {
            this.tripped = true;
            setChanged();
        }
    }

    /** Clear a latched trip once the rotor is below {@value TurbineMath#RESET_BELOW_RPM} rpm. */
    public boolean requestReset() {
        if (!this.tripped || !TurbineMath.canReset(this.rpm)) {
            return false;
        }
        this.tripped = false;
        setChanged();
        return true;
    }

    /** Redstone input from the block: a rising edge requests a reset. */
    public void onRedstone(boolean powered) {
        if (powered && !this.lastRedstone) {
            requestReset();
        }
        this.lastRedstone = powered;
    }

    private void updateReadout(long steam, TurbineMath.Rotor rotor, TurbineMath.Plan plan, long output,
            FailureStage stage) {
        int cause;
        MachineStatus status = null;
        if (!formed()) {
            cause = STALL_NOT_FORMED;
            status = MachineStatus.UNFORMED;
        } else if (!rotor.runnable()) {
            // The shell is right, but no vane slice or no wound stator slice: the screen names which.
            cause = STALL_NO_WORKING_SLICE;
            status = MachineStatus.UNFORMED;
        } else if (this.tripped) {
            cause = STALL_NONE; // the readout says "tripped"
            status = MachineStatus.THROTTLED;
        } else if (steam <= 0L) {
            cause = STALL_NO_STEAM;
            status = MachineStatus.STARVED;
        } else if (plan.steamPassed() <= 0L) {
            cause = STALL_EXHAUST_BLOCKED;
            status = MachineStatus.BLOCKED;
        } else if (plan.potential() > 0L && plan.extracted() <= 0L) {
            cause = STALL_BUFFER_FULL;
            status = MachineStatus.BLOCKED;
        } else {
            cause = STALL_NONE;
        }
        if (status != null) {
            reportStatus(status);
        }
        this.stallCause = cause;
        if (this.tripped) {
            this.readout = TurbineReadout.TRIP;
        } else if (stage == FailureStage.FAILURE) {
            this.readout = TurbineReadout.FAIL;
        } else if (stage != FailureStage.STABLE) {
            this.readout = TurbineReadout.HOT;
        } else if (output > 0L) {
            this.readout = TurbineReadout.WORK;
        } else if (cause != STALL_NONE) {
            this.readout = TurbineReadout.STALL;
        } else {
            this.readout = TurbineReadout.REST;
        }
    }

    /** The code of interior slice {@code i} (0-based from the helm), {@link #SLICE_INVALID} past the end. */
    public int sliceCode(int i) {
        return i >= 0 && i < MAX_SLICES && i < Math.max(0, this.length - 2) ? this.sliceCodes[i] : SLICE_INVALID;
    }

    // --- failure context --------------------------------------------------------------------------------

    @Override
    protected FailureContext failureContext() {
        Bounds b = this.bounds;
        if (b == null) {
            return super.failureContext();
        }
        return new FailureContext(new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()),
                owner(), machineId());
    }

    // --- read surface (server: live; client: last synced) -----------------------------------------------

    /** Rotor speed (rpm). Server only; clients read {@link #rpmBucket()}. */
    public int rpm() {
        return this.rpm;
    }

    /** Synced render input: rpm / 100, 0..24. */
    public int rpmBucket() {
        return TurbineMath.rpmBucket(this.rpm);
    }

    /** Synced render input: the trip is latched. */
    public boolean tripped() {
        return this.tripped;
    }

    /** Synced render input: the formed structure's bounds, or null when unformed. */
    @Nullable
    public Bounds formedBounds() {
        return this.bounds;
    }

    /** Synced readout (rest / work / stall / hot / trip / fail). */
    public int renderReadout() {
        return this.readout;
    }

    /** Synced stall cause. */
    public int renderStallCause() {
        return this.stallCause;
    }

    /** NE delivered last tick. */
    public long lastOutput() {
        return this.lastOutput;
    }

    /** Formed structure length (blocks, helm to tail), or 0 when unformed. Read by the link module. */
    public int length() {
        return formed() ? this.length : 0;
    }

    /** Inlet steam fill as permille (0..1000) at the last tick; 0 with no inlet. Read by the link module. */
    public int steamPermille() {
        if (this.steamCapacity <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(1000L, this.steamStored * 1000L / this.steamCapacity));
    }

    /**
     * Latch the trip on request from a remote operator (link action {@code turbine_trip}): the same latch
     * an overspeed sets, with no failure action. Clearing it is the normal reset path.
     *
     * @return whether the turbine was running untripped before
     */
    public boolean requestTrip() {
        boolean was = this.tripped;
        trip();
        return !was;
    }

    @Override
    protected boolean renderSyncDirty() {
        int bucket = rpmBucket();
        boolean dirty = bucket != this.syncedBucket || this.tripped != this.syncedTripped
                || this.readout != this.syncedReadout || this.stallCause != this.syncedStall
                || !java.util.Objects.equals(this.bounds, this.syncedBounds);
        if (dirty) {
            this.syncedBucket = bucket;
            this.syncedTripped = this.tripped;
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
            this.syncedBounds = this.bounds;
        }
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(TurbineReadout.KEY_READOUT, this.readout);
        tag.putInt(TurbineReadout.KEY_STALL, this.stallCause);
        tag.putInt("NpRpmBucket", rpmBucket());
        tag.putBoolean("NpTripped", this.tripped);
        Bounds b = this.bounds;
        tag.putBoolean("NpFormed", b != null);
        if (b != null) {
            int[] corners = {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()};
            for (int i = 0; i < corners.length; i++) {
                tag.putInt("NpB" + i, corners[i]);
            }
        }
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(TurbineReadout.KEY_READOUT, 0);
        this.stallCause = input.getIntOr(TurbineReadout.KEY_STALL, 0);
        this.rpm = input.getIntOr("NpRpmBucket", 0) * TurbineMath.RPM_PER_BUCKET;
        this.tripped = input.getBooleanOr("NpTripped", false);
        if (input.getBooleanOr("NpFormed", false)) {
            int[] c = new int[6];
            for (int i = 0; i < c.length; i++) {
                c[i] = input.getIntOr("NpB" + i, 0);
            }
            this.bounds = new Bounds(c[0], c[1], c[2], c[3], c[4], c[5]);
        } else {
            this.bounds = null;
        }
    }

    // --- menu sync ----------------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        TurbineMath.Plan plan = this.lastPlan;
        TurbineMath.Rotor rotor = rotor();
        FailureThresholds ladder = failure().thresholds();
        int trip = TurbineConfig.tripRpm();
        if (index >= EXTRA_SLICE_CODES && index < EXTRA_SLICE_CODES + 3) {
            int packed = 0;
            int first = (index - EXTRA_SLICE_CODES) * 5;
            for (int i = 0; i < 5; i++) {
                packed |= (sliceCode(first + i) & 7) << (3 * i);
            }
            return packed;
        }
        if (index >= EXTRA_TIER_FLOW && index < EXTRA_TIER_FLOW + 3) {
            return clampShort(TurbineConfig.vaneFlow(VaneTier.values()[index - EXTRA_TIER_FLOW + 1]));
        }
        if (index >= EXTRA_TIER_EFF && index < EXTRA_TIER_EFF + 3) {
            return clampShort(TurbineConfig.vaneEfficiency(VaneTier.values()[index - EXTRA_TIER_EFF + 1]));
        }
        if (index >= EXTRA_TIER_CEILING && index < EXTRA_TIER_CEILING + 3) {
            return clampShort(TurbineConfig.statorCeiling(WindingTier.values()[index - EXTRA_TIER_CEILING + 1]));
        }
        return switch (index) {
            case EXTRA_RPM -> clampShort(this.rpm);
            case EXTRA_TRIP_RPM -> clampShort(trip);
            case EXTRA_WARN_RPM -> clampShort(stressToRpm(ladder.warningPermille(), trip));
            case EXTRA_UNSTABLE_RPM -> clampShort(stressToRpm(ladder.unstablePermille(), trip));
            case EXTRA_TRIPPED -> this.tripped ? 1 : 0;
            case EXTRA_FAILURE_STAGE -> failureStage().code();
            case EXTRA_FAILURE_ACTION -> (NeroPowerConfig.overloadEnabled()
                    ? TurbineConfig.failureAction() : TurbineConfig.FailureMode.TRIP).ordinal();
            case EXTRA_STEAM_P -> hi(this.steamStored);
            case EXTRA_STEAM_P + 1 -> lo(this.steamStored);
            case EXTRA_STEAM_CAPACITY_P -> hi(this.steamCapacity);
            case EXTRA_STEAM_CAPACITY_P + 1 -> lo(this.steamCapacity);
            case EXTRA_EXHAUST_P -> hi(this.exhaustStored);
            case EXTRA_EXHAUST_P + 1 -> lo(this.exhaustStored);
            case EXTRA_EXHAUST_CAPACITY_P -> hi(this.exhaustCapacity);
            case EXTRA_EXHAUST_CAPACITY_P + 1 -> lo(this.exhaustCapacity);
            case EXTRA_STEAM_USED_P -> hi(plan.steamUsed());
            case EXTRA_STEAM_USED_P + 1 -> lo(plan.steamUsed());
            case EXTRA_FLOW_CAP_P -> hi(rotor.flowCap());
            case EXTRA_FLOW_CAP_P + 1 -> lo(rotor.flowCap());
            case EXTRA_EFFICIENCY -> rotor.effPermille();
            case EXTRA_EXHAUST_FACTOR -> plan.exhaustPermille();
            case EXTRA_VENT_EFFICIENCY -> TurbineConfig.ventEfficiencyPermille();
            case EXTRA_OUTPUT_P -> hi(this.lastOutput);
            case EXTRA_OUTPUT_P + 1 -> lo(this.lastOutput);
            case EXTRA_CEILING_P -> hi(rotor.ceiling());
            case EXTRA_CEILING_P + 1 -> lo(rotor.ceiling());
            case EXTRA_WOUND_STATORS -> clampShort(woundStators());
            case EXTRA_GOVERNOR -> governorFitted() ? 1 : 0;
            case EXTRA_PORTS_INLET -> this.portsInlet;
            case EXTRA_PORTS_EXHAUST -> this.portsExhaust;
            case EXTRA_PORTS_VENT -> this.portsVent;
            case EXTRA_SLICE_COUNT -> formed() ? Math.max(0, this.length - 2) : 0;
            case EXTRA_FORMED -> formed() ? 1 : 0;
            case EXTRA_READOUT -> this.readout;
            case EXTRA_STALL -> this.stallCause;
            default -> 0;
        };
    }

    /** Wound stator blocks in the structure. */
    public int woundStators() {
        int n = 0;
        for (Map.Entry<WindingTier, Integer> entry : this.windings.entrySet()) {
            if (entry.getKey() != WindingTier.NONE) {
                n += entry.getValue();
            }
        }
        return n;
    }

    /** The rpm at which overspeed stress reaches {@code permille}. */
    static int stressToRpm(int permille, int tripRpm) {
        return TurbineMath.RATED_RPM + (int) ((long) permille * (tripRpm - TurbineMath.RATED_RPM) / 1000L);
    }

    private static int clampShort(long value) {
        return (int) Math.max(0L, Math.min(SHORT_MAX, value));
    }

    private static int hi(long value) {
        return za.co.neroland.neropower.menu.GaugeTail.hi2(value);
    }

    private static int lo(long value) {
        return za.co.neroland.neropower.menu.GaugeTail.lo2(value);
    }

    // --- persistence -----------------------------------------------------------------------------------------

    //? if >=1.21.6 {
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
    //?} else {
    /*@Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ValueOutput output = ValueOutput.of(tag, registries);
    *///?}
        output.putInt("Rpm", this.rpm);
        output.putBoolean("Tripped", this.tripped);
        output.putInt("Length", this.length);
    }

    //? if >=1.21.6 {
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
    //?} else {
    /*@Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ValueInput input = ValueInput.of(tag, registries);
    *///?}
        this.rpm = Math.max(0, Math.min(TurbineMath.MAX_TARGET_RPM, input.getIntOr("Rpm", 0)));
        this.tripped = input.getBooleanOr("Tripped", false);
        this.length = Math.max(0, input.getIntOr("Length", 0));
        this.recheckDue = true;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            MultiblockIndex.unregister(level.dimension(), this.worldPosition.asLong());
        }
    }

    /** A generator is never load-shed by a Grid Controller. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.turbine_helm");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        this.recheckDue = true; // opening the screen re-checks the structure
        return new TurbineHelmMenu(containerId, playerInventory, this, this.data);
    }
}

package za.co.neroland.neropower.storage;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.fluid.GatedFluidView;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.Explode;
import za.co.neroland.neropower.failure.FailureContext;
import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.LevelProbe;
import za.co.neroland.neropower.multiblock.MultiblockHost;
import za.co.neroland.neropower.multiblock.MultiblockIndex;
import za.co.neroland.neropower.multiblock.ShapeResult;
import za.co.neroland.neropower.multiblock.SmesRingShape;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Storage Ring Controller (0.4.0, decision 008; shape in decision 004): the largest store in the
 * ecosystem. The ring of {@code smes_coil} it sits in sets the capacity (coils x
 * {@code smesCapacityPerCoil}) and I/O; the energy itself is one {@code long} here ({@link SmesCore}), so
 * breaking a coil unforms the ring but loses nothing, and a smaller re-formed ring caps the charge (the
 * excess is lost, never duplicated).
 *
 * <p><b>Cryo coolant.</b> Every 100 ticks a formed ring draws {@code smesCryoPerCoilPer100Ticks} mB per
 * coil of {@code #nerotech:cryo_coolant} from its tank. Starved, its warmth rises; the warmth is the heat
 * fed to the shared failure ladder (WARNING, UNSTABLE halves the I/O, FAILURE quenches). Every transition
 * telegraphs through {@code FailureTelegraph} (the bus, NeroLink, the alarm state, the owner's alert).
 * A drain-mode quench (default) loses {@code smesQuenchLossPermille} of the charge and locks the ring out
 * for {@code smesQuenchLockoutTicks}; it re-arms once its warmth is back below the warning threshold
 * minus the hysteresis. No blast, no terrain change. Because a drain quench destroys nothing, it walks the
 * full ladder even with {@code overloadEnabled=false}; the blast variant honours that switch.
 *
 * <p>Validation runs on change only (a ring member placed or removed, through {@link MultiblockIndex};
 * load; menu open). Saves {@code SmesEnergy}, {@code Warmth}, {@code Lockout}, {@code Quenched}, the
 * coolant tank and the failure stage. No player data; the synced ring bounds are offsets from the
 * controller, not world coordinates.
 */
public class SmesControllerBlockEntity extends StorageBlockEntity implements MultiblockHost {

    /** Coolant draw cadence (ticks). */
    public static final int COOLANT_INTERVAL = 100;
    /** Synced charge buckets: 0..{@value}. */
    public static final int CHARGE_BUCKETS = 16;

    /** Menu-synced ints after the seven shared ones (see {@link #extraData}). */
    public static final int EXTRA_DATA = 29;

    private final SmesCore core = new SmesCore();
    private final CoolantTank coolant = new CoolantTank(StorageConfig.smesCryoTankMb(), this::setChanged);
    private final NeroEnergyStorage view = new NeroEnergyStorage() {
        @Override
        public long getAmount() {
            return SmesControllerBlockEntity.this.core.energy();
        }

        @Override
        public long getCapacity() {
            return SmesControllerBlockEntity.this.core.capacity();
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            long accepted = SmesControllerBlockEntity.this.core.insert(maxAmount, simulate);
            if (accepted > 0 && !simulate) {
                setChanged();
            }
            return accepted;
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            long given = SmesControllerBlockEntity.this.core.extract(maxAmount, simulate);
            if (given > 0 && !simulate) {
                setChanged();
            }
            return given;
        }
    };
    private final int coolantPhase = Math.floorMod(System.identityHashCode(this), COOLANT_INTERVAL);

    private boolean recheckDue = true;
    private boolean starved;
    private long ioLimit;
    private long lastIn;
    private long lastOut;
    private Bounds ring;

    // Synced render buckets (contract section 2) and their last-sent values.
    private int chargeBucket;
    private int ioBucket;
    private int warmthBucket;
    private boolean lockoutFlag;
    private int ringMinDx;
    private int ringMinDz;
    private int ringMaxDx;
    private int ringMaxDz;
    private long syncedBuckets = Long.MIN_VALUE;

    public SmesControllerBlockEntity(BlockPos pos, BlockState state) {
        // The base buffer is a placeholder (capacity 0): the ring's energy lives in the SmesCore.
        super(StorageContent.SMES_CONTROLLER.get(), pos, state, 0, 0, 0);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, false)
                .build());
    }

    /** The ring's energy surface (Core's capability and the side-config gate resolve to this). */
    @Override
    public NeroEnergyStorage getEnergy() {
        return this.view;
    }

    public SmesCore core() {
        return this.core;
    }

    public CoolantTank coolant() {
        return this.coolant;
    }

    // --- fluid surface (the coolant tank, every face) --------------------------------------------------------

    @Nullable
    @Override
    public NeroFluidStorage fluidStorage(@Nullable Direction side) {
        return this.coolant;
    }

    @Override
    public List<GatedFluidView> standardFluidViews(@Nullable Direction side) {
        return List.of(gatedView(this.coolant, Channel.FLUID, side));
    }

    // --- structure ----------------------------------------------------------------------------------------------

    @Override
    public void markStructureDirty() {
        this.recheckDue = true;
    }

    private static Cell cellOf(BlockState state) {
        if (state.getBlock() instanceof SmesCoilBlock) {
            return Cell.SMES_COIL;
        }
        if (state.getBlock() instanceof SmesControllerBlock) {
            return Cell.SMES_CONTROLLER;
        }
        return state.isAir() ? Cell.AIR : Cell.OTHER;
    }

    private void revalidate(Level level, BlockPos pos) {
        if (!this.recheckDue || !MultiblockIndex.tryRevalidate(level.dimension(), level.getGameTime())) {
            return;
        }
        this.recheckDue = false;
        ShapeResult result = SmesRingShape.evaluate(new LevelProbe(level, SmesControllerBlockEntity::cellOf),
                pos.getX(), pos.getY(), pos.getZ());
        int coils = result.valid() ? result.count(Cell.SMES_COIL) : 0;
        if (result.valid()) {
            MultiblockIndex.register(level.dimension(), pos.asLong(), result.bounds(), this::markStructureDirty);
            this.ring = result.bounds();
        } else {
            MultiblockIndex.unregister(level.dimension(), pos.asLong());
            this.ring = null;
        }
        boolean wasFormed = this.core.formed();
        int wasCoils = this.core.coils();
        this.core.applyStructure(result.valid(), coils, StorageConfig.smesCapacityPerCoil());
        if (wasFormed != this.core.formed() || wasCoils != this.core.coils()) {
            setChanged();
        }
        Bounds b = this.ring;
        this.ringMinDx = b == null ? 0 : b.minX() - pos.getX();
        this.ringMinDz = b == null ? 0 : b.minZ() - pos.getZ();
        this.ringMaxDx = b == null ? 0 : b.maxX() - pos.getX();
        this.ringMaxDz = b == null ? 0 : b.maxZ() - pos.getZ();
    }

    // --- tick ----------------------------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        revalidate(level, pos);
        this.coolant.resize(StorageConfig.smesCryoTankMb());

        // Coolant: one draw every COOLANT_INTERVAL ticks; a short draw means starved until the next one.
        if ((level.getGameTime() + this.coolantPhase) % COOLANT_INTERVAL == 0) {
            long need = this.core.coolantNeed(StorageConfig.smesCryoPerCoilPer100Ticks());
            if (need > 0) {
                long drawn = this.coolant.drain(need, false);
                this.starved = drawn < need;
            } else {
                this.starved = false;
            }
        }
        if (!this.core.formed()) {
            this.starved = false; // no ring, no superconducting load to keep cold
        }
        this.core.tickWarmth(this.starved, StorageConfig.smesWarmingPerTick(), StorageConfig.smesCoolingPerTick());

        // The shared failure ladder, fed warmth as heat permille.
        boolean blast = StorageConfig.smesQuenchBlast();
        FailureController ladder = failure();
        FailureStage stage = ladder.tick(this.core.warmth(), SmesCore.MAX_WARMTH,
                !blast || NeroPowerConfig.overloadEnabled());
        if (stage == FailureStage.FAILURE && ladder.justEntered()) {
            if (blast && level instanceof ServerLevel serverLevel) {
                FailureContext ctx = failureContext();
                new Explode(ctx.boundsRadius()).apply(serverLevel, pos, ctx);
                return; // the controller is gone, and its charge with it
            }
            this.core.quench(StorageConfig.smesQuenchLossPermille(), StorageConfig.smesQuenchLockoutTicks());
            setChanged();
        }
        FailureThresholds thresholds = ladder.thresholds();
        this.core.tickLockout(thresholds.warningPermille() - thresholds.hysteresisPermille());

        // I/O for this tick (an energy budget each way), then push like any store (never into storage).
        long inBefore = this.core.inThisTick();
        long outBefore = this.core.outThisTick();
        this.ioLimit = this.core.ioLimit(StorageConfig.smesIoPerCoil(), StorageConfig.smesMaxIo(),
                stage == FailureStage.UNSTABLE);
        this.core.beginTick(this.ioLimit);
        StorageEnergy.pushToNeighbours(level, pos, this.view, this.ioLimit, sideConfig(), null, -1);
        this.lastIn = inBefore + this.core.inThisTick();
        this.lastOut = outBefore + this.core.outThisTick();

        updateReadout(stage);
        updateBuckets(this.lastIn + this.lastOut);
        setActive(this.core.online() && this.core.energy() > 0);
    }

    private void updateReadout(FailureStage stage) {
        // Screen spec: fail = quenching; stall 15 = locked out; stall 1 = not formed; stall 13 = no coolant
        // (warming); hot = warning / unstable; work = moving energy; rest = holding.
        if (stage == FailureStage.FAILURE) {
            setReadout(READOUT_FAIL, STALL_NONE, MachineStatus.BLOCKED);
        } else if (this.core.quenched()) {
            setReadout(READOUT_STALL, STALL_LOCKED_OUT, MachineStatus.BLOCKED);
        } else if (!this.core.formed()) {
            setReadout(READOUT_STALL, STALL_NOT_FORMED, MachineStatus.UNFORMED);
        } else if (stage != FailureStage.STABLE) {
            setReadout(READOUT_HOT, this.starved ? STALL_NO_COOLANT : STALL_NONE, MachineStatus.THROTTLED);
        } else if (this.starved) {
            setReadout(READOUT_STALL, STALL_NO_COOLANT, MachineStatus.STARVED);
        } else if (this.lastIn > 0 || this.lastOut > 0) {
            setReadout(READOUT_WORK, STALL_NONE, MachineStatus.RUNNING);
        } else {
            setReadout(READOUT_REST, STALL_NONE, MachineStatus.IDLE);
        }
    }

    private void updateBuckets(long moved) {
        long capacity = this.core.capacity();
        this.chargeBucket = capacity <= 0 ? 0
                : (int) Math.min(CHARGE_BUCKETS, this.core.energy() * CHARGE_BUCKETS / capacity);
        this.ioBucket = this.ioLimit <= 0 || moved <= 0 ? 0
                : (int) Math.max(1L, Math.min(4L, moved * 4L / (this.ioLimit * 2L)));
        this.warmthBucket = Math.min(10, this.core.warmth() / 100);
        this.lockoutFlag = this.core.quenched();
    }

    @Override
    protected boolean bucketsDirty() {
        long packed = this.chargeBucket
                | ((long) this.ioBucket << 5)
                | ((long) this.warmthBucket << 8)
                | ((this.lockoutFlag ? 1L : 0L) << 12)
                | ((long) (this.ringMinDx & 0xFF) << 13)
                | ((long) (this.ringMinDz & 0xFF) << 21)
                | ((long) (this.ringMaxDx & 0xFF) << 29)
                | ((long) (this.ringMaxDz & 0xFF) << 37);
        boolean dirty = packed != this.syncedBuckets;
        this.syncedBuckets = packed;
        return dirty;
    }

    @Override
    protected void writeBuckets(CompoundTag tag) {
        tag.putInt("ChargeBucket", this.chargeBucket);
        tag.putInt("IoBucket", this.ioBucket);
        tag.putInt("WarmthBucket", this.warmthBucket);
        tag.putBoolean("Lockout", this.lockoutFlag);
        tag.putInt("RingMinDx", this.ringMinDx);
        tag.putInt("RingMinDz", this.ringMinDz);
        tag.putInt("RingMaxDx", this.ringMaxDx);
        tag.putInt("RingMaxDz", this.ringMaxDz);
    }

    @Override
    protected void readBuckets(ValueInput input) {
        this.chargeBucket = input.getIntOr("ChargeBucket", 0);
        this.ioBucket = input.getIntOr("IoBucket", 0);
        this.warmthBucket = input.getIntOr("WarmthBucket", 0);
        this.lockoutFlag = input.getBooleanOr("Lockout", false);
        this.ringMinDx = input.getIntOr("RingMinDx", 0);
        this.ringMinDz = input.getIntOr("RingMinDz", 0);
        this.ringMaxDx = input.getIntOr("RingMaxDx", 0);
        this.ringMaxDz = input.getIntOr("RingMaxDz", 0);
    }

    // --- render read surface (bucketed; contract section 2) ---------------------------------------------------

    /** Charge bucket 0..16. */
    public int chargeBucket() {
        return this.chargeBucket;
    }

    /** Throughput bucket 0..4 (share of the I/O limit moved last tick). */
    public int ioBucket() {
        return this.ioBucket;
    }

    /** Warmth bucket 0..10. */
    public int warmthBucket() {
        return this.warmthBucket;
    }

    /** Whether the ring is locked out after a quench. */
    public boolean lockout() {
        return this.lockoutFlag;
    }

    /** The formed ring's bounds as offsets from the controller {@code {minDx, minDz, maxDx, maxDz}}; zeros when unformed. */
    public int[] ringOffsets() {
        return new int[] {this.ringMinDx, this.ringMinDz, this.ringMaxDx, this.ringMaxDz};
    }

    // --- failure ----------------------------------------------------------------------------------------------

    @Override
    protected FailureContext failureContext() {
        Bounds b = this.ring;
        if (b == null) {
            return super.failureContext();
        }
        return new FailureContext(new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()),
                owner(), machineId());
    }

    // --- menu sync (screen spec smes_controller; every int < 2^15, larger values as 15-bit pairs) ------------
    // [0] coils, [1|2] capacity per coil, [3|4] I/O per coil, [5|6] max I/O, [7|8] coolant mB,
    // [9|10] tank mB, [11] coolant per coil per 100 ticks, [12] warmth permille, [13] warning permille,
    // [14] unstable permille, [15] failure stage code, [16|17] lockout ticks, [18] quench mode (0 drain,
    // 1 blast), [19] quench loss permille, [20|21] quench lockout ticks, [22|23] in last tick,
    // [24|25] out last tick, [26] formed, [27] readout, [28] stallCause.

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        FailureThresholds t = failure().thresholds();
        return switch (index) {
            case 0 -> this.core.coils();
            case 1 -> GaugeTail.hi2(StorageConfig.smesCapacityPerCoil());
            case 2 -> GaugeTail.lo2(StorageConfig.smesCapacityPerCoil());
            case 3 -> GaugeTail.hi2(StorageConfig.smesIoPerCoil());
            case 4 -> GaugeTail.lo2(StorageConfig.smesIoPerCoil());
            case 5 -> GaugeTail.hi2(StorageConfig.smesMaxIo());
            case 6 -> GaugeTail.lo2(StorageConfig.smesMaxIo());
            case 7 -> GaugeTail.hi2(this.coolant.getAmount());
            case 8 -> GaugeTail.lo2(this.coolant.getAmount());
            case 9 -> GaugeTail.hi2(this.coolant.getCapacity());
            case 10 -> GaugeTail.lo2(this.coolant.getCapacity());
            case 11 -> StorageConfig.smesCryoPerCoilPer100Ticks();
            case 12 -> this.core.warmth();
            case 13 -> t.warningPermille();
            case 14 -> t.unstablePermille();
            case 15 -> failureStage().code();
            case 16 -> GaugeTail.hi2(this.core.lockout());
            case 17 -> GaugeTail.lo2(this.core.lockout());
            case 18 -> StorageConfig.smesQuenchBlast() ? 1 : 0;
            case 19 -> StorageConfig.smesQuenchLossPermille();
            case 20 -> GaugeTail.hi2(StorageConfig.smesQuenchLockoutTicks());
            case 21 -> GaugeTail.lo2(StorageConfig.smesQuenchLockoutTicks());
            case 22 -> GaugeTail.hi2(this.lastIn);
            case 23 -> GaugeTail.lo2(this.lastIn);
            case 24 -> GaugeTail.hi2(this.lastOut);
            case 25 -> GaugeTail.lo2(this.lastOut);
            case 26 -> this.core.formed() ? 1 : 0;
            case 27 -> readoutCode();
            case 28 -> stallCause();
            default -> 0;
        };
    }

    /** The I/O limit in force this tick (NE/t), for the menu's gauge tail. */
    public long ioLimit() {
        return this.ioLimit;
    }

    // --- persistence -------------------------------------------------------------------------------------------

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
        output.putLong("SmesEnergy", this.core.energy());
        output.putInt("Warmth", this.core.warmth());
        output.putInt("Lockout", this.core.lockout());
        output.putBoolean("Quenched", this.core.quenched());
        this.coolant.save(output);
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
        if (input.getIntOr("RdOut", -1) >= 0) {
            return; // a client update tag: the render surface was read by the base; never the energy
        }
        this.core.load(input.getLongOr("SmesEnergy", 0L), input.getIntOr("Warmth", 0),
                input.getIntOr("Lockout", 0), input.getBooleanOr("Quenched", false));
        this.coolant.load(input);
        this.recheckDue = true;
    }

    // --- misc -----------------------------------------------------------------------------------------------------

    /** Removed or unloaded: drop the ring from the in-memory multiblock index. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            MultiblockIndex.unregister(level.dimension(), this.worldPosition.asLong());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.smes_controller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        this.recheckDue = true; // opening the screen re-checks the ring
        return new SmesMenu(containerId, playerInventory, this, this.data);
    }
}

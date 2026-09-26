package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;
import za.co.neroland.nerolandcore.sideconfig.SlotGroup;

import za.co.neroland.nerotech.config.NeroTechConfig;
import za.co.neroland.nerotech.machine.MachineEnergy;
import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.failure.Explode;
import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.failure.FailureContext;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Fission Reactor controller (Stage 4) — NeroPower's first reactor and the first machine on the
 * failure ladder. A hollow 3³ / 5³ shell of Fission Casing validated by {@link FissionStructure},
 * this core at the centre of one vertical wall facing outward, Control Rod Assemblies inside.
 * <b>Inert until formed.</b>
 *
 * <p><b>Fuel cycle.</b> Four rod slots, of which {@code shellSize - 1} (2 / 4) are usable — the
 * rest are locked. Each loaded Fuel Rod carries its burn-up (permille) in the
 * {@code neropower:burnup} data component; every running tick advances it by
 * {@code fissionBurnupPerTick} (permille x1000, scaled by preset and Speed modules, through a
 * shared long accumulator so tiny rates still tick over) and at 1000‰ the rod becomes a Spent Fuel
 * Rod in place. Output and heat follow the {@link FissionMath} burn-up curve: a fresh rod
 * over-performs and runs hottest, a nearly spent one limps.
 *
 * <p><b>Control rods.</b> Every Control Rod Assembly inside the shell scales output <i>and</i> heat
 * by {@code 1 - n x fissionControlRodPermille / 1000} (floor 15%) — the operator's throttle, at the
 * cost of NE/tick.
 *
 * <p><b>Poison.</b> Running hot — flat out, every usable slot loaded, control-rod factor above
 * {@code fissionPoisonRodThresholdPermille} ({@link FissionMath#poisonAccumulates}) — builds
 * neutron poison; at
 * {@code fissionPoisonStallPermille} the core falls into the xenon pit ({@link PoisonModel}) and
 * reports THROTTLED until the poison has decayed 300‰ below the line.
 *
 * <p><b>SCRAM.</b> {@link #requestScram()} (the NeroLink {@code scram} action, gated by
 * {@code LinkScope.mayAct}) asks the core to drop every control rod: from the next tick the
 * control-rod factor is pinned at
 * {@link FissionMath#CONTROL_ROD_FLOOR} for {@value #SCRAM_TICKS} ticks, whatever the assembly
 * count, so output and heat fall to the floor and the ladder can climb back down.
 *
 * <p><b>Failure.</b> Heat feeds the shared ladder ({@link #tickFailure}); at FAILURE the shell
 * explodes ({@link Explode}, radius shell + 2, capped and terrain-damage-aware) and, when
 * {@code fissionScorchEnabled}, leaves a {@link ScorchZones scorch zone}.
 */
public class FissionCoreBlockEntity extends NeroPowerMachineBlockEntity {

    /** Rod slot indices {@code 0..ROD_SLOTS-1}. */
    public static final int ROD_SLOTS = FissionMath.ROD_SLOTS;

    /** Machine-specific synced ints after NeroTech's seven: 4 burn-ups, poison, control-rod factor, failure stage, shell size. */
    public static final int EXTRA_DATA = ROD_SLOTS + 4;
    public static final int EXTRA_POISON = ROD_SLOTS;
    public static final int EXTRA_CONTROL_ROD = ROD_SLOTS + 1;
    public static final int EXTRA_FAILURE_STAGE = ROD_SLOTS + 2;
    public static final int EXTRA_SHELL_SIZE = ROD_SLOTS + 3;

    /** Formed state (persisted so a reload never flickers through "unformed"; synced to the BER via the update tag). */
    private int shellSize;
    private int rodCount;
    /** A structure recheck is due (neighbour change / first tick); the cadence also forces one. */
    private boolean recheckDue = true;

    /** Burn-up accumulator in permille x1000 (one shared clock — every loaded rod ages at the same rate). */
    private long burnupAccumulator;

    private final PoisonModel poison = new PoisonModel();

    /** How long a SCRAM pins the control-rod factor at the floor (1200 ticks = 60 s). */
    public static final int SCRAM_TICKS = 1200;

    /** Set by {@link #requestScram()}; honoured (turned into {@link #scramTicksLeft}) on the next tick. */
    private boolean scramRequested;

    /** Last failure-stage code pushed to clients (the {@link #renderSyncDirty} compare-and-record state). */
    private int syncedFailureStage;

    /** Ticks the control-rod factor stays pinned at the floor; 0 = no SCRAM in force. */
    private int scramTicksLeft;

    /** The parsed burn-up curve and the config string it came from (re-parsed on hot-reload). */
    @Nullable
    private FissionMath.Curve curve;
    @Nullable
    private String curveSource;

    public FissionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(FissionContent.FISSION_CORE_TYPE.get(), pos, state, ROD_SLOTS);
        // GENERATOR preset: ENERGY OUTPUT on every face; rods accepted IN on every face, and a face
        // set to OUTPUT lets automation pull spent rods (fresh ones are guarded in canTakeItemThroughFace).
        int[] slots = {0, 1, 2, 3};
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.ITEM, SlotGroup.of("rods", slots), SlotGroup.of("spent", slots))
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    // --- structure ----------------------------------------------------------------

    /** The block fires this on every neighbour change: the right moment to schedule a structure recheck. */
    @Override
    public void invalidateThermalLinks() {
        super.invalidateThermalLinks();
        this.recheckDue = true;
    }

    /** Whether the shell is formed. */
    public boolean formed() {
        return this.shellSize >= FissionStructure.MIN_SIZE;
    }

    /** The formed shell's edge (3 / 5), or 0. */
    public int shellSize() {
        return this.shellSize;
    }

    /** Control Rod Assemblies inside the shell at the last check. */
    public int rodCount() {
        return this.rodCount;
    }

    /** Rod slots the current shell can use ({@link FissionMath#usableRodSlots}). */
    public int usableRodSlots() {
        return FissionMath.usableRodSlots(this.shellSize);
    }

    /** The current neutron poison (permille). */
    public int poison() {
        return this.poison.poison();
    }

    /** The current control-rod factor as permille (1000 = no assemblies). */
    public int controlRodFactorPermille() {
        return (int) Math.round(controlRodFactor() * FissionMath.PERMILLE);
    }

    private double controlRodFactor() {
        if (this.scramTicksLeft > 0) {
            return FissionMath.CONTROL_ROD_FLOOR;
        }
        return FissionMath.controlRodFactor(this.rodCount, FissionMath.interiorVolume(this.shellSize),
                FissionConfig.controlRodPermille());
    }

    // --- SCRAM --------------------------------------------------------------------

    /**
     * Ask the core to SCRAM: from its next tick every control rod counts as inserted for
     * {@value #SCRAM_TICKS} ticks. Server side; idempotent while a SCRAM is already in force.
     */
    public void requestScram() {
        this.scramRequested = true;
    }

    /** Whether a SCRAM is currently pinning the control-rod factor at the floor. */
    public boolean scrammed() {
        return this.scramTicksLeft > 0;
    }

    /** Ticks left on the current SCRAM (0 when none). */
    public int scramTicksLeft() {
        return this.scramTicksLeft;
    }

    /** Honour a pending SCRAM request and count a running one down (once per tick). */
    private void tickScram() {
        if (this.scramRequested) {
            this.scramRequested = false;
            this.scramTicksLeft = SCRAM_TICKS;
            setChanged();
        } else if (this.scramTicksLeft > 0) {
            this.scramTicksLeft--;
            if (this.scramTicksLeft == 0) {
                setChanged();
            }
        }
    }

    /** Bounded structure re-validation: on a neighbour change, and on the 40-tick phase-spread cadence. */
    private void revalidate(Level level, BlockPos pos, BlockState state) {
        int cadence = FissionStructure.RECHECK_TICKS;
        boolean onCadence = (level.getGameTime() + Math.floorMod(pos.hashCode(), cadence)) % cadence == 0;
        if (!this.recheckDue && !onCadence) {
            return;
        }
        this.recheckDue = false;
        Direction facing = state.getValue(NeroTechMachineBlock.FACING);
        FissionStructure.Result result = FissionStructure.check(level, pos, facing);
        int nowSize = result.valid() ? result.shellSize() : 0;
        int nowRods = result.valid() ? result.rodCount() : 0;
        if (nowSize == this.shellSize && nowRods == this.rodCount) {
            return;
        }
        this.shellSize = nowSize;
        this.rodCount = nowRods;
        setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    // --- tick -----------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        revalidate(level, pos, state);
        tickScram();

        // The failure ladder first: at FAILURE the action has already removed this block.
        if (tickFailure(level, pos) == FailureStage.FAILURE) {
            return;
        }

        if (!formed()) {
            reportStatus(MachineStatus.UNFORMED);
            idle();
            this.poison.tick(false, 0, FissionConfig.poisonDecayPerTick(), FissionConfig.poisonStallPermille());
            MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(),
                    sideConfig());
            return;
        }

        FissionMath.Curve curve = curve();
        int usable = usableRodSlots();
        double rodSum = 0.0D;
        double heatSum = 0.0D;
        long burnupTotal = 0L;
        int loaded = 0;
        for (int slot = 0; slot < usable; slot++) {
            ItemStack stack = this.items.get(slot);
            if (isFuelRod(stack)) {
                int burnup = burnupOf(stack);
                rodSum += FissionMath.outputFactor(curve, burnup) / (double) FissionMath.PERMILLE;
                heatSum += FissionMath.heatFactor(curve, burnup) / (double) FissionMath.PERMILLE;
                burnupTotal += burnup;
                loaded++;
            }
        }

        boolean roomToStore = getEnergy().getAmount() < getEnergy().getCapacity();
        boolean stalled = this.poison.stalled();
        boolean running = loaded > 0 && roomToStore && !stalled;

        if (running) {
            double crf = controlRodFactor();
            double speed = presetSpeedFactor() * modifiers().speedMultiplier();
            long output = FissionMath.outputPerTick(FissionConfig.nePerRod(), rodSum, crf, speed,
                    failureOutputPermille());
            if (output > 0) {
                energyBuffer().generate((int) Math.min(Integer.MAX_VALUE, output));
            }
            addHeat(FissionMath.heatPerTick(FissionConfig.heatPerRodPerTick(), heatSum, crf));
            advanceBurnup(usable, speed);
            // Work bar: the average burn-up of the loaded rods.
            this.maxProgress = FissionMath.PERMILLE;
            this.progress = (int) (burnupTotal / loaded);
            setActive(true);
        } else {
            idle();
            if (loaded == 0) {
                reportStatus(MachineStatus.STARVED);
            } else if (stalled) {
                reportStatus(MachineStatus.THROTTLED);
            } else {
                reportStatus(MachineStatus.BLOCKED);
            }
        }

        // Poison builds only while the core runs hot (flat out, every usable slot loaded, control-rod
        // factor above fissionPoisonRodThresholdPermille); decays otherwise.
        boolean accumulating = FissionMath.poisonAccumulates(running, failureOutputPermille(), loaded, usable,
                controlRodFactorPermille(), FissionConfig.poisonRodThresholdPermille());
        boolean nowStalled = this.poison.tick(accumulating, FissionConfig.poisonPerTick(),
                FissionConfig.poisonDecayPerTick(), FissionConfig.poisonStallPermille());
        if (nowStalled != stalled) {
            setChanged();
        }

        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(),
                sideConfig());
    }

    private void idle() {
        this.progress = 0;
        this.maxProgress = 0;
        setActive(false);
    }

    /**
     * Age every loaded rod: the shared accumulator gains {@code fissionBurnupPerTick x speed}
     * (permille x1000) and every whole permille it holds is applied to each rod; a rod reaching
     * 1000‰ becomes a Spent Fuel Rod in its slot.
     */
    private void advanceBurnup(int usable, double speed) {
        long step = Math.max(1L, Math.round(FissionConfig.burnupPerTick() * speed));
        this.burnupAccumulator += step;
        if (this.burnupAccumulator < 1000L) {
            return;
        }
        int delta = (int) Math.min(FissionMath.MAX_BURNUP, this.burnupAccumulator / 1000L);
        this.burnupAccumulator %= 1000L;
        for (int slot = 0; slot < usable; slot++) {
            ItemStack stack = this.items.get(slot);
            if (!isFuelRod(stack)) {
                continue;
            }
            int burnup = burnupOf(stack) + delta;
            if (FissionMath.spent(burnup)) {
                this.items.set(slot, new ItemStack(FissionContent.SPENT_FUEL_ROD.get(), stack.getCount()));
            } else {
                stack.set(FissionContent.BURNUP.get(), burnup);
            }
        }
        setChanged();
    }

    /** The burn-up curve, re-parsed only when {@code fissionBurnupCurve} changed (hot-reload safe). */
    private FissionMath.Curve curve() {
        String raw = FissionConfig.burnupCurve();
        FissionMath.Curve current = this.curve;
        if (current == null || !raw.equals(this.curveSource)) {
            current = FissionMath.parseCurve(raw);
            this.curve = current;
            this.curveSource = raw;
        }
        return current;
    }

    // --- rods -----------------------------------------------------------------------

    /** Whether {@code stack} is a Fuel Rod (spent rods are a different item and never count). */
    public static boolean isFuelRod(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FissionContent.FUEL_ROD.get());
    }

    /** A rod's burn-up (permille); a rod without the component is fresh. */
    public static int burnupOf(ItemStack stack) {
        Integer burnup = stack.get(FissionContent.BURNUP.get());
        return burnup == null ? 0 : FissionMath.clampBurnup(burnup);
    }

    /** The burn-up in rod slot {@code slot} (0 for an empty or non-rod slot) — the menu's read surface. */
    public int slotBurnup(int slot) {
        if (slot < 0 || slot >= ROD_SLOTS) {
            return 0;
        }
        ItemStack stack = this.items.get(slot);
        if (isFuelRod(stack)) {
            return burnupOf(stack);
        }
        return stack.is(FissionContent.SPENT_FUEL_ROD.get()) ? FissionMath.MAX_BURNUP : 0;
    }

    /** Only usable slots take rods, and only Fuel Rods; locked slots refuse everything. */
    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot < usableRodSlots() && isFuelRod(stack);
    }

    /** Automation may pull a slot only once its rod is spent. */
    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot < ROD_SLOTS && this.items.get(slot).is(FissionContent.SPENT_FUEL_ROD.get());
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canTakeMachineItem(slot) && super.canTakeItemThroughFace(slot, stack, side);
    }

    // --- failure ladder -------------------------------------------------------------

    /** The blast grows with the shell (5 / 7 before the cap); the scorch zone follows when enabled. */
    @Override
    protected FailureAction failureAction() {
        return new ScorchZoneAction(new Explode(Math.max(FissionStructure.MIN_SIZE, this.shellSize) + 2));
    }

    /** The shell's bounds when formed (epicentre = interior centre), else this block alone. */
    @Override
    protected FailureContext failureContext() {
        if (!formed()) {
            return super.failureContext();
        }
        int half = (this.shellSize - 1) / 2;
        Direction facing = getBlockState().getValue(NeroTechMachineBlock.FACING);
        BlockPos center = this.worldPosition.relative(facing.getOpposite(), half);
        return new FailureContext(center.offset(-half, -half, -half), center.offset(half, half, half),
                owner(), machineId());
    }

    // --- BER read surface (synced via the update tag) -------------------------------

    /**
     * The failure-ladder rung as the BER sees it — {@code FailureStage}/{@code FailureTicks} ride
     * {@code saveAdditional} and therefore the update tag; {@link #renderSyncDirty} pushes a packet
     * whenever the rung moves. The alarm strobe itself keys off the {@code alarm} block state.
     */
    public FailureStage renderFailureStage() {
        return failureStage();
    }

    /**
     * NeroTech's render-sync hook: the formed state and heat bucket already ride the base's sync
     * (shell changes push their own packet in {@link #revalidate}); this adds the failure rung so
     * the BER's core glow can shift as the ladder climbs, without a per-tick packet.
     */
    @Override
    protected boolean renderSyncDirty() {
        int stage = failureStage().code();
        if (stage != this.syncedFailureStage) {
            this.syncedFailureStage = stage;
            return true;
        }
        return false;
    }

    // --- menu sync ------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        if (index < ROD_SLOTS) {
            return slotBurnup(index);
        }
        return switch (index) {
            case EXTRA_POISON -> this.poison.poison();
            case EXTRA_CONTROL_ROD -> controlRodFactorPermille();
            case EXTRA_FAILURE_STAGE -> failureStage().code();
            case EXTRA_SHELL_SIZE -> this.shellSize;
            default -> 0;
        };
    }

    // --- persistence ----------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ShellSize", this.shellSize);
        output.putInt("RodCount", this.rodCount);
        output.putLong("BurnupAcc", this.burnupAccumulator);
        output.putInt("Poison", this.poison.poison());
        output.putBoolean("Stalled", this.poison.stalled());
        output.putInt("ScramTicks", this.scramTicksLeft);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.shellSize = input.getIntOr("ShellSize", 0);
        this.rodCount = input.getIntOr("RodCount", 0);
        this.burnupAccumulator = Math.max(0L, input.getLongOr("BurnupAcc", 0L));
        this.poison.load(input.getIntOr("Poison", 0), input.getBooleanOr("Stalled", false));
        this.scramTicksLeft = Math.max(0, Math.min(SCRAM_TICKS, input.getIntOr("ScramTicks", 0)));
        this.recheckDue = true;
    }

    // --- misc -------------------------------------------------------------------------

    /** A generator is never load-shed by a Grid Controller. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.fission_core");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FissionCoreMenu(containerId, playerInventory, this, this.data);
    }
}

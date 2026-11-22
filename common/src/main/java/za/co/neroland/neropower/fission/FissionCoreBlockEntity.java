package za.co.neroland.neropower.fission;

import java.util.ArrayList;
import java.util.List;
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

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.data.SaveAdapters;
import za.co.neroland.neropower.failure.Explode;
import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.failure.FailureContext;
import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.LevelProbe;
import za.co.neroland.neropower.multiblock.MultiblockHost;
import za.co.neroland.neropower.multiblock.MultiblockIndex;
import za.co.neroland.neropower.progression.FissionWorldLog;
import za.co.neroland.neropower.progression.MilestoneGates;
import za.co.neroland.neropower.progression.OperatorCredit;

/**
 * Fission Reactor controller. A hollow 3³ / 5³ / 7³ shell of Fission Casing validated by
 * {@link FissionStructure} (7³ needs a Reactor Control Computer in the wall), this core at the centre of
 * one vertical wall facing outward. <b>Inert until formed.</b>
 *
 * <p><b>Fuel.</b> Six rod slots, {@code shellSize - 1} (2 / 4 / 6) usable. Fuel Rods and Braided Fuel
 * Rods carry their burn-up in {@code neropower:burnup}; output and heat follow the {@link FissionMath}
 * burn-up curve. A Braided Fuel Rod gives {@code fissionBraidedOutputPermille} of a Fuel Rod, burns at
 * {@code fissionBraidedBurnupPermille} of its rate, and fissions only when the Moderator Tanks' contact
 * score reaches {@code fissionBraidedHeavyWaterContacts}. A burnt-out rod becomes a Hot Spent Fuel Rod,
 * which must cool in a Spent Fuel Rack.
 *
 * <p><b>Reactivity.</b> Control Rod Assemblies, Europium Control Rod Assemblies and Breeder Blankets
 * reduce output and heat ({@link FissionMath#reactivityFactor}, floor 15%); moderators raise both by
 * their fuel-channel score ({@link ReactorLayout}, {@link FissionMath#moderatorBonusPermille}). A Fuel
 * Economizer in an upgrade slot slows burn-up and trims output.
 *
 * <p><b>Modes.</b> {@link ReactorMode#DIRECT}: NE as in 0.3.x. {@link ReactorMode#STEAM} (asked for in
 * the screen, effective while the shell holds a Reactor Steam Port): no NE; each tick the heat becomes
 * steam at the ports ({@code fissionSteamPerHeat} mB per heat, the same mB of water consumed); heat that
 * finds no water or no steam room stays in the core and climbs the failure ladder. A port holding cryo
 * coolant carries the remaining heat off in either mode and, while it holds any, widens the safe band.
 *
 * <p><b>Breeding.</b> Slot 6 takes Pallurite Pellets, slot 7 receives Kindled Pellets ({@link Breeder}).
 *
 * <p><b>Control computer.</b> Fed the core's readings every tick; its auto-SCRAM requests a SCRAM.
 *
 * <p><b>SCRAM / poison / failure</b> are unchanged from 0.3.x.
 */
public class FissionCoreBlockEntity extends NeroPowerMachineBlockEntity implements MultiblockHost {

    /** Rod slot indices {@code 0..ROD_SLOTS-1}. */
    public static final int ROD_SLOTS = FissionMath.ROD_SLOTS;
    /** Blanket input (Pallurite Pellets) and output (Kindled Pellets). */
    public static final int BLANKET_IN = ROD_SLOTS;
    public static final int BLANKET_OUT = ROD_SLOTS + 1;
    /** Machine slots: rods, then the blanket pair. */
    public static final int MACHINE_SLOTS = ROD_SLOTS + 2;
    /** Most Pallurite Pellets the blanket input holds. */
    public static final int BLANKET_IN_MAX = 16;

    // --- synced ints after NeroTech's seven (read through FissionCoreMenu) ----------------------
    public static final int EXTRA_POISON = ROD_SLOTS;
    public static final int EXTRA_CONTROL_ROD = ROD_SLOTS + 1;
    public static final int EXTRA_FAILURE_STAGE = ROD_SLOTS + 2;
    public static final int EXTRA_SHELL_SIZE = ROD_SLOTS + 3;
    public static final int EXTRA_MODE = ROD_SLOTS + 4;
    public static final int EXTRA_REQUESTED_MODE = ROD_SLOTS + 5;
    public static final int EXTRA_STEAM_PORTS = ROD_SLOTS + 6;
    public static final int EXTRA_BREED = ROD_SLOTS + 7;
    public static final int EXTRA_BLANKETS = ROD_SLOTS + 8;
    public static final int EXTRA_BLANKETS_COUNTED = ROD_SLOTS + 9;
    public static final int EXTRA_BLANKETS_MAX = ROD_SLOTS + 10;
    public static final int EXTRA_BLANKET_COST = ROD_SLOTS + 11;
    public static final int EXTRA_CRYO_FED = ROD_SLOTS + 12;
    public static final int EXTRA_CRYO_BAND = ROD_SLOTS + 13;
    public static final int EXTRA_SCRAM_TICKS = ROD_SLOTS + 14;
    public static final int EXTRA_MODERATOR = ROD_SLOTS + 15;
    public static final int EXTRA_READOUT = ROD_SLOTS + 16;
    public static final int EXTRA_STALL = ROD_SLOTS + 17;
    /** Pairs (15-bit halves, hi then lo): water, water capacity, steam, steam capacity (mB, summed over ports). */
    public static final int EXTRA_WATER = ROD_SLOTS + 18;
    public static final int EXTRA_WATER_CAP = ROD_SLOTS + 20;
    public static final int EXTRA_STEAM = ROD_SLOTS + 22;
    public static final int EXTRA_STEAM_CAP = ROD_SLOTS + 24;
    public static final int EXTRA_DATA = ROD_SLOTS + 26;

    /** Stall causes (synced; the screen's stall line). */
    public static final int STALL_NONE = 0;
    public static final int STALL_UNFORMED = 1;
    public static final int STALL_NO_FUEL = 2;
    public static final int STALL_POISONED = 3;
    public static final int STALL_NO_WATER = 4;
    public static final int STALL_STEAM_FULL = 5;
    public static final int STALL_NO_ROOM = 6;
    public static final int STALL_SCRAM = 15;

    /** Readout codes (render inputs). */
    public static final int READOUT_REST = 0;
    public static final int READOUT_WORK = 1;
    public static final int READOUT_STALL = 2;
    public static final int READOUT_HOT = 3;
    public static final int READOUT_TRIP = 4;
    public static final int READOUT_FAIL = 5;

    /** Record gate opened by the first bred pellet (declared by the progression data). */
    public static final Identifier FIRST_BREED = MilestoneGates.FIRST_BREED;

    private static final String KEY_MODE = "NpMode";
    private static final String KEY_BREED = "NpBreed";
    private static final String KEY_READOUT = "NpReadout";
    private static final String KEY_STALL = "NpStall";

    /** Whether this loaded core has already recorded its first burn (criticality + world log). Transient. */
    private boolean burnRecorded;
    /**
     * The operator this loaded core last tried to credit with criticality / first breed, so a running
     * reactor does not re-run the gate lookup every tick. Transient (never saved or synced), compared
     * for equality only; personal data: never log it.
     */
    @Nullable
    private UUID criticalityTried;
    @Nullable
    private UUID breedTried;

    /** Formed state (persisted so a reload never flickers through "unformed"). */
    private int shellSize;
    private int rodCount;
    /** Interior and wall contents beyond the shape; recomputed on every revalidation (transient). */
    private ReactorLayout layout = ReactorLayout.EMPTY;
    /** A structure recheck is due (member change, neighbour change, load, menu open). */
    private boolean recheckDue = true;
    /** Whether the core ran last tick (transient; an idle-to-running edge triggers a shell recheck). */
    private boolean wasRunning;

    /** Burn-up accumulators in permille x1000: Fuel Rods, and Braided Fuel Rods (their own rate). */
    private long burnupAccumulator;
    private long braidedAccumulator;

    private final PoisonModel poison = new PoisonModel();
    private final Breeder breeder = new Breeder();

    /** The mode the operator asked for (saved as {@code Mode}). */
    private ReactorMode requestedMode = ReactorMode.DIRECT;

    /** How long a SCRAM pins the control-rod factor at the floor (1200 ticks = 60 s). */
    public static final int SCRAM_TICKS = 1200;

    private boolean scramRequested;
    private int syncedFailureStage;
    private int scramTicksLeft;

    // Transient per-tick readings (menu, computer, render).
    private boolean cryoFed;
    private long lastRate;
    private int readout;
    private int stallCause = STALL_UNFORMED;
    private int renderMode;
    private int renderBreedStep;
    private int syncedRenderKey = -1;

    @Nullable
    private FissionMath.Curve curve;
    @Nullable
    private String curveSource;

    public FissionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(FissionContent.FISSION_CORE_TYPE.get(), pos, state, MACHINE_SLOTS);
        // GENERATOR preset: ENERGY OUTPUT on every face; rods and Pallurite IN on every face, and a face set
        // to OUTPUT lets automation pull spent rods and Kindled Pellets (fresh rods are guarded below).
        int[] in = {0, 1, 2, 3, 4, 5, BLANKET_IN};
        int[] out = {0, 1, 2, 3, 4, 5, BLANKET_OUT};
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .channel(Channel.ITEM, SlotGroup.of("rods", in), SlotGroup.of("spent", out))
                .defaultPreset(SidePreset.GENERATOR)
                .autoEject(Channel.ENERGY, true)
                .build());
    }

    // --- structure ----------------------------------------------------------------

    @Override
    public void invalidateThermalLinks() {
        super.invalidateThermalLinks();
        this.recheckDue = true;
    }

    public boolean formed() {
        return this.shellSize >= FissionStructure.MIN_SIZE;
    }

    /** The formed shell's edge (3 / 5 / 7), or 0. */
    public int shellSize() {
        return this.shellSize;
    }

    /** Control Rod Assemblies (tier 1) inside the shell at the last check. */
    public int rodCount() {
        return this.rodCount;
    }

    /** The interior and wall contents at the last check. */
    public ReactorLayout layout() {
        return this.layout;
    }

    public int usableRodSlots() {
        return FissionMath.usableRodSlots(this.shellSize);
    }

    public int poison() {
        return this.poison.poison();
    }

    /** The reactivity reduction (rods of both tiers and blankets) as permille; 1000 = nothing inside. */
    public int controlRodFactorPermille() {
        return (int) Math.round(controlRodFactor() * FissionMath.PERMILLE);
    }

    private double controlRodFactor() {
        if (this.scramTicksLeft > 0) {
            return FissionMath.CONTROL_ROD_FLOOR;
        }
        return FissionMath.reactivityFactor(this.rodCount, this.layout.europiumRods(), this.layout.blankets(),
                FissionMath.interiorVolume(this.shellSize), FissionConfig.controlRodPermille(),
                FissionConfig.europiumControlRodPermille(), FissionConfig.breederReactivityCostPermille());
    }

    /** The moderators' output and heat bonus, permille. */
    public int moderatorBonusPermille() {
        return FissionMath.moderatorBonusPermille(this.layout.moderatorScore(),
                FissionConfig.moderatorPermillePerContact(), FissionConfig.moderatorCapPermille());
    }

    /** The mode the core runs in now. */
    public ReactorMode mode() {
        return ReactorMode.effective(this.requestedMode, this.layout.steamPorts().size());
    }

    /** The mode the operator asked for. */
    public ReactorMode requestedMode() {
        return this.requestedMode;
    }

    /**
     * Ask for a mode (the screen's mode button). Refused (false) when steam is asked for without a
     * Reactor Steam Port in the shell, or while the core is at WARNING or worse.
     */
    public boolean requestMode(ReactorMode target) {
        if (target == this.requestedMode) {
            return true;
        }
        if (!ReactorMode.canRequest(target, this.layout.steamPorts().size())
                || failureStage().code() >= FailureStage.WARNING.code()) {
            return false;
        }
        this.requestedMode = target;
        setChanged();
        if (target == ReactorMode.STEAM && this.level != null && !this.level.isClientSide()) {
            this.level.playSound(null, this.worldPosition, za.co.neroland.neropower.registry.ModSounds.REACTOR_STEAM_MODE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    // --- SCRAM --------------------------------------------------------------------

    public void requestScram() {
        this.scramRequested = true;
    }

    public boolean scrammed() {
        return this.scramTicksLeft > 0;
    }

    public int scramTicksLeft() {
        return this.scramTicksLeft;
    }

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

    @Override
    public void markStructureDirty() {
        this.recheckDue = true;
    }

    /** Bounded structure re-validation, on change only (see the multiblock index). */
    private void revalidate(Level level, BlockPos pos, BlockState state) {
        if (!this.recheckDue || !MultiblockIndex.tryRevalidate(level.dimension(), level.getGameTime())) {
            return;
        }
        this.recheckDue = false;
        Direction facing = state.getValue(NeroTechMachineBlock.FACING);
        FissionStructure.Result result = FissionStructure.check(level, pos, facing);
        ReactorLayout nowLayout = ReactorLayout.EMPTY;
        if (result.valid()) {
            Bounds bounds = Bounds.of(result.min().getX(), result.min().getY(), result.min().getZ(),
                    result.max().getX(), result.max().getY(), result.max().getZ());
            MultiblockIndex.register(level.dimension(), pos.asLong(), bounds, this::markStructureDirty);
            nowLayout = ReactorLayout.scan(new LevelProbe(level, FissionStructure::cellOf), bounds);
        } else {
            MultiblockIndex.unregister(level.dimension(), pos.asLong());
        }
        this.layout = nowLayout;
        int nowSize = result.valid() ? result.shellSize() : 0;
        int nowRods = result.valid() ? result.rodCount() - nowLayout.europiumRods() : 0;
        if (nowSize == this.shellSize && nowRods == this.rodCount) {
            return;
        }
        this.shellSize = nowSize;
        this.rodCount = nowRods;
        setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /**
     * The ports this core served on its last formed tick: the members of the reactor's shared coolant and
     * steam tanks. Transient, never saved; empty while unformed.
     */
    private List<ReactorSteamPortBlockEntity> linkedPorts = List.of();

    /** The ports sharing this reactor's tanks (as of the core's last tick). */
    public List<ReactorSteamPortBlockEntity> linkedPorts() {
        return this.linkedPorts;
    }

    /** The Reactor Steam Ports of the formed shell that are loaded and present. */
    private List<ReactorSteamPortBlockEntity> ports(Level level) {
        List<int[]> at = this.layout.steamPorts();
        if (at.isEmpty()) {
            return List.of();
        }
        List<ReactorSteamPortBlockEntity> out = new ArrayList<>(at.size());
        for (int[] p : at) {
            BlockPos portPos = new BlockPos(p[0], p[1], p[2]);
            if (level.isLoaded(portPos) && level.getBlockEntity(portPos) instanceof ReactorSteamPortBlockEntity port) {
                out.add(port);
            }
        }
        return out;
    }

    /**
     * The operator in the moment of this reactor: whoever last worked the core, else its control
     * computer (NeroTech's transient {@code lastOperator}; empty for a gallery exhibit). Never saved.
     */
    @Nullable
    private UUID reactorOperator(Level level) {
        ReactorControlComputerBlockEntity c = computer(level);
        return OperatorCredit.firstKnown(lastOperator().orElse(null),
                c == null ? null : c.lastOperator().orElse(null));
    }

    @Nullable
    private ReactorControlComputerBlockEntity computer(Level level) {
        int[] p = this.layout.computer();
        if (p == null) {
            return null;
        }
        BlockPos at = new BlockPos(p[0], p[1], p[2]);
        return level.isLoaded(at) && level.getBlockEntity(at) instanceof ReactorControlComputerBlockEntity c ? c : null;
    }

    // --- tick -----------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        revalidate(level, pos, state);
        tickScram();

        if (tickFailure(level, pos) == FailureStage.FAILURE) {
            return;
        }

        if (!formed()) {
            reportStatus(MachineStatus.UNFORMED);
            idle();
            this.linkedPorts = List.of();
            this.cryoFed = false;
            this.lastRate = 0L;
            this.stallCause = STALL_UNFORMED;
            this.readout = READOUT_STALL;
            this.renderMode = ReactorMode.DIRECT.code();
            this.poison.tick(false, 0, FissionConfig.poisonDecayPerTick(), FissionConfig.poisonStallPermille());
            MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(),
                    sideConfig());
            return;
        }

        List<ReactorSteamPortBlockEntity> ports = ports(level);
        this.linkedPorts = List.copyOf(ports);
        for (ReactorSteamPortBlockEntity port : ports) {
            port.link(this);
        }
        ReactorMode mode = ReactorMode.effective(this.requestedMode, ports.size());
        boolean economizer = economizerFitted();
        boolean braidedActive = this.layout.heavyWaterScore() >= FissionConfig.braidedHeavyWaterContacts();
        int braidedPermille = FissionConfig.braidedOutputPermille();

        FissionMath.Curve curve = curve();
        int usable = usableRodSlots();
        double rodSum = 0.0D;
        double heatSum = 0.0D;
        long burnupTotal = 0L;
        int worstBurnup = -1;
        int loaded = 0;
        for (int slot = 0; slot < usable; slot++) {
            ItemStack stack = this.items.get(slot);
            boolean fuel = isFuelRod(stack);
            boolean braided = !fuel && braidedActive && isBraidedRod(stack);
            if (!fuel && !braided) {
                continue;
            }
            int burnup = burnupOf(stack);
            double scale = braided ? braidedPermille / (double) FissionMath.PERMILLE : 1.0D;
            rodSum += FissionMath.outputFactor(curve, burnup) / (double) FissionMath.PERMILLE * scale;
            heatSum += FissionMath.heatFactor(curve, burnup) / (double) FissionMath.PERMILLE * scale;
            burnupTotal += burnup;
            worstBurnup = Math.max(worstBurnup, burnup);
            loaded++;
        }

        boolean steam = mode == ReactorMode.STEAM;
        boolean roomToStore = steam || getEnergy().getAmount() < getEnergy().getCapacity();
        boolean stalled = this.poison.stalled();
        boolean running = loaded > 0 && roomToStore && !stalled;
        if (running && !this.wasRunning) {
            // Idle to running: re-check the shell next tick ("validate on change"; a block
            // swapped with no neighbour update would otherwise only be caught on load or menu open).
            this.recheckDue = true;
        }
        this.wasRunning = running;
        double reduction = controlRodFactor();
        double crf = reduction * (1.0D + moderatorBonusPermille() / (double) FissionMath.PERMILLE);
        int failurePermille = failureOutputPermille();
        int outputPermille = economizer ? FissionConfig.economizerOutputPermille() : FissionMath.PERMILLE;

        long rate = 0L;
        int stall = STALL_NONE;
        boolean cryo = false;
        if (running) {
            double speed = presetSpeedFactor() * modifiers().speedMultiplier();
            // Steam mode: heat is the product, so it follows the work rate as burn-up does.
            int heat = steam ? FissionMath.steamModeHeat(FissionConfig.heatPerRodPerTick(), heatSum, crf, speed)
                    : FissionMath.heatPerTick(FissionConfig.heatPerRodPerTick(), heatSum, crf);
            int residual = heat;
            if (!steam) {
                long output = FissionMath.outputPerTick(FissionConfig.nePerRod(), rodSum, crf, speed, failurePermille);
                if (economizer) {
                    output = output * outputPermille / FissionMath.PERMILLE;
                }
                if (output > 0) {
                    energyBuffer().generate((int) Math.min(Integer.MAX_VALUE, output));
                }
                rate = output;
            } else {
                int steamPerHeat = FissionConfig.steamPerHeat();
                int steamPermille = (int) ((long) outputPermille * Math.max(0, failurePermille) / FissionMath.PERMILLE);
                // Shared tanks: water from any port, steam into any port with room.
                long water = 0L;
                long room = 0L;
                for (ReactorSteamPortBlockEntity port : ports) {
                    water += port.waterAvailable();
                    room += port.steamRoom();
                }
                boolean anyWater = water > 0L;
                boolean anyRoom = room > 0L;
                SteamBoil boil = SteamBoil.boil(residual, steamPerHeat, steamPermille, water, room);
                if (boil.heatTaken() > 0) {
                    long drawLeft = boil.waterUsed();
                    for (ReactorSteamPortBlockEntity port : ports) {
                        if (drawLeft <= 0L) {
                            break;
                        }
                        long here = port.waterAvailable();
                        if (here > 0L) {
                            drawLeft -= port.drawCoolant(Math.min(here, drawLeft));
                        }
                    }
                    long raiseLeft = boil.steam();
                    for (ReactorSteamPortBlockEntity port : ports) {
                        if (raiseLeft <= 0L) {
                            break;
                        }
                        long added = port.addSteam(Math.min(raiseLeft, port.steamRoom()));
                        raiseLeft -= added;
                        rate += added;
                    }
                    residual -= boil.heatTaken();
                }
                if (residual > 0) {
                    stall = !anyWater ? STALL_NO_WATER : !anyRoom ? STALL_STEAM_FULL : STALL_NONE;
                }
            }
            // Cryo coolant carries off what stays in the core (either mode).
            int perMb = FissionConfig.cryoHeatPerMb();
            for (ReactorSteamPortBlockEntity port : ports) {
                long available = port.cryoAvailable();
                if (available <= 0L) {
                    continue;
                }
                cryo = true;
                if (residual <= 0) {
                    continue;
                }
                long need = (residual + perMb - 1L) / perMb;
                long drawn = port.drawCoolant(Math.min(need, available));
                residual -= (int) Math.min(residual, drawn * perMb);
            }
            if (residual > 0) {
                addHeat(residual);
            }
            advanceBurnup(usable, speed, economizer, braidedActive);
            this.maxProgress = FissionMath.PERMILLE;
            this.progress = (int) (burnupTotal / loaded);
            setActive(true);
        } else {
            for (ReactorSteamPortBlockEntity port : ports) {
                cryo |= port.cryoAvailable() > 0L;
            }
            idle();
            if (loaded == 0) {
                reportStatus(MachineStatus.STARVED);
                stall = STALL_NO_FUEL;
            } else if (stalled) {
                reportStatus(MachineStatus.THROTTLED);
                stall = STALL_POISONED;
            } else {
                reportStatus(MachineStatus.BLOCKED);
                stall = STALL_NO_ROOM;
            }
        }
        this.cryoFed = cryo;
        this.lastRate = rate;
        if (stall == STALL_NO_WATER) {
            reportStatus(MachineStatus.STARVED);
        } else if (stall == STALL_STEAM_FULL) {
            reportStatus(MachineStatus.BLOCKED);
        }

        tickBreeder(level, running && !scrammed(), loaded > 0 ? rodSum / loaded * crf : 0.0D);

        boolean accumulating = FissionMath.poisonAccumulates(running, failurePermille, loaded, usable,
                controlRodFactorPermille(), FissionConfig.poisonRodThresholdPermille());
        boolean nowStalled = this.poison.tick(accumulating, FissionConfig.poisonPerTick(),
                FissionConfig.poisonDecayPerTick(), FissionConfig.poisonStallPermille());
        if (nowStalled != stalled) {
            setChanged();
        }

        // Readout and stall line.
        if (scrammed()) {
            stall = STALL_SCRAM;
        }
        FailureStage stage = failureStage();
        this.stallCause = stall;
        this.readout = stage == FailureStage.UNSTABLE ? READOUT_FAIL
                : stage == FailureStage.WARNING ? READOUT_HOT
                : scrammed() ? READOUT_TRIP
                : running && stall == STALL_NONE ? READOUT_WORK
                : stall != STALL_NONE ? READOUT_STALL : READOUT_REST;
        this.renderMode = mode.code();

        feedComputer(level, usable, loaded, worstBurnup, rate, steam, stage);

        MachineEnergy.pushToNeighbours(level, pos, energyBuffer(), NeroTechConfig.machineMaxTransfer(),
                sideConfig());
    }

    /** Hand the computer this tick's readings; its auto-SCRAM requests a SCRAM. */
    private void feedComputer(Level level, int usable, int loaded, int worstBurnup, long rate, boolean steam,
            FailureStage stage) {
        ReactorControlComputerBlockEntity computer = computer(level);
        if (computer == null) {
            return;
        }
        int capacity = heatCapacity();
        int heatPermille = capacity <= 0 ? 0 : (int) Math.min(1000L, (long) heat() * 1000L / capacity);
        int fuelPermille = loaded > 0 ? FissionMath.MAX_BURNUP - Math.max(0, worstBurnup) : 0;
        long rated = steam
                ? (long) FissionConfig.heatPerRodPerTick() * FissionConfig.steamPerHeat() * Math.max(1, usable)
                : (long) FissionConfig.nePerRod() * Math.max(1, usable);
        int outputPermille = rated <= 0L ? 0 : (int) Math.min(1000L, rate * 1000L / rated);
        if (computer.feed(heatPermille, fuelPermille, loaded, outputPermille, rate, steam, stage.code())) {
            requestScram();
        }
    }

    /** Breed while the core runs and is not SCRAMmed (the {@link Breeder} rule). */
    private void tickBreeder(Level level, boolean running, double outputFactor) {
        ItemStack in = this.items.get(BLANKET_IN);
        ItemStack out = this.items.get(BLANKET_OUT);
        ItemStack kindled = new ItemStack(za.co.neroland.neropower.registry.content.FissionContent.KINDLED_PELLET.get());
        boolean canBreed = this.layout.blankets() > 0 && isPallurite(in)
                && (out.isEmpty() || (ItemStack.isSameItemSameComponents(out, kindled) && out.getCount() < out.getMaxStackSize()));
        int step = FissionMath.breedStep(this.layout.blankets(), FissionConfig.breederMaxBlankets(),
                (int) Math.round(outputFactor * FissionMath.PERMILLE));
        int ticksPerPellet = FissionConfig.breederTicksPerPellet();
        int before = this.breeder.progress();
        if (this.breeder.tick(running, canBreed, step, ticksPerPellet)) {
            in.shrink(1);
            if (out.isEmpty()) {
                this.items.set(BLANKET_OUT, kindled);
            } else {
                out.grow(1);
            }
            if (level instanceof ServerLevel serverLevel) {
                MilestoneGates.record(serverLevel, ownerId(), FIRST_BREED);
                UUID operator = reactorOperator(serverLevel);
                if (OperatorCredit.shouldTry(true, operator, this.breedTried)
                        && MilestoneGates.online(serverLevel, operator)) {
                    this.breedTried = operator;
                    MilestoneGates.credit(serverLevel, operator, FIRST_BREED);
                }
            }
            setChanged();
        } else if (this.breeder.progress() != before) {
            setChanged();
        }
        this.renderBreedStep = this.breeder.step(ticksPerPellet);
    }

    private void idle() {
        this.progress = 0;
        this.maxProgress = 0;
        setActive(false);
    }

    /**
     * Age every loaded rod. Fuel Rods share one accumulator ({@code fissionBurnupPerTick x speed}, permille
     * x1000); Braided Fuel Rods (only while they fission) another at {@code fissionBraidedBurnupPermille} of
     * that rate. A Fuel Economizer scales both. A rod reaching 1000‰ becomes a Hot Spent Fuel Rod in place.
     */
    private void advanceBurnup(int usable, double speed, boolean economizer, boolean braidedActive) {
        double econ = economizer ? FissionConfig.economizerBurnupPermille() / (double) FissionMath.PERMILLE : 1.0D;
        long step = Math.max(1L, Math.round(FissionConfig.burnupPerTick() * speed * econ));
        this.burnupAccumulator += step;
        int delta = 0;
        if (this.burnupAccumulator >= 1000L) {
            delta = (int) Math.min(FissionMath.MAX_BURNUP, this.burnupAccumulator / 1000L);
            this.burnupAccumulator %= 1000L;
        }
        int braidedDelta = 0;
        if (braidedActive) {
            long braidedStep = Math.max(1L, Math.round(FissionConfig.burnupPerTick() * speed * econ
                    * FissionConfig.braidedBurnupPermille() / FissionMath.PERMILLE));
            this.braidedAccumulator += braidedStep;
            if (this.braidedAccumulator >= 1000L) {
                braidedDelta = (int) Math.min(FissionMath.MAX_BURNUP, this.braidedAccumulator / 1000L);
                this.braidedAccumulator %= 1000L;
            }
        }
        if (delta == 0 && braidedDelta == 0) {
            return;
        }
        if (this.level instanceof ServerLevel serverLevel) {
            if (!this.burnRecorded) {
                this.burnRecorded = true;
                MilestoneGates.record(serverLevel, ownerId(), MilestoneGates.CRITICALITY);
                if (!isGalleryExhibit()) { // the gallery opens no world-first crossing
                    FissionWorldLog.recordBurn(serverLevel);
                }
            }
            // Criticality is the real signal: rods aged this tick in a formed core (not crafting it).
            // Credit the online operator in the moment — whoever last worked the core or its computer.
            UUID operator = reactorOperator(serverLevel);
            if (OperatorCredit.shouldTry(OperatorCredit.critical(formed(), Math.max(delta, braidedDelta)),
                    operator, this.criticalityTried) && MilestoneGates.online(serverLevel, operator)) {
                this.criticalityTried = operator;
                MilestoneGates.credit(serverLevel, operator, MilestoneGates.CRITICALITY);
            }
        }
        for (int slot = 0; slot < usable; slot++) {
            ItemStack stack = this.items.get(slot);
            int d = isFuelRod(stack) ? delta : braidedActive && isBraidedRod(stack) ? braidedDelta : 0;
            if (d == 0) {
                continue;
            }
            int burnup = burnupOf(stack) + d;
            if (FissionMath.spent(burnup)) {
                this.items.set(slot, new ItemStack(
                        za.co.neroland.neropower.registry.content.FissionContent.HOT_SPENT_FUEL_ROD.get(), 1));
            } else {
                stack.set(FissionContent.BURNUP.get(), burnup);
            }
        }
        setChanged();
    }

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

    // --- failure ladder: cryo widens the safe band ------------------------------------

    /**
     * While a port holds cryo coolant, WARNING and UNSTABLE sit {@code fissionCryoSafeBandPermille} higher:
     * below the FAILURE line the ladder sees the heat lowered by the band (it climbs one rung at a time, so
     * FAILURE is still reached only at the real FAILURE line). Otherwise the shared ladder as is.
     */
    @Override
    protected FailureStage tickFailure(Level level, BlockPos pos) {
        if (!this.cryoFed) {
            return super.tickFailure(level, pos);
        }
        FailureController controller = failure();
        int capacity = heatCapacity();
        int heat = heat();
        long failHeat = (long) capacity * controller.thresholds().failurePermille() / FissionMath.PERMILLE;
        int band = (int) ((long) capacity * FissionConfig.cryoSafeBandPermille() / FissionMath.PERMILLE);
        int seen = heat >= failHeat ? heat : Math.max(0, heat - band);
        FailureStage stage = controller.tick(seen, capacity, NeroPowerConfig.overloadEnabled());
        if (stage == FailureStage.FAILURE && controller.justEntered() && level instanceof ServerLevel serverLevel) {
            if (!isGalleryExhibit()) { // a gallery exhibit telegraphs its failure but never runs the action
                failureAction().apply(serverLevel, pos, failureContext());
            }
        }
        return stage;
    }

    // --- rods and slots ---------------------------------------------------------------

    /** Whether {@code stack} is a Fuel Rod (spent rods are a different item and never count). */
    public static boolean isFuelRod(ItemStack stack) {
        return !stack.isEmpty() && stack.is(FissionContent.FUEL_ROD.get());
    }

    /** Whether {@code stack} is a Braided Fuel Rod. */
    public static boolean isBraidedRod(ItemStack stack) {
        return !stack.isEmpty() && stack.is(za.co.neroland.neropower.registry.content.FissionContent.BRAIDED_FUEL_ROD.get());
    }

    /** Whether {@code stack} is a spent rod of either temperature. */
    public static boolean isSpentRod(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(FissionContent.SPENT_FUEL_ROD.get())
                || stack.is(za.co.neroland.neropower.registry.content.FissionContent.HOT_SPENT_FUEL_ROD.get()));
    }

    private static boolean isPallurite(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(za.co.neroland.neropower.registry.content.FissionContent.PALLURITE_PELLET.get());
    }

    /** A rod's burn-up (permille); a rod without the component is fresh. */
    public static int burnupOf(ItemStack stack) {
        Integer burnup = stack.get(FissionContent.BURNUP.get());
        return burnup == null ? 0 : FissionMath.clampBurnup(burnup);
    }

    /** The burn-up in rod slot {@code slot} (0 for an empty or non-rod slot, 1000 for a spent rod). */
    public int slotBurnup(int slot) {
        if (slot < 0 || slot >= ROD_SLOTS) {
            return 0;
        }
        ItemStack stack = this.items.get(slot);
        if (isFuelRod(stack) || isBraidedRod(stack)) {
            return burnupOf(stack);
        }
        return isSpentRod(stack) ? FissionMath.MAX_BURNUP : 0;
    }

    /**
     * What {@code stack} becomes on leaving the core: a Fuel or Braided Fuel Rod with any
     * burn-up is a Hot Spent Fuel Rod; anything else, fresh rods included, is returned as is.
     */
    public static ItemStack leavingCore(ItemStack stack) {
        boolean rod = isFuelRod(stack) || isBraidedRod(stack);
        if (FissionMath.leavesSpent(rod, rod ? burnupOf(stack) : 0)) {
            return new ItemStack(za.co.neroland.neropower.registry.content.FissionContent.HOT_SPENT_FUEL_ROD.get(),
                    stack.getCount());
        }
        return stack;
    }

    /**
     * Turn the part-burnt rod in rod slot {@code slot} (if any) into a Hot Spent Fuel Rod in place, because
     * it is about to leave the core. Returns the rod that was there when it was converted, else null.
     */
    @Nullable
    public ItemStack spendLeavingRod(int slot) {
        if (slot < 0 || slot >= ROD_SLOTS) {
            return null;
        }
        ItemStack before = this.items.get(slot);
        ItemStack after = leavingCore(before);
        if (after == before) {
            return null;
        }
        this.items.set(slot, after);
        setChanged();
        return before;
    }

    /** Put a rod back that {@link #spendLeavingRod} converted but that did not leave after all. */
    public void restoreRod(int slot, ItemStack converted, ItemStack original) {
        if (slot >= 0 && slot < ROD_SLOTS && this.items.get(slot) == converted) {
            this.items.set(slot, original);
            setChanged();
        }
    }

    /** Every part-burnt rod leaves as spent: run before the slots spill (block removed or broken). */
    public void spendLeavingRods() {
        for (int slot = 0; slot < ROD_SLOTS; slot++) {
            spendLeavingRod(slot);
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        spendLeavingRod(slot);
        return super.removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        spendLeavingRod(slot);
        return super.removeItemNoUpdate(slot);
    }

    //? if >=1.21.5 {
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        spendLeavingRods();
        super.preRemoveSideEffects(pos, state);
    }
    //?}

    /** Usable rod slots take Fuel and Braided Fuel Rods; slot 6 Pallurite Pellets; slot 7 nothing. */
    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        if (slot < ROD_SLOTS) {
            return slot < usableRodSlots() && (isFuelRod(stack) || isBraidedRod(stack));
        }
        if (slot == BLANKET_IN) {
            return isPallurite(stack) && this.items.get(BLANKET_IN).getCount() < BLANKET_IN_MAX;
        }
        return false;
    }

    /** Automation may pull a rod slot only once its rod is spent, and the blanket output at any time. */
    @Override
    public boolean canTakeMachineItem(int slot) {
        if (slot < ROD_SLOTS) {
            return isSpentRod(this.items.get(slot));
        }
        return slot == BLANKET_OUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return canTakeMachineItem(slot) && super.canTakeItemThroughFace(slot, stack, side);
    }

    /** The upgrade slots also take the Fuel Economizer (read here; Core's classifier never counts it). */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot >= this.machineSlots && isEconomizer(stack)) {
            return true;
        }
        return super.canPlaceItem(slot, stack);
    }

    private static boolean isEconomizer(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(za.co.neroland.neropower.registry.content.FissionContent.FUEL_ECONOMIZER.get());
    }

    /** Whether a Fuel Economizer sits in an upgrade slot. */
    public boolean economizerFitted() {
        for (int i = 0; i < upgrades().slots(); i++) {
            if (isEconomizer(upgrades().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    // --- failure action -----------------------------------------------------------

    /**
     * Every failure path, cryo-fed or not: first the contamination burst on the core's chunk and its eight
     * neighbours (nothing while radiation is off, for an exhibit or on protected ground), then the blast,
     * which grows with the shell (5 / 7 / 9 before the cap), and the scorch zone when enabled.
     */
    @Override
    protected FailureAction failureAction() {
        return withBurst(new ScorchZoneAction(new Explode(Math.max(FissionStructure.MIN_SIZE, this.shellSize) + 2)));
    }

    /** {@code action} preceded by the reactor's radiation burst. */
    static FailureAction withBurst(FailureAction action) {
        FailureAction burst = (level, pos, ctx) ->
                za.co.neroland.neropower.radiation.RadiationSources.reactorFailure(level, pos);
        return burst.andThen(action);
    }

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

    // --- render read surface (synced via the update tag) ------------------------------

    public FailureStage renderFailureStage() {
        return failureStage();
    }

    /** Synced: 0 direct, 1 steam (the steam-mode front). */
    public int renderMode() {
        return this.renderMode;
    }

    /** Synced: breeding progress bucket 0..4. */
    public int renderBreedStep() {
        return this.renderBreedStep;
    }

    public int renderReadout() {
        return this.readout;
    }

    public int renderStallCause() {
        return this.stallCause;
    }

    @Override
    protected boolean renderSyncDirty() {
        int stage = failureStage().code();
        int key = ((((stage * 2 + this.renderMode) * 8 + this.renderBreedStep) * 8 + this.readout) * 16) + this.stallCause;
        boolean dirty = stage != this.syncedFailureStage || key != this.syncedRenderKey;
        this.syncedFailureStage = stage;
        this.syncedRenderKey = key;
        return dirty;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(KEY_MODE, this.renderMode);
        tag.putInt(KEY_BREED, this.renderBreedStep);
        tag.putInt(KEY_READOUT, this.readout);
        tag.putInt(KEY_STALL, this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.renderMode = input.getIntOr(KEY_MODE, 0);
        this.renderBreedStep = input.getIntOr(KEY_BREED, 0);
        this.readout = input.getIntOr(KEY_READOUT, 0);
        this.stallCause = input.getIntOr(KEY_STALL, 0);
    }

    // --- menu sync ------------------------------------------------------------------

    /** NE/t (direct) or mB/t of steam (steam mode) this tick; the menu's wide value. */
    public long lastRate() {
        return this.lastRate;
    }

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
            case EXTRA_MODE -> mode().code();
            case EXTRA_REQUESTED_MODE -> this.requestedMode.code();
            case EXTRA_STEAM_PORTS -> this.layout.steamPorts().size();
            case EXTRA_BREED -> this.breeder.permille(FissionConfig.breederTicksPerPellet());
            case EXTRA_BLANKETS -> this.layout.blankets();
            case EXTRA_BLANKETS_COUNTED -> Math.min(this.layout.blankets(), FissionConfig.breederMaxBlankets());
            case EXTRA_BLANKETS_MAX -> FissionConfig.breederMaxBlankets();
            case EXTRA_BLANKET_COST -> FissionConfig.breederReactivityCostPermille();
            case EXTRA_CRYO_FED -> this.cryoFed ? 1 : 0;
            case EXTRA_CRYO_BAND -> FissionConfig.cryoSafeBandPermille();
            case EXTRA_SCRAM_TICKS -> this.scramTicksLeft;
            case EXTRA_MODERATOR -> moderatorBonusPermille();
            case EXTRA_READOUT -> this.readout;
            case EXTRA_STALL -> this.stallCause;
            default -> tankData(index);
        };
    }

    /** The summed port tank pairs (hi, lo halves). */
    private int tankData(int index) {
        if (index < EXTRA_WATER || index >= EXTRA_DATA || this.level == null) {
            return 0;
        }
        long water = 0L;
        long waterCap = 0L;
        long steamMb = 0L;
        long steamCap = 0L;
        for (ReactorSteamPortBlockEntity port : ports(this.level)) {
            water += port.waterAvailable();
            waterCap += port.coolantCapacity();
            steamMb += port.steamAmount();
            steamCap += port.steamCapacity();
        }
        int pair = (index - EXTRA_WATER) / 2;
        long value = switch (pair) {
            case 0 -> water;
            case 1 -> waterCap;
            case 2 -> steamMb;
            default -> steamCap;
        };
        boolean hi = (index - EXTRA_WATER) % 2 == 0;
        return hi ? za.co.neroland.neropower.menu.GaugeTail.hi2(value) : za.co.neroland.neropower.menu.GaugeTail.lo2(value);
    }

    // --- persistence ----------------------------------------------------------------

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
        new FissionCoreSave(this.shellSize, this.rodCount, this.burnupAccumulator, this.poison.poison(),
                this.poison.stalled(), this.scramTicksLeft).write(SaveAdapters.writer(output));
        // 0.4.0 additive keys (absent in 0.3.x saves: direct mode, no breeding progress, no braided rods).
        output.putString("Mode", this.requestedMode.id());
        output.putInt("BreedProgress", this.breeder.progress());
        output.putLong("BraidedBurnupAcc", this.braidedAccumulator);
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
        FissionCoreSave saved = FissionCoreSave.read(SaveAdapters.reader(input));
        this.shellSize = saved.shellSize();
        this.rodCount = saved.rodCount();
        this.burnupAccumulator = saved.burnupAccumulator();
        this.poison.load(saved.poison(), saved.stalled());
        this.scramTicksLeft = saved.scramTicks();
        this.requestedMode = ReactorMode.byId(input.getStringOr("Mode", ReactorMode.DIRECT.id()));
        this.breeder.load(input.getIntOr("BreedProgress", 0));
        this.braidedAccumulator = Math.max(0L, input.getLongOr("BraidedBurnupAcc", 0L));
        this.recheckDue = true;
    }

    // --- misc -------------------------------------------------------------------------

    @Override
    public void setRemoved() {
        super.setRemoved();
        Level level = getLevel();
        if (level != null && !level.isClientSide()) {
            MultiblockIndex.unregister(level.dimension(), this.worldPosition.asLong());
        }
    }

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
        this.recheckDue = true; // opening the screen re-checks the shell (bounds a setBlock with no update)
        return new FissionCoreMenu(containerId, playerInventory, this, this.data);
    }
}

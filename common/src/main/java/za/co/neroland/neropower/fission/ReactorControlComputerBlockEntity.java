package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.registry.content.FissionContent;

/**
 * Reactor Control Computer: the reactor's automation hook. The Fission Core feeds it its readings every
 * tick ({@link #feed}) and asks whether to SCRAM ({@link ControlComputerLogic#shouldScram}). The redstone
 * output and comparator follow the signals chosen in its screen ({@link ControlComputerLogic}). Takes no
 * power and holds no items; its settings are machine state only.
 *
 * <p>Saved (additive, each with a default): {@code ThresholdHeat}, {@code ThresholdFuel},
 * {@code ThresholdOutput} (permille), {@code ThresholdStage} (1..3), {@code AutoScram},
 * {@code AutoScramAt} (permille), {@code RedstoneSignal} (0..4, 4 off), {@code ComparatorSignal} (0..3).
 * Synced render inputs: {@code readout} (rest / work / hot / trip) and {@code stallCause} (0 linked,
 * 1 not part of a formed reactor).
 */
public class ReactorControlComputerBlockEntity extends NeroPowerMachineBlockEntity {

    /** Machine-specific synced ints after NeroTech's seven. */
    public static final int EXTRA_HEAT = 0;
    public static final int EXTRA_FUEL = 1;
    public static final int EXTRA_RODS = 2;
    public static final int EXTRA_OUTPUT = 3;
    public static final int EXTRA_STEAM_MODE = 4;
    public static final int EXTRA_STAGE = 5;
    public static final int EXTRA_T_HEAT = 6;
    public static final int EXTRA_T_FUEL = 7;
    public static final int EXTRA_T_OUTPUT = 8;
    public static final int EXTRA_T_STAGE = 9;
    public static final int EXTRA_AUTO_SCRAM = 10;
    public static final int EXTRA_AUTO_SCRAM_AT = 11;
    public static final int EXTRA_REDSTONE_SIGNAL = 12;
    public static final int EXTRA_COMPARATOR_SIGNAL = 13;
    public static final int EXTRA_REDSTONE_NOW = 14;
    public static final int EXTRA_LINKED = 15;
    public static final int EXTRA_TRIPPED = 16;
    public static final int EXTRA_DATA = 17;

    /** Ticks without a feed before the computer counts as unlinked. */
    private static final int LINK_TICKS = 20;
    /** Lamp test: how long the alarm look is forced, and the minimum gap between tests. */
    private static final int LAMP_TEST_TICKS = 60;
    private static final int LAMP_TEST_COOLDOWN = 100;

    private static final String KEY_READOUT = "NpReadout";
    private static final String KEY_STALL = "NpStall";

    // Settings (saved).
    private int thresholdHeat = ControlComputerLogic.DEFAULT_THRESHOLD_HEAT;
    private int thresholdFuel = ControlComputerLogic.DEFAULT_THRESHOLD_FUEL;
    private int thresholdOutput = ControlComputerLogic.DEFAULT_THRESHOLD_OUTPUT;
    private int thresholdStage = ControlComputerLogic.DEFAULT_THRESHOLD_STAGE;
    private boolean autoScram;
    private int autoScramAt = ControlComputerLogic.DEFAULT_AUTO_SCRAM_AT;
    private int redstoneSignal = ControlComputerLogic.DEFAULT_REDSTONE_SIGNAL;
    private int comparatorSignal = ControlComputerLogic.DEFAULT_COMPARATOR_SIGNAL;

    // Readings from the core (transient).
    private int heat;
    private int fuel;
    private int rods;
    private int output;
    private long outputRate;
    private boolean steamMode;
    private int stage;
    private int sinceFeed = LINK_TICKS;
    private boolean tripped;

    private int lampTestTicks;
    private long lastLampTest = Long.MIN_VALUE / 2;

    private int lastComparator = -1;
    private int lastRedstone = -1;
    private int readout;
    private int stallCause = 1;
    private int syncedReadout = -1;
    private int syncedStall = -1;

    public ReactorControlComputerBlockEntity(BlockPos pos, BlockState state) {
        super(FissionContent.REACTOR_CONTROL_COMPUTER_TYPE.get(), pos, state, 0);
        // No power, fluid or items: the energy channel exists only so the side-config widget has something
        // to show, and it is deliberately disabled on every face.
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build());
    }

    // --- the core's side --------------------------------------------------------------------------

    /**
     * The core's readings for this tick.
     *
     * @param heatPermille   heat / capacity
     * @param fuelPermille   fuel left in the most burnt usable rod (0 with no rods)
     * @param rodsLoaded     rods loaded in usable slots
     * @param outputPermille output or steam against the rated output
     * @param rate           NE/t (direct) or mB/t of steam (steam mode)
     * @param steam          whether the core runs in steam mode
     * @param stageCode      failure stage 0..3
     * @return whether the core should SCRAM now
     */
    public boolean feed(int heatPermille, int fuelPermille, int rodsLoaded, int outputPermille, long rate,
            boolean steam, int stageCode) {
        this.heat = clamp(heatPermille);
        this.fuel = clamp(fuelPermille);
        this.rods = Math.max(0, rodsLoaded);
        this.output = clamp(outputPermille);
        this.outputRate = Math.max(0L, rate);
        this.steamMode = steam;
        this.stage = Math.max(0, Math.min(3, stageCode));
        this.sinceFeed = 0;
        this.tripped = ControlComputerLogic.shouldScram(this.autoScram, this.heat, this.autoScramAt);
        return this.tripped;
    }

    private static int clamp(int permille) {
        return Math.max(0, Math.min(1000, permille));
    }

    /** Whether a core fed this computer within the last second. */
    public boolean linked() {
        return this.sinceFeed < LINK_TICKS;
    }

    private int threshold(int signal) {
        return switch (signal) {
            case ControlComputerLogic.HEAT -> this.thresholdHeat;
            case ControlComputerLogic.FUEL -> this.thresholdFuel;
            case ControlComputerLogic.OUTPUT -> this.thresholdOutput;
            default -> this.thresholdStage;
        };
    }

    /** The comparator level for the chosen signal (0 while unlinked). */
    public int comparatorSignal() {
        return linked() ? ControlComputerLogic.comparator(this.comparatorSignal, this.heat, this.fuel, this.output,
                this.stage) : 0;
    }

    /** The redstone output, 0 or 15 (0 while unlinked). */
    public int redstoneOutput() {
        return linked() && ControlComputerLogic.on(this.redstoneSignal, this.heat, this.fuel, this.output,
                this.stage, threshold(this.redstoneSignal)) ? 15 : 0;
    }

    /** NE/t or mB/t now (the menu's wide value). */
    public long outputRate() {
        return linked() ? this.outputRate : 0L;
    }

    // --- screen controls (validated and clamped by the menu) ---------------------------------------

    public void setThreshold(int signal, int value) {
        int v = ControlComputerLogic.clampThreshold(signal, value);
        switch (signal) {
            case ControlComputerLogic.HEAT -> this.thresholdHeat = v;
            case ControlComputerLogic.FUEL -> this.thresholdFuel = v;
            case ControlComputerLogic.OUTPUT -> this.thresholdOutput = v;
            case ControlComputerLogic.STAGE -> this.thresholdStage = v;
            default -> {
                return;
            }
        }
        setChanged();
    }

    public void setAutoScram(boolean on) {
        this.autoScram = on;
        setChanged();
    }

    public void setAutoScramAt(int permille) {
        this.autoScramAt = ControlComputerLogic.clampAutoScram(permille);
        setChanged();
    }

    public void setRedstoneSignal(int signal) {
        this.redstoneSignal = Math.max(0, Math.min(ControlComputerLogic.OFF, signal));
        setChanged();
    }

    public void setComparatorSignal(int signal) {
        this.comparatorSignal = Math.max(0, Math.min(ControlComputerLogic.SIGNALS - 1, signal));
        setChanged();
    }

    /** Light the alarm look for three seconds; at most once every five. Changes nothing else. */
    public void testLamps() {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now - this.lastLampTest < LAMP_TEST_COOLDOWN) {
            return;
        }
        this.lastLampTest = now;
        this.lampTestTicks = LAMP_TEST_TICKS;
    }

    // --- tick -------------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        if (this.sinceFeed < LINK_TICKS) {
            this.sinceFeed++;
        }
        if (this.lampTestTicks > 0) {
            this.lampTestTicks--;
        }
        boolean linked = linked();
        if (!linked) {
            this.heat = 0;
            this.fuel = 0;
            this.rods = 0;
            this.output = 0;
            this.outputRate = 0L;
            this.stage = 0;
            this.tripped = false;
        }
        int comparator = comparatorSignal();
        if (comparator != this.lastComparator) {
            this.lastComparator = comparator;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        int redstone = redstoneOutput();
        if (redstone != this.lastRedstone) {
            this.lastRedstone = redstone;
            level.updateNeighborsAt(pos, state.getBlock());
        }
        boolean alarm = this.lampTestTicks > 0 || (linked && this.stage >= 1);
        if (state.hasProperty(NeroPowerMachineBlock.ALARM) && state.getValue(NeroPowerMachineBlock.ALARM) != alarm) {
            level.setBlock(pos, state.setValue(NeroPowerMachineBlock.ALARM, alarm), Block.UPDATE_CLIENTS);
        }
        this.stallCause = linked ? 0 : 1;
        this.readout = !linked ? 2 : this.tripped ? 4 : this.stage >= 1 ? 3 : 1;
        setActive(linked);
    }

    // --- menu sync ----------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case EXTRA_HEAT -> this.heat;
            case EXTRA_FUEL -> this.fuel;
            case EXTRA_RODS -> this.rods;
            case EXTRA_OUTPUT -> this.output;
            case EXTRA_STEAM_MODE -> this.steamMode ? 1 : 0;
            case EXTRA_STAGE -> this.stage;
            case EXTRA_T_HEAT -> this.thresholdHeat;
            case EXTRA_T_FUEL -> this.thresholdFuel;
            case EXTRA_T_OUTPUT -> this.thresholdOutput;
            case EXTRA_T_STAGE -> this.thresholdStage;
            case EXTRA_AUTO_SCRAM -> this.autoScram ? 1 : 0;
            case EXTRA_AUTO_SCRAM_AT -> this.autoScramAt;
            case EXTRA_REDSTONE_SIGNAL -> this.redstoneSignal;
            case EXTRA_COMPARATOR_SIGNAL -> this.comparatorSignal;
            case EXTRA_REDSTONE_NOW -> redstoneOutput();
            case EXTRA_LINKED -> linked() ? 1 : 0;
            case EXTRA_TRIPPED -> this.tripped ? 1 : 0;
            default -> 0;
        };
    }

    // --- render sync --------------------------------------------------------------------------------

    public int renderReadout() {
        return this.readout;
    }

    public int renderStallCause() {
        return this.stallCause;
    }

    @Override
    protected boolean renderSyncDirty() {
        if (this.readout != this.syncedReadout || this.stallCause != this.syncedStall) {
            this.syncedReadout = this.readout;
            this.syncedStall = this.stallCause;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt(KEY_READOUT, this.readout);
        tag.putInt(KEY_STALL, this.stallCause);
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr(KEY_READOUT, 0);
        this.stallCause = input.getIntOr(KEY_STALL, 0);
    }

    // --- persistence --------------------------------------------------------------------------------

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
        output.putInt("ThresholdHeat", this.thresholdHeat);
        output.putInt("ThresholdFuel", this.thresholdFuel);
        output.putInt("ThresholdOutput", this.thresholdOutput);
        output.putInt("ThresholdStage", this.thresholdStage);
        output.putBoolean("AutoScram", this.autoScram);
        output.putInt("AutoScramAt", this.autoScramAt);
        output.putInt("RedstoneSignal", this.redstoneSignal);
        output.putInt("ComparatorSignal", this.comparatorSignal);
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
        this.thresholdHeat = ControlComputerLogic.clampThreshold(ControlComputerLogic.HEAT,
                input.getIntOr("ThresholdHeat", ControlComputerLogic.DEFAULT_THRESHOLD_HEAT));
        this.thresholdFuel = ControlComputerLogic.clampThreshold(ControlComputerLogic.FUEL,
                input.getIntOr("ThresholdFuel", ControlComputerLogic.DEFAULT_THRESHOLD_FUEL));
        this.thresholdOutput = ControlComputerLogic.clampThreshold(ControlComputerLogic.OUTPUT,
                input.getIntOr("ThresholdOutput", ControlComputerLogic.DEFAULT_THRESHOLD_OUTPUT));
        this.thresholdStage = ControlComputerLogic.clampThreshold(ControlComputerLogic.STAGE,
                input.getIntOr("ThresholdStage", ControlComputerLogic.DEFAULT_THRESHOLD_STAGE));
        this.autoScram = input.getBooleanOr("AutoScram", false);
        this.autoScramAt = ControlComputerLogic.clampAutoScram(
                input.getIntOr("AutoScramAt", ControlComputerLogic.DEFAULT_AUTO_SCRAM_AT));
        this.redstoneSignal = Math.max(0, Math.min(ControlComputerLogic.OFF,
                input.getIntOr("RedstoneSignal", ControlComputerLogic.DEFAULT_REDSTONE_SIGNAL)));
        this.comparatorSignal = Math.max(0, Math.min(ControlComputerLogic.SIGNALS - 1,
                input.getIntOr("ComparatorSignal", ControlComputerLogic.DEFAULT_COMPARATOR_SIGNAL)));
    }

    /** Takes no power and never fails: never load-shed. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.reactor_control_computer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReactorControlComputerMenu(containerId, playerInventory, this, this.data);
    }
}

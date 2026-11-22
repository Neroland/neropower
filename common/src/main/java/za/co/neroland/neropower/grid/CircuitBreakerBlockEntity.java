package za.co.neroland.neropower.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.platform.EnergyLookup;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.RelativeFace;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SideMode;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;
import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureTelegraph;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Circuit Breaker (0.4.0, decision 008 annex; screens/circuit_breaker.md). Inline: power enters at the
 * back, leaves at the front. While closed it passes up to {@code min(demand, 125 % of its limit)} a tick;
 * load (demand × 1000 / limit) climbs its own failure ladder ({@link BreakerLogic#thresholds}, dwell
 * {@code breakerMinDwellTicks}) with the usual telegraph — amber at 60 %, unstable at 80 %, and at 100 %
 * held for the dwell it <b>trips</b>: the circuit opens and nothing passes until it is reset. A trip never
 * damages anything. Reset: the screen button, a right-click on its front, or (option {@code RedstoneReset},
 * default on) a rising redstone edge. Comparator: 15 tripped, else load / 67.
 *
 * <p>It holds at most one tick of what it may pass ({@code Held}). NBT (additive): {@code Limit},
 * {@code Tripped}, {@code RedstoneReset}, {@code Held}, {@code FailureStage}/{@code FailureTicks}. No
 * player data.
 */
public class CircuitBreakerBlockEntity extends NeroPowerMachineBlockEntity {

    /**
     * Menu-synced ints after the seven shared ones: [0] readout, [1] stallCause, [2|3] limit, [4|5] demand,
     * [6|7] passed (15-bit pairs), [8] tripped, [9] redstone reset, [10] failure stage code, [11] stress.
     */
    public static final int EXTRA_DATA = 12;

    private int limit = BreakerLogic.DEFAULT_LIMIT;
    private boolean tripped;
    private boolean redstoneReset = true;
    private long held;
    private long pulledThisTick;
    private long demand;
    private long passed;
    private int stress;
    private boolean wasPowered;
    private int readout;
    private int stallCause;
    private int comparator;
    private int syncedKey = -1;

    private FailureController ladder;

    /** What Core's capability sees on every face (the side config lets the back fill and the front draw). */
    private final NeroEnergyStorage store = new NeroEnergyStorage() {
        @Override
        public long getAmount() {
            return CircuitBreakerBlockEntity.this.held;
        }

        @Override
        public long getCapacity() {
            return BreakerLogic.passCap(CircuitBreakerBlockEntity.this.limit);
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            if (CircuitBreakerBlockEntity.this.tripped || maxAmount <= 0L) {
                return 0L;
            }
            long room = Math.max(0L, getCapacity() - CircuitBreakerBlockEntity.this.held);
            long moved = Math.min(room, maxAmount);
            if (!simulate && moved > 0L) {
                CircuitBreakerBlockEntity.this.held += moved;
                setChanged();
            }
            return moved;
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            if (CircuitBreakerBlockEntity.this.tripped || maxAmount <= 0L) {
                return 0L;
            }
            long moved = Math.min(CircuitBreakerBlockEntity.this.held, maxAmount);
            if (!simulate && moved > 0L) {
                CircuitBreakerBlockEntity.this.held -= moved;
                CircuitBreakerBlockEntity.this.pulledThisTick += moved;
                setChanged();
            }
            return moved;
        }
    };

    public CircuitBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(GridContent.CIRCUIT_BREAKER_BE.get(), pos, state, 0);
        SideConfig config = SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.ALL_DISABLED)
                .build();
        config.setMode(Channel.ENERGY, RelativeFace.BACK, SideMode.INPUT);
        config.setMode(Channel.ENERGY, RelativeFace.FRONT, SideMode.OUTPUT);
        setupSideConfig(config);
    }

    @Override
    public NeroEnergyStorage getEnergy() {
        return this.store;
    }

    // --- failure ladder (the breaker's own thresholds; never a removal or blast) ----------------

    @Override
    public FailureController failure() {
        FailureController controller = this.ladder;
        if (controller == null) {
            controller = new FailureController(BreakerLogic.thresholds(GridConfig.breakerMinDwellTicks()));
            controller.setListener((from, to) -> {
                FailureTelegraph.announce(this, from, to);
                setChanged();
            });
            this.ladder = controller;
        }
        return controller;
    }

    @Override
    public FailureStage failureStage() {
        return this.ladder == null ? FailureStage.STABLE : this.ladder.stage();
    }

    // --- controls ---------------------------------------------------------------------------------

    public int limit() {
        return this.limit;
    }

    public void setLimit(long value) {
        this.limit = BreakerLogic.clampLimit(value);
        this.held = Math.min(this.held, BreakerLogic.passCap(this.limit));
        setChanged();
    }

    public boolean tripped() {
        return this.tripped;
    }

    public boolean redstoneReset() {
        return this.redstoneReset;
    }

    public void setRedstoneReset(boolean value) {
        this.redstoneReset = value;
        setChanged();
    }

    /** Close a tripped breaker (button, front right-click, redstone edge). Returns whether it was tripped. */
    public boolean reset() {
        if (!this.tripped) {
            return false;
        }
        this.tripped = false;
        setChanged();
        sound(za.co.neroland.neropower.registry.ModSounds.BREAKER_RESET.get());
        return true;
    }

    private void trip() {
        this.tripped = true;
        setChanged();
        sound(za.co.neroland.neropower.registry.ModSounds.BREAKER_TRIP.get());
    }

    private void sound(net.minecraft.sounds.SoundEvent event) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.playSound(null, this.worldPosition, event, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // --- ticking ----------------------------------------------------------------------------------

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        Direction front = state.hasProperty(NeroTechMachineBlock.FACING)
                ? state.getValue(NeroTechMachineBlock.FACING) : Direction.NORTH;
        long pulled = this.pulledThisTick;
        this.pulledThisTick = 0L;

        boolean powered = level.hasNeighborSignal(pos);
        if (BreakerLogic.redstoneReset(this.redstoneReset, this.tripped, this.wasPowered, powered)) {
            reset();
        }
        this.wasPowered = powered;

        long moved = 0L;
        long probed = 0L;
        if (!this.tripped) {
            BlockPos outPos = pos.relative(front);
            NeroEnergyStorage sink = level.hasChunkAt(outPos)
                    ? EnergyLookup.INSTANCE.find(level, outPos, front.getOpposite()) : null;
            if (sink != null) {
                probed = Math.max(0L, sink.insert(BreakerLogic.probe(this.limit), true));
                long give = Math.max(0L, BreakerLogic.passed(false, probed, this.held, this.limit) - pulled);
                if (give > 0L) {
                    moved = Math.max(0L, sink.insert(give, false));
                    this.held -= Math.min(this.held, moved);
                }
            }
        }
        this.demand = this.tripped ? 0L : probed + pulled;
        this.passed = moved + pulled;
        this.stress = this.tripped ? 0 : BreakerLogic.stress(this.demand, this.limit);

        FailureController controller = failure();
        FailureStage stage = controller.tick(this.stress, BreakerLogic.PERMILLE, true);
        if (stage == FailureStage.FAILURE && controller.justEntered()) {
            trip();
        }

        if (this.tripped) {
            setReadout(GridReadout.TRIP, GridReadout.CAUSE_NONE, MachineStatus.BLOCKED);
        } else if (stage.code() >= FailureStage.WARNING.code()) {
            setReadout(GridReadout.HOT, GridReadout.CAUSE_NONE, MachineStatus.THROTTLED);
        } else if (this.passed > 0L) {
            setReadout(GridReadout.WORK, GridReadout.CAUSE_NONE, MachineStatus.RUNNING);
        } else {
            setReadout(GridReadout.REST, GridReadout.CAUSE_NONE, MachineStatus.IDLE);
        }
        setActive(this.passed > 0L);

        if (state.hasProperty(CircuitBreakerBlock.TRIPPED) && state.getValue(CircuitBreakerBlock.TRIPPED) != this.tripped) {
            level.setBlock(pos, state.setValue(CircuitBreakerBlock.TRIPPED, this.tripped), Block.UPDATE_ALL);
        }
        int signal = BreakerLogic.comparator(this.tripped, this.stress);
        if (signal != this.comparator) {
            this.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private void setReadout(int code, int cause, MachineStatus status) {
        this.readout = GridReadout.clampReadout(code);
        this.stallCause = GridReadout.clampCause(cause);
        if (code != GridReadout.WORK) {
            reportStatus(status);
        }
    }

    public int comparatorSignal() {
        return BreakerLogic.comparator(this.tripped, this.stress);
    }

    public int readoutCode() {
        return this.readout;
    }

    public int stallCause() {
        return this.stallCause;
    }

    /** Contract bucket {@code loadStep} 0..4. */
    public int loadStep() {
        return BreakerLogic.loadStep(this.stress);
    }

    @Override
    public boolean shedable() {
        return false;
    }

    // --- render sync (readout, stallCause, loadStep; tripped rides the block state) ---------------

    @Override
    protected boolean renderSyncDirty() {
        int key = this.readout | (this.stallCause << 3) | (loadStep() << 7);
        if (key != this.syncedKey) {
            this.syncedKey = key;
            return true;
        }
        return false;
    }

    @Override
    protected void writeRenderExtras(CompoundTag tag) {
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
        tag.putInt("LoadStep", loadStep());
    }

    @Override
    protected void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("Readout", GridReadout.REST);
        this.stallCause = input.getIntOr("StallCause", 0);
        this.stress = input.getIntOr("LoadStep", 0) * 250;
    }

    // --- persistence --------------------------------------------------------------------------

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
        output.putInt("Limit", this.limit);
        output.putBoolean("Tripped", this.tripped);
        output.putBoolean("RedstoneReset", this.redstoneReset);
        output.putLong("Held", this.held);
        if (this.ladder != null) {
            output.putInt("FailureStage", this.ladder.saveStage());
            output.putInt("FailureTicks", this.ladder.saveTicksInStage());
        }
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
        this.limit = BreakerLogic.clampLimit(input.getIntOr("Limit", BreakerLogic.DEFAULT_LIMIT));
        this.tripped = input.getBooleanOr("Tripped", false);
        this.redstoneReset = input.getBooleanOr("RedstoneReset", true);
        this.held = Math.max(0L, Math.min(BreakerLogic.passCap(this.limit), input.getLongOr("Held", 0L)));
    }

    // --- menu sync ----------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> this.readout;
            case 1 -> this.stallCause;
            case 2 -> GaugeTail.hi2(this.limit);
            case 3 -> GaugeTail.lo2(this.limit);
            case 4 -> GaugeTail.hi2(this.demand);
            case 5 -> GaugeTail.lo2(this.demand);
            case 6 -> GaugeTail.hi2(this.passed);
            case 7 -> GaugeTail.lo2(this.passed);
            case 8 -> this.tripped ? 1 : 0;
            case 9 -> this.redstoneReset ? 1 : 0;
            case 10 -> failureStage().code();
            case 11 -> this.stress;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.circuit_breaker");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CircuitBreakerMenu(containerId, playerInventory, this, this.data);
    }
}

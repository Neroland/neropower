package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Momentum Wheel (0.4.0, decision 008): a kinetic store. It charges by spinning up (energy in), gives
 * energy back by slowing down (energy out), and loses {@code momentumWheelLossPermillePerSecond} of what
 * it holds to friction every second ({@link MomentumMath}). No fuel, no failure, never part of a bank pool
 * (so its loss is always visible). The renderer reads {@link #chargeBucket()} (0..20, synced only when it
 * changes); the menu reads the fill and the loss rate.
 *
 * <p>Side posture: {@link SidePreset#STORAGE}, auto-eject off; it pushes at its own rate through
 * {@link StorageEnergy} (slosh guard: never into another store). Saves its energy only. No player data.
 */
public class MomentumWheelBlockEntity extends StorageBlockEntity {

    /**
     * Menu-synced ints after the seven shared ones: [0] readout, [1] stallCause, [2|3] in last tick,
     * [4|5] out last tick (15-bit pairs), [6] loss permille per second.
     */
    public static final int EXTRA_DATA = 7;

    private final FlowMeter meter = new FlowMeter(energyBuffer());

    private final int lossPhase = Math.floorMod(System.identityHashCode(this), MomentumMath.LOSS_INTERVAL);
    private int chargeBucket;
    private int syncedChargeBucket = -1;

    public MomentumWheelBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.MOMENTUM_WHEEL.get(), pos, state, 0,
                StorageConfig.momentumWheelCapacity(), StorageConfig.momentumWheelIo());
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, false)
                .build());
    }

    /** The metered view: Core's capability and the push go through it. */
    @Override
    public za.co.neroland.nerolandcore.energy.NeroEnergyStorage getEnergy() {
        return this.meter;
    }

    /** Charge bucket 0..20 (the renderer spins the rim by it; fill permille / 50). */
    public int chargeBucket() {
        return this.chargeBucket;
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        this.meter.roll();
        long stored = energyBuffer().getAmount();
        if ((level.getGameTime() + this.lossPhase) % MomentumMath.LOSS_INTERVAL == 0) {
            long loss = MomentumMath.lossPerSecond(stored, StorageConfig.momentumWheelLossPermillePerSecond());
            if (loss > 0) {
                energyBuffer().consume((int) Math.min(Integer.MAX_VALUE, loss));
                stored = energyBuffer().getAmount();
            }
        }
        long capacity = energyBuffer().getCapacity();
        boolean spinning = stored > 0;
        setActive(spinning);
        if (capacity > 0 && stored >= capacity) {
            setReadout(READOUT_STALL, STALL_BUFFER_FULL, MachineStatus.BLOCKED);
        } else if (this.meter.lastIn() > 0 || this.meter.lastOut() > 0) {
            setReadout(READOUT_WORK, STALL_NONE, MachineStatus.RUNNING);
        } else {
            setReadout(READOUT_REST, STALL_NONE, spinning ? MachineStatus.IDLE : MachineStatus.NO_ENERGY);
        }
        this.chargeBucket = MomentumMath.chargeBucket(stored, capacity);
        StorageEnergy.pushToNeighbours(level, pos, this.meter, StorageConfig.momentumWheelIo(), sideConfig(),
                null, -1);
    }

    @Override
    protected boolean bucketsDirty() {
        boolean dirty = this.chargeBucket != this.syncedChargeBucket;
        this.syncedChargeBucket = this.chargeBucket;
        return dirty;
    }

    @Override
    protected void writeBuckets(CompoundTag tag) {
        tag.putInt("ChargeBucket", this.chargeBucket);
    }

    @Override
    protected void readBuckets(ValueInput input) {
        this.chargeBucket = input.getIntOr("ChargeBucket", 0);
    }

    // --- menu sync ----------------------------------------------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> readoutCode();
            case 1 -> stallCause();
            case 2 -> za.co.neroland.neropower.menu.GaugeTail.hi2(this.meter.lastIn());
            case 3 -> za.co.neroland.neropower.menu.GaugeTail.lo2(this.meter.lastIn());
            case 4 -> za.co.neroland.neropower.menu.GaugeTail.hi2(this.meter.lastOut());
            case 5 -> za.co.neroland.neropower.menu.GaugeTail.lo2(this.meter.lastOut());
            case 6 -> StorageConfig.momentumWheelLossPermillePerSecond();
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.momentum_wheel");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MomentumWheelMenu(containerId, playerInventory, this, this.data);
    }
}

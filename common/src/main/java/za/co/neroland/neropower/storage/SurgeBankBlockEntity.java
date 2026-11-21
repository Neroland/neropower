package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.menu.GaugeTail;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Surge Bank (0.4.0, decision 008): a small, very fast store. Standing alone it is a buffer that pushes
 * at its own high rate (through {@link StorageEnergy}, so the slosh guard applies); touching a Battery
 * Bank's cells or controller it joins that bank as the <b>fast tier</b> ({@link PoolMember#fastTier()}):
 * filled first and drained first, with its I/O added on top of the cells' pooled I/O under
 * {@code bankFastTierIoCap}. Every unit stays in this block's own buffer, so joining or leaving a bank
 * moves nothing. No failure, no fuel. One {@link ChargeSlot} tops up a power cell.
 *
 * <p>Side posture: {@link SidePreset#STORAGE}, auto-eject off (the push loop here is the eject). Saves
 * its own energy only (Core's buffer) and the charge slot. No player data.
 */
public class SurgeBankBlockEntity extends StorageBlockEntity implements PoolMember {

    /** Menu-synced ints after the seven shared ones: readout, stallCause, in (pair), out (pair), pooled, bank members. */
    public static final int EXTRA_DATA = 8;
    /** How long (ticks) a bank's last rescan keeps this bank reading as pooled (controllers rescan every 20). */
    private static final int POOLED_GRACE = 45;

    private final FlowMeter meter = new FlowMeter(energyBuffer());
    private long pooledAt = Long.MIN_VALUE;
    private int bankMembers;

    public SurgeBankBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.SURGE_BANK.get(), pos, state, 1,
                StorageConfig.surgeBankCapacity(), StorageConfig.surgeBankIo());
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, false)
                .build());
    }

    /** The metered view: Core's capability, the bank pool and the push all go through it. */
    @Override
    public NeroEnergyStorage getEnergy() {
        return this.meter;
    }

    @Override
    public NeroEnergyStorage poolBuffer() {
        return this.meter;
    }

    /** Called by a Battery Bank Controller's rescan that found this bank among {@code members} members. */
    public void notePooled(long gameTime, int members) {
        this.pooledAt = gameTime;
        this.bankMembers = members;
    }

    /** Whether a bank pooled this one within the last rescan or two. */
    public boolean pooled() {
        return this.level != null && this.level.getGameTime() - this.pooledAt <= POOLED_GRACE;
    }

    @Override
    public long poolIo() {
        return StorageConfig.surgeBankIo();
    }

    @Override
    public boolean fastTier() {
        return true;
    }

    @Override
    public boolean canPlaceMachineItem(int slot, ItemStack stack) {
        return slot == ChargeSlot.SLOT && ChargeSlot.accepts(stack);
    }

    @Override
    public boolean canTakeMachineItem(int slot) {
        return slot == ChargeSlot.SLOT && ChargeSlot.canTake(getItem(slot));
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        this.meter.roll();
        long amount = getEnergy().getAmount();
        long capacity = getEnergy().getCapacity();
        boolean charged = amount > 0;
        setActive(charged);
        if (capacity > 0 && amount >= capacity) {
            setReadout(READOUT_STALL, STALL_BUFFER_FULL, MachineStatus.BLOCKED);
        } else if (!charged) {
            setReadout(READOUT_STALL, STALL_NO_POWER, MachineStatus.NO_ENERGY);
        } else if (this.meter.lastIn() > 0 || this.meter.lastOut() > 0) {
            setReadout(READOUT_WORK, STALL_NONE, MachineStatus.RUNNING);
        } else {
            setReadout(READOUT_REST, STALL_NONE, MachineStatus.IDLE);
        }
        int cans = PoolMath.bucket(amount, capacity, SurgeBankBlock.CAN_LEVELS);
        if (state.hasProperty(SurgeBankBlock.CANS) && state.getValue(SurgeBankBlock.CANS) != cans) {
            level.setBlock(pos, state.setValue(SurgeBankBlock.CANS, cans), Block.UPDATE_CLIENTS);
        }
        if (ChargeSlot.charge(getItem(ChargeSlot.SLOT), this.meter, poolIo()) > 0) {
            setChanged();
        }
        StorageEnergy.pushToNeighbours(level, pos, this.meter, poolIo(), sideConfig(), null, -1);
    }

    // --- menu sync (all < 2^15; NE amounts ride 15-bit pairs) ----------------------------------------------

    @Override
    protected int extraDataCount() {
        return EXTRA_DATA;
    }

    @Override
    protected int extraData(int index) {
        return switch (index) {
            case 0 -> readoutCode();
            case 1 -> stallCause();
            case 2 -> GaugeTail.hi2(this.meter.lastIn());
            case 3 -> GaugeTail.lo2(this.meter.lastIn());
            case 4 -> GaugeTail.hi2(this.meter.lastOut());
            case 5 -> GaugeTail.lo2(this.meter.lastOut());
            case 6 -> pooled() ? 1 : 0;
            case 7 -> pooled() ? Math.min(Short.MAX_VALUE, this.bankMembers) : 0;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.surge_bank");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SurgeBankMenu(containerId, playerInventory, this, this.data);
    }
}

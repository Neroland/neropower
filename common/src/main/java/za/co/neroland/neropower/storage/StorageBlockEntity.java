package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Shared base of the 0.4.0 storage blocks (Surge Bank, Momentum Wheel, Storage Ring Controller): the
 * contract's bucketed render surface. Every one syncs {@code readout} 0..5 (rest / work / stall / hot /
 * trip / fail) and {@code stallCause} 0..15 (the screen kit's code table, {@code STALL_*}), plus its own
 * buckets, and
 * pushes an update packet only when one of them changes. Renderers and models read these, never a raw
 * value. Machine state only: no UUID, name, coordinate or free text rides the update tag from here.
 */
public abstract class StorageBlockEntity extends NeroPowerMachineBlockEntity {

    public static final int READOUT_REST = 0;
    public static final int READOUT_WORK = 1;
    public static final int READOUT_STALL = 2;
    public static final int READOUT_HOT = 3;
    public static final int READOUT_TRIP = 4;
    public static final int READOUT_FAIL = 5;

    // stallCause codes (screen gauge kit table; decision 012 accepts it). Only the ones storage uses.
    public static final int STALL_NONE = 0;
    public static final int STALL_NOT_FORMED = 1;
    public static final int STALL_BUFFER_FULL = 8;
    public static final int STALL_NO_POWER = 9;
    public static final int STALL_NO_COOLANT = 13;
    public static final int STALL_LOCKED_OUT = 15;

    private int readout;
    private int stallCause;
    private int syncedReadout = -1;
    private int syncedStall = -1;

    protected StorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int machineSlots,
            int energyCapacity, int maxTransfer) {
        super(type, pos, state, machineSlots, energyCapacity, maxTransfer);
    }

    /**
     * Set this tick's readout (0..5) and stall cause (0..15, {@code STALL_*}); also reports the matching
     * status to NeroTech's analytics.
     */
    protected void setReadout(int code, int cause, MachineStatus status) {
        this.readout = Math.max(READOUT_REST, Math.min(READOUT_FAIL, code));
        this.stallCause = Math.max(0, Math.min(15, cause));
        if (code != READOUT_WORK) {
            reportStatus(status);
        }
    }

    /** The menu-sync value for {@link #readoutCode()} and {@link #stallCause()}. */
    protected int syncedReadoutData(boolean stall) {
        return stall ? this.stallCause : this.readout;
    }

    /** The synced readout, 0..5. */
    public int readoutCode() {
        return this.readout;
    }

    /** The synced stall cause, 0..15. */
    public int stallCause() {
        return this.stallCause;
    }

    /** Subclass buckets: compare-and-record, true when a client-visible bucket changed. */
    protected boolean bucketsDirty() {
        return false;
    }

    /** Subclass buckets onto the update tag. */
    protected void writeBuckets(CompoundTag tag) {
    }

    /** Subclass buckets from the update tag (client side only). */
    protected void readBuckets(ValueInput input) {
    }

    @Override
    protected final boolean renderSyncDirty() {
        boolean dirty = this.readout != this.syncedReadout || this.stallCause != this.syncedStall;
        this.syncedReadout = this.readout;
        this.syncedStall = this.stallCause;
        dirty |= bucketsDirty();
        return dirty;
    }

    @Override
    protected final void writeRenderExtras(CompoundTag tag) {
        tag.putInt("Readout", this.readout);
        tag.putInt("StallCause", this.stallCause);
        writeBuckets(tag);
    }

    @Override
    protected final void readRenderExtras(ValueInput input) {
        this.readout = input.getIntOr("Readout", READOUT_REST);
        this.stallCause = input.getIntOr("StallCause", 0);
        readBuckets(input);
    }

    /** Shedding a store would achieve nothing: it has no work rate to throttle. */
    @Override
    public boolean shedable() {
        return false;
    }
}

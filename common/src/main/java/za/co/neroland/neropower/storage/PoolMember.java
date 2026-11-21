package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * One block a Battery Bank pools: a Battery Cell (the slow, deep tier) or a Surge Bank (the fast
 * tier). The pool reads and moves energy only through {@link #poolBuffer()}, so joining or leaving a
 * bank never moves, creates or destroys a unit: every unit stays in the member that holds it.
 *
 * <p>Block entities satisfy {@link #getBlockPos()} and {@link #isRemoved()} by inheritance.
 */
public interface PoolMember {

    /** The member's own buffer: the pool inserts into and extracts from this, never anything else. */
    NeroEnergyStorage poolBuffer();

    /** The member's per-transfer insert / extract limit (NE/t). */
    long poolIo();

    /**
     * Whether the member is fast tier (Surge Bank): filled first and drained first, and its I/O adds to
     * the pool on top of the cells' {@code min x count} rule, under its own cap.
     */
    boolean fastTier();

    /** The member's position (block entities inherit it). */
    BlockPos getBlockPos();

    /** Whether the member has been removed since the last scan (block entities inherit it). */
    boolean isRemoved();
}

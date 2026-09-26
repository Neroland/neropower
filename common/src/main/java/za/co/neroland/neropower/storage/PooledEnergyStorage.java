package za.co.neroland.neropower.storage;

import java.util.List;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * One {@link NeroEnergyStorage} over every cell in a Battery Bank: amount and capacity are sums,
 * and each insert / extract is spread round-robin across the members ({@link PoolMath#distribute})
 * with every cell's own I/O limit honoured and the whole transfer capped at the pool's effective
 * I/O ({@code min(cell I/O) × count}, capped by {@code bankMaxIoCap}).
 *
 * <p>The controller hands this out as its energy surface (its {@code getEnergy()}), so Core's
 * capability lookup and side-config gating on the controller reach the pool. The object is stable
 * for the controller's lifetime — Forge caches the capability view per face — and only its member
 * list changes, swapped whole by each rescan. With no members it reads as an empty, zero-capacity
 * store that accepts nothing.
 */
public final class PooledEnergyStorage implements NeroEnergyStorage {

    private List<BatteryCellBlockEntity> members = List.of();
    private long minCellIo;
    /** Round-robin start index, advanced after every real transfer. */
    private int cursor;

    /** Replace the member set (a rescan). Discovery order is kept so the cursor stays meaningful. */
    public void setMembers(List<BatteryCellBlockEntity> cells) {
        this.members = List.copyOf(cells);
        long min = Long.MAX_VALUE;
        for (BatteryCellBlockEntity cell : this.members) {
            min = Math.min(min, cell.tier().maxIo());
        }
        this.minCellIo = this.members.isEmpty() ? 0L : min;
        if (this.cursor >= this.members.size()) {
            this.cursor = 0;
        }
    }

    public List<BatteryCellBlockEntity> members() {
        return this.members;
    }

    public int memberCount() {
        return this.members.size();
    }

    /** Whether at least one cell is pooled. */
    public boolean formed() {
        return !this.members.isEmpty();
    }

    /** The pool's per-tick insert / extract limit (NE/t). */
    public long effectiveIo() {
        return PoolMath.effectiveIo(this.minCellIo, this.members.size(), StorageConfig.bankMaxIoCap());
    }

    @Override
    public long getAmount() {
        long total = 0L;
        for (BatteryCellBlockEntity cell : this.members) {
            total += cell.cellBuffer().getAmount();
        }
        return total;
    }

    @Override
    public long getCapacity() {
        long total = 0L;
        for (BatteryCellBlockEntity cell : this.members) {
            total += cell.cellBuffer().getCapacity();
        }
        return total;
    }

    @Override
    public long insert(long maxAmount, boolean simulate) {
        return transfer(maxAmount, simulate, true);
    }

    @Override
    public long extract(long maxAmount, boolean simulate) {
        return transfer(maxAmount, simulate, false);
    }

    /**
     * Distribute one transfer: each member's limit is what it can still take (insert) or give
     * (extract) this tick — {@link EnergyBuffer} enforces the cell's own I/O bound in the simulate
     * probe — then {@link PoolMath#distribute} shares the capped amount round-robin from the cursor.
     */
    private long transfer(long maxAmount, boolean simulate, boolean insert) {
        int count = this.members.size();
        if (count == 0 || maxAmount <= 0) {
            return 0L;
        }
        long budget = Math.min(maxAmount, effectiveIo());
        if (budget <= 0) {
            return 0L;
        }
        long[] limits = new long[count];
        for (int i = 0; i < count; i++) {
            EnergyBuffer buffer = this.members.get(i).cellBuffer();
            limits[i] = insert ? buffer.insert(budget, true) : buffer.extract(budget, true);
        }
        long[] given = PoolMath.distribute(limits, budget, this.cursor);
        long moved = PoolMath.sum(given);
        if (moved > 0 && !simulate) {
            for (int i = 0; i < count; i++) {
                if (given[i] > 0) {
                    EnergyBuffer buffer = this.members.get(i).cellBuffer();
                    if (insert) {
                        buffer.insert(given[i], false);
                    } else {
                        buffer.extract(given[i], false);
                    }
                }
            }
            this.cursor = (this.cursor + 1) % count;
        }
        return moved;
    }
}

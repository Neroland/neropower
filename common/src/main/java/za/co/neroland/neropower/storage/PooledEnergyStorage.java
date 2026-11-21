package za.co.neroland.neropower.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * One {@link NeroEnergyStorage} over every member of a Battery Bank: amount and capacity are sums,
 * and each insert / extract is spread round-robin across the members ({@link PoolMath#distribute})
 * with every member's own I/O limit honoured.
 *
 * <p><b>Two tiers (0.4.0).</b> Battery Cells are the deep tier: their pooled I/O is
 * {@code min(cell I/O) x cells}, capped by {@code bankMaxIoCap} (unchanged from 0.3). Surge Banks are
 * the fast tier: their I/O is the sum of their own I/O, capped by {@code bankFastTierIoCap}, and is
 * <i>added</i> on top. A transfer is offered to the fast tier first (fill and drain alike, so the
 * surge banks absorb spin-up and beam bursts), then whatever remains goes to the cells. A bank of cells
 * only behaves exactly as before.
 *
 * <p>The pool holds no energy of its own: joining or leaving only changes which members' buffers are
 * summed, so a rescan can never create or destroy a unit.
 *
 * <p>The controller hands this out as its energy surface (its {@code getEnergy()}), so Core's
 * capability lookup and side-config gating on the controller reach the pool. The object is stable for
 * the controller's lifetime (Forge caches the capability view per face) and only its member list
 * changes, swapped whole by each rescan. With no members it reads as an empty, zero-capacity store.
 */
public final class PooledEnergyStorage implements NeroEnergyStorage {

    private final LongSupplier slowCap;
    private final LongSupplier fastCap;
    private List<PoolMember> members = List.of();
    private List<PoolMember> slow = List.of();
    private List<PoolMember> fast = List.of();
    private long minCellIo;
    private long fastIoSum;
    /** Round-robin start index per tier, advanced after every real transfer. */
    private int slowCursor;
    private int fastCursor;

    /** The in-game pool: caps from {@code StorageConfig}. */
    public PooledEnergyStorage() {
        this(StorageConfig::bankMaxIoCap, StorageConfig::bankFastTierIoCap);
    }

    /** A pool with explicit tier caps (tests; no config class-load). */
    public PooledEnergyStorage(LongSupplier slowCap, LongSupplier fastCap) {
        this.slowCap = slowCap;
        this.fastCap = fastCap;
    }

    /** Replace the member set (a rescan). Discovery order is kept so the cursors stay meaningful. */
    public void setMembers(List<? extends PoolMember> list) {
        this.members = List.copyOf(list);
        List<PoolMember> slowList = new ArrayList<>();
        List<PoolMember> fastList = new ArrayList<>();
        long min = Long.MAX_VALUE;
        long fastSum = 0L;
        for (PoolMember member : this.members) {
            if (member.fastTier()) {
                fastList.add(member);
                fastSum += Math.max(0L, member.poolIo());
            } else {
                slowList.add(member);
                min = Math.min(min, member.poolIo());
            }
        }
        this.slow = List.copyOf(slowList);
        this.fast = List.copyOf(fastList);
        this.minCellIo = this.slow.isEmpty() ? 0L : min;
        this.fastIoSum = fastSum;
        if (this.slowCursor >= this.slow.size()) {
            this.slowCursor = 0;
        }
        if (this.fastCursor >= this.fast.size()) {
            this.fastCursor = 0;
        }
    }

    public List<PoolMember> members() {
        return this.members;
    }

    public int memberCount() {
        return this.members.size();
    }

    /** Surge Banks in the pool. */
    public int fastCount() {
        return this.fast.size();
    }

    /** Whether at least one member is pooled. */
    public boolean formed() {
        return !this.members.isEmpty();
    }

    /** The deep tier's per-tick limit: {@code min(cell I/O) x cells}, capped (NE/t). */
    public long slowIo() {
        return PoolMath.effectiveIo(this.minCellIo, this.slow.size(), this.slowCap.getAsLong());
    }

    /** The fast tier's per-tick limit: the Surge Banks' summed I/O, capped (NE/t). */
    public long fastIo() {
        return PoolMath.fastTierIo(this.fastIoSum, this.fast.size(), this.fastCap.getAsLong());
    }

    /** The pool's per-tick insert / extract limit (NE/t): deep tier plus fast tier. */
    public long effectiveIo() {
        return this.slowIo() + this.fastIo();
    }

    @Override
    public long getAmount() {
        long total = 0L;
        for (PoolMember member : this.members) {
            total += member.poolBuffer().getAmount();
        }
        return total;
    }

    @Override
    public long getCapacity() {
        long total = 0L;
        for (PoolMember member : this.members) {
            total += member.poolBuffer().getCapacity();
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

    /** Fast tier first, then the cells, each under its own tier limit. */
    private long transfer(long maxAmount, boolean simulate, boolean insert) {
        if (this.members.isEmpty() || maxAmount <= 0) {
            return 0L;
        }
        long fastMoved = tier(this.fast, Math.min(maxAmount, fastIo()), simulate, insert, true);
        long rest = maxAmount - fastMoved;
        long slowMoved = rest <= 0 ? 0L : tier(this.slow, Math.min(rest, slowIo()), simulate, insert, false);
        return fastMoved + slowMoved;
    }

    /**
     * Distribute one tier's share: each member's limit is what it can still take (insert) or give
     * (extract) this call, as its own buffer's simulate probe reports, then {@link PoolMath#distribute}
     * shares the budget round-robin from the tier's cursor.
     */
    private long tier(List<PoolMember> list, long budget, boolean simulate, boolean insert, boolean fastTier) {
        int count = list.size();
        if (count == 0 || budget <= 0) {
            return 0L;
        }
        long[] limits = new long[count];
        for (int i = 0; i < count; i++) {
            NeroEnergyStorage buffer = list.get(i).poolBuffer();
            long probe = insert ? buffer.insert(budget, true) : buffer.extract(budget, true);
            limits[i] = Math.max(0L, Math.min(probe, list.get(i).poolIo()));
        }
        int cursor = fastTier ? this.fastCursor : this.slowCursor;
        long[] given = PoolMath.distribute(limits, budget, cursor);
        long moved = 0L;
        if (simulate) {
            return PoolMath.sum(given);
        }
        for (int i = 0; i < count; i++) {
            if (given[i] > 0) {
                NeroEnergyStorage buffer = list.get(i).poolBuffer();
                moved += insert ? buffer.insert(given[i], false) : buffer.extract(given[i], false);
            }
        }
        if (moved > 0) {
            if (fastTier) {
                this.fastCursor = (this.fastCursor + 1) % count;
            } else {
                this.slowCursor = (this.slowCursor + 1) % count;
            }
        }
        return moved;
    }
}

package za.co.neroland.neropower.storage;

/**
 * The pure arithmetic behind a Battery Bank — Minecraft-free so it is unit-testable: the pool's
 * effective I/O, fair round-robin distribution of one transfer across member cells, the
 * PRIORITY_SOURCE fill test, and the 0..4 charge-level bucket the cell model overlay shows.
 */
public final class PoolMath {

    /** Charge levels the {@code charge} block-state property can take: 0 (empty) .. {@value} (full). */
    public static final int CHARGE_LEVELS = 4;

    private PoolMath() {
    }

    /**
     * The pool's per-tick insert / extract limit: {@code min(cell I/O) × member count}, capped by
     * {@code cap}. Zero for an empty pool or a non-positive cap.
     */
    public static long effectiveIo(long minCellIo, int memberCount, long cap) {
        if (memberCount <= 0 || minCellIo <= 0 || cap <= 0) {
            return 0L;
        }
        long total = minCellIo * memberCount;
        return Math.min(total, cap);
    }

    /**
     * Share {@code amount} across members, round-robin, honouring each member's {@code limits[i]}
     * (what it can still take / give this tick). Every pass hands each still-open member an equal
     * share of what is left (at least 1), starting at {@code cursor} so the remainder of an uneven
     * split lands on a different member each tick. Terminates when the amount is spent or every
     * member is saturated.
     *
     * @return per-member allocation, same length as {@code limits}; its sum is {@code ≤ amount}
     */
    public static long[] distribute(long[] limits, long amount, int cursor) {
        long[] given = new long[limits.length];
        if (limits.length == 0 || amount <= 0) {
            return given;
        }
        int start = Math.floorMod(cursor, limits.length);
        long remaining = amount;
        while (remaining > 0) {
            int open = 0;
            for (int i = 0; i < limits.length; i++) {
                if (given[i] < limits[i]) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            long share = Math.max(1L, remaining / open);
            for (int step = 0; step < limits.length && remaining > 0; step++) {
                int i = (start + step) % limits.length;
                long room = limits[i] - given[i];
                if (room <= 0) {
                    continue;
                }
                long take = Math.min(share, Math.min(room, remaining));
                given[i] += take;
                remaining -= take;
            }
        }
        return given;
    }

    /** Sum of an allocation (what {@link #distribute} actually placed). */
    public static long sum(long[] values) {
        long total = 0L;
        for (long v : values) {
            total += v;
        }
        return total;
    }

    /**
     * PRIORITY_SOURCE test: whether a neighbour holding {@code amount} of {@code capacity} is below
     * {@code thresholdPermille} and so may be fed. A neighbour with no capacity is never fed.
     */
    public static boolean belowThreshold(long amount, long capacity, int thresholdPermille) {
        if (capacity <= 0) {
            return false;
        }
        return amount * 1000L / capacity < thresholdPermille;
    }

    /**
     * The 0..{@value #CHARGE_LEVELS} charge bucket for the cell model overlay: 0 only when empty,
     * {@value #CHARGE_LEVELS} only when full, and 1..3 in between (any charge at all shows at least
     * one bar).
     */
    public static int chargeLevel(long amount, long capacity) {
        if (amount <= 0 || capacity <= 0) {
            return 0;
        }
        if (amount >= capacity) {
            return CHARGE_LEVELS;
        }
        int level = (int) (amount * CHARGE_LEVELS / capacity);
        return Math.max(1, Math.min(CHARGE_LEVELS - 1, level));
    }
}

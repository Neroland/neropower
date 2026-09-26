package za.co.neroland.neropower.environmental;

/**
 * Pure integer maths for the Radioisotope Generator's decay curve: the output of a pellet inserted
 * {@code elapsedTicks} ago is {@code 2^(-elapsed / halfLife)} of the fresh rate, expressed in permille.
 * No floating point at runtime (the 2^(-k/64) table is filled once at class-load), so the same
 * inputs give the same output on every JVM and every loader — and the whole curve is unit-testable
 * without Minecraft.
 */
public final class RtgMath {

    /** Fixed-point one (Q16). */
    private static final int ONE = 1 << 16;

    /** Table resolution: one half-life is split into this many steps of {@code 2^(-k/STEPS)}. */
    private static final int STEPS = 64;

    /**
     * {@code round(ONE * 2^(-k / STEPS))} for k = 0..STEPS (inclusive, so interpolation of the last
     * step has a partner). Strictly decreasing; {@code TABLE[0] == ONE}, {@code TABLE[STEPS] == ONE / 2}.
     */
    private static final int[] TABLE = new int[STEPS + 1];

    static {
        for (int k = 0; k <= STEPS; k++) {
            TABLE[k] = (int) Math.round(ONE * Math.pow(2.0D, -k / (double) STEPS));
        }
    }

    private RtgMath() {
    }

    /**
     * The fraction of the fresh output a pellet still yields after {@code elapsedTicks}, in permille
     * (0..1000). Exactly 1000 at insertion, 500 at one half-life, 250 at two, and so on; monotonically
     * non-increasing in {@code elapsedTicks}; 0 once more than 30 half-lives have passed.
     *
     * @param elapsedTicks ticks since the pellet was inserted (negative reads as 0)
     * @param halfLifeTicks the half-life in ticks (values below 1 read as 1)
     */
    public static int outputPermille(long elapsedTicks, long halfLifeTicks) {
        long elapsed = Math.max(0L, elapsedTicks);
        long halfLife = Math.max(1L, halfLifeTicks);
        long whole = elapsed / halfLife;
        if (whole >= 31) {
            return 0;
        }
        long frac = elapsed % halfLife;
        // Position within the half-life at STEPS*64 resolution, then a linear interpolation between
        // the two neighbouring table entries — piecewise linear, so monotonic like the table itself.
        long pos = frac * (STEPS * 64L) / halfLife;   // 0 .. STEPS*64 - 1
        int k = (int) (pos >> 6);                       // table index 0 .. STEPS-1
        int rem = (int) (pos & 63L);                    // 0 .. 63 within the step
        long value = TABLE[k] - ((long) (TABLE[k] - TABLE[k + 1]) * rem >> 6);
        value >>= whole;
        return (int) (value * 1000L >> 16);
    }

    /** Whether a pellet at {@code permille} output is spent: below the configured cutoff. */
    public static boolean spent(int outputPermille, int cutoffPermille) {
        return outputPermille < cutoffPermille;
    }

    /**
     * The tick offset (from insertion) at which a pellet becomes spent under {@code cutoffPermille}:
     * the smallest {@code t} with {@code outputPermille(t, halfLife) < cutoff}. A binary search over
     * the monotonic curve — call it once per config change, not per tick.
     */
    public static long ticksToCutoff(long halfLifeTicks, int cutoffPermille) {
        long halfLife = Math.max(1L, halfLifeTicks);
        long lo = 0L;
        long hi = halfLife * 31L;
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2L;
            if (spent(outputPermille(mid, halfLife), cutoffPermille)) {
                hi = mid;
            } else {
                lo = mid + 1L;
            }
        }
        return lo;
    }

    /** Whole in-game days in {@code ticks} ({@link EnvironmentalConfig#TICKS_PER_DAY}). */
    public static int days(long ticks) {
        return (int) Math.max(0L, ticks / EnvironmentalConfig.TICKS_PER_DAY);
    }
}

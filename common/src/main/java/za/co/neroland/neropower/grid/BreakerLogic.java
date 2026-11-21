package za.co.neroland.neropower.grid;

import za.co.neroland.neropower.failure.FailureThresholds;

/**
 * Circuit Breaker rules (decision 008 annex), Minecraft-free: what passes, how stress is measured, when it
 * trips, how a reset is recognised and what a comparator reads. The breaker never stores more than one
 * tick of what it may pass and never blasts: a trip only opens the circuit.
 */
public final class BreakerLogic {

    public static final int PERMILLE = 1_000;
    public static final int MIN_LIMIT = 1;
    public static final int MAX_LIMIT = 10_000_000;
    public static final int DEFAULT_LIMIT = 10_000;

    /** Stress is measured up to twice the limit (the probe offered downstream). */
    public static final int MAX_STRESS = 2 * PERMILLE;

    private BreakerLogic() {
    }

    public static int clampLimit(long limit) {
        return (int) Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
    }

    /** The most it passes in one tick while closed: 125 % of the limit. */
    public static long passCap(int limit) {
        return (long) clampLimit(limit) * 5L / 4L;
    }

    /** How much it offers downstream to learn the demand: twice the limit. */
    public static long probe(int limit) {
        return (long) clampLimit(limit) * 2L;
    }

    /** What passes this tick: min(demand, available, 125 % of the limit); nothing while tripped. */
    public static long passed(boolean tripped, long demand, long available, int limit) {
        if (tripped || demand <= 0L || available <= 0L) {
            return 0L;
        }
        return Math.min(Math.min(demand, available), passCap(limit));
    }

    /** Stress = demand × 1000 / limit, capped at {@link #MAX_STRESS}. */
    public static int stress(long demand, int limit) {
        if (demand <= 0L) {
            return 0;
        }
        long s = demand * PERMILLE / clampLimit(limit);
        return (int) Math.min(MAX_STRESS, s);
    }

    /** The breaker's ladder: WARNING 60 %, UNSTABLE 80 %, FAILURE (trip) at 100 %; no output penalty. */
    public static FailureThresholds thresholds(int minDwellTicks) {
        return new FailureThresholds(600, 800, 1000, 50, Math.max(0, minDwellTicks), PERMILLE);
    }

    /** Comparator: 15 while tripped, else stress / 67 (0..14). */
    public static int comparator(boolean tripped, int stress) {
        if (tripped) {
            return 15;
        }
        return Math.max(0, Math.min(14, stress / 67));
    }

    /** Contract bucket {@code loadStep} 0..4 (a quarter of the limit a step). */
    public static int loadStep(int stress) {
        return Math.max(0, Math.min(4, stress / 250));
    }

    /** A reset by redstone: a rising edge, only when the option is on and the breaker is tripped. */
    public static boolean redstoneReset(boolean option, boolean tripped, boolean wasPowered, boolean powered) {
        return option && tripped && powered && !wasPowered;
    }

    /** The slider notches: the 1-2-5 series from 1 to 10 000 000 (22 stops). */
    public static int[] notches() {
        int[] out = new int[22];
        int i = 0;
        for (int decade = 1; decade <= 1_000_000; decade *= 10) {
            out[i++] = decade;
            out[i++] = decade * 2;
            out[i++] = decade * 5;
        }
        out[i] = MAX_LIMIT;
        return out;
    }
}

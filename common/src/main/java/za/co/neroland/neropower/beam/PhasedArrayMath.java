package za.co.neroland.neropower.beam;

import java.util.function.IntPredicate;

/**
 * Phased Array Emitter serving order (decision 008 annex), Minecraft-free: one target per pass, in list
 * order, round-robin. A target that cannot take the beam this pass (blocked, out of range, full, asleep)
 * is skipped and the next one is tried, at most once each, so one bad target never starves the rest.
 */
public final class PhasedArrayMath {

    private PhasedArrayMath() {
    }

    /**
     * Try targets from {@code cursor} onward (wrapping) until {@code serve} accepts one.
     *
     * @param cursor the index to try first (taken modulo {@code count})
     * @param count  number of targets
     * @param serve  attempts to serve index i this pass; true when energy was delivered
     * @return the served index, or -1 when none could be served
     */
    public static int serveNext(int cursor, int count, IntPredicate serve) {
        if (count <= 0) {
            return -1;
        }
        int start = Math.floorMod(cursor, count);
        for (int step = 0; step < count; step++) {
            int index = (start + step) % count;
            if (serve.test(index)) {
                return index;
            }
        }
        return -1;
    }

    /** Where the next pass starts after serving {@code served} (or keeps {@code cursor} when none was). */
    public static int nextCursor(int cursor, int served, int count) {
        if (count <= 0) {
            return 0;
        }
        return served < 0 ? Math.floorMod(cursor, count) : (served + 1) % count;
    }

    /** A direction component in 1/16 steps (-16..16) for the BER, from a delta and the length. */
    public static int directionStep(double delta, double length) {
        if (length <= 0.0) {
            return 0;
        }
        return (int) Math.max(-16L, Math.min(16L, Math.round(delta / length * 16.0)));
    }
}

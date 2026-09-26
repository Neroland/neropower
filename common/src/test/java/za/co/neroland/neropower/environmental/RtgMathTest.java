package za.co.neroland.neropower.environmental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link RtgMath}: exact halving at each half-life (within ±1‰), a monotonically non-increasing
 * curve, the cutoff test and the ticks-to-cutoff search. Pure JVM.
 */
class RtgMathTest {

    /** Mirrors {@code EnvironmentalConfig.TICKS_PER_DAY} without class-loading the config schema. */
    private static final long TICKS_PER_DAY = 24_000L;
    private static final long HALF_LIFE = 20L * TICKS_PER_DAY;

    @Test
    void freshPelletIsFullOutput() {
        assertEquals(1000, RtgMath.outputPermille(0L, HALF_LIFE));
        assertEquals(1000, RtgMath.outputPermille(-5L, HALF_LIFE), "negative elapsed reads as fresh");
    }

    @Test
    void halvesAtExactlyOneHalfLife() {
        assertWithin(500, RtgMath.outputPermille(HALF_LIFE, HALF_LIFE), 1);
        assertWithin(250, RtgMath.outputPermille(2L * HALF_LIFE, HALF_LIFE), 1);
        assertWithin(125, RtgMath.outputPermille(3L * HALF_LIFE, HALF_LIFE), 1);
    }

    @Test
    void quarterHalfLifeMatchesTheClosedForm() {
        // 2^(-1/4) = 0.8409 -> 840.9‰; 2^(-1/2) = 0.7071 -> 707.1‰
        assertWithin(841, RtgMath.outputPermille(HALF_LIFE / 4L, HALF_LIFE), 1);
        assertWithin(707, RtgMath.outputPermille(HALF_LIFE / 2L, HALF_LIFE), 1);
    }

    @Test
    void decayIsMonotonicallyNonIncreasing() {
        int previous = Integer.MAX_VALUE;
        long step = HALF_LIFE / 997L; // deliberately not a divisor of the table resolution
        for (long t = 0L; t <= 6L * HALF_LIFE; t += step) {
            int now = RtgMath.outputPermille(t, HALF_LIFE);
            assertTrue(now <= previous, "output rose at t=" + t + ": " + previous + " -> " + now);
            previous = now;
        }
    }

    @Test
    void decaysToZeroEventually() {
        assertEquals(0, RtgMath.outputPermille(31L * HALF_LIFE, HALF_LIFE));
        assertEquals(0, RtgMath.outputPermille(Long.MAX_VALUE, HALF_LIFE));
    }

    @Test
    void degenerateHalfLifeReadsAsOneTick() {
        assertEquals(1000, RtgMath.outputPermille(0L, 0L));
        assertWithin(500, RtgMath.outputPermille(1L, 0L), 1);
    }

    @Test
    void cutoffIsStrictlyBelow() {
        assertFalse(RtgMath.spent(50, 50));
        assertTrue(RtgMath.spent(49, 50));
        assertFalse(RtgMath.spent(1000, 50));
    }

    @Test
    void ticksToCutoffIsTheFirstSpentTick() {
        long t = RtgMath.ticksToCutoff(HALF_LIFE, 50);
        assertTrue(RtgMath.spent(RtgMath.outputPermille(t, HALF_LIFE), 50), "spent at t");
        assertFalse(RtgMath.spent(RtgMath.outputPermille(t - 1L, HALF_LIFE), 50), "not yet spent at t-1");
        // 5% of the fresh rate is reached after log2(20) = 4.32 half-lives.
        assertTrue(t > 4L * HALF_LIFE && t < 5L * HALF_LIFE, "cutoff lands in the fifth half-life: " + t);
    }

    /** Integer tolerance assertion (JUnit's delta overloads are float/double only). */
    private static void assertWithin(int expected, int actual, int tolerance) {
        assertTrue(Math.abs(expected - actual) <= tolerance,
                "expected " + expected + " ±" + tolerance + " but was " + actual);
    }

    @Test
    void daysAreWholeGameDays() {
        assertEquals(0, RtgMath.days(TICKS_PER_DAY - 1L));
        assertEquals(1, RtgMath.days(TICKS_PER_DAY));
        assertEquals(0, RtgMath.days(-TICKS_PER_DAY), "negative remaining reads as 0");
    }
}

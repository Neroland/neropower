package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link PoolMath}: the pool's effective I/O (min × count, capped), fair round-robin
 * distribution that honours per-cell limits and rotates the remainder, the PRIORITY_SOURCE permille
 * test and the 0..4 charge bucket. Pure JVM.
 */
class PooledMathTest {

    @Test
    void effectiveIoIsMinTimesCountCapped() {
        assertEquals(4_000L, PoolMath.effectiveIo(1_000, 4, 64_000));
        assertEquals(64_000L, PoolMath.effectiveIo(16_000, 8, 64_000), "capped by bankMaxIoCap");
        assertEquals(0L, PoolMath.effectiveIo(1_000, 0, 64_000), "no members");
        assertEquals(0L, PoolMath.effectiveIo(0, 3, 64_000), "no cell I/O");
        assertEquals(0L, PoolMath.effectiveIo(1_000, 3, 0), "no cap");
    }

    @Test
    void distributeSplitsEvenlyWhenNobodySaturates() {
        long[] given = PoolMath.distribute(new long[] {100, 100, 100}, 90, 0);
        assertArrayEquals(new long[] {30, 30, 30}, given);
        assertEquals(90L, PoolMath.sum(given));
    }

    @Test
    void distributeHonoursLimitsAndRefillsTheRest() {
        // The first cell can only take 10; the other two share what it could not.
        long[] given = PoolMath.distribute(new long[] {10, 100, 100}, 90, 0);
        assertArrayEquals(new long[] {10, 40, 40}, given);
        assertEquals(90L, PoolMath.sum(given));
    }

    @Test
    void distributeNeverExceedsTheTotalLimit() {
        long[] given = PoolMath.distribute(new long[] {5, 5, 5}, 1_000, 0);
        assertArrayEquals(new long[] {5, 5, 5}, given);
        assertEquals(15L, PoolMath.sum(given), "saturated: only what the cells can take");
    }

    @Test
    void distributeRotatesTheRemainderFromTheCursor() {
        long[] first = PoolMath.distribute(new long[] {100, 100, 100}, 4, 0);
        long[] second = PoolMath.distribute(new long[] {100, 100, 100}, 4, 1);
        long[] third = PoolMath.distribute(new long[] {100, 100, 100}, 4, 2);
        assertArrayEquals(new long[] {2, 1, 1}, first);
        assertArrayEquals(new long[] {1, 2, 1}, second);
        assertArrayEquals(new long[] {1, 1, 2}, third);
        // A cursor past the end wraps rather than throwing.
        assertArrayEquals(first, PoolMath.distribute(new long[] {100, 100, 100}, 4, 3));
    }

    @Test
    void distributeDegenerateInputs() {
        assertEquals(0, PoolMath.distribute(new long[0], 50, 0).length);
        assertArrayEquals(new long[] {0, 0}, PoolMath.distribute(new long[] {10, 10}, 0, 0));
        assertArrayEquals(new long[] {0, 0}, PoolMath.distribute(new long[] {0, 0}, 10, 0), "all saturated");
    }

    @Test
    void permilleThresholdFeedsOnlyTheShort() {
        assertTrue(PoolMath.belowThreshold(299, 1_000, 300));
        assertFalse(PoolMath.belowThreshold(300, 1_000, 300), "at the threshold is not below it");
        assertFalse(PoolMath.belowThreshold(0, 0, 300), "no capacity is never fed");
        assertFalse(PoolMath.belowThreshold(0, 1_000, 0), "threshold 0 disables feeding");
        assertTrue(PoolMath.belowThreshold(999, 1_000, 1_000), "threshold 1000 feeds anything not full");
    }

    @Test
    void chargeLevelBuckets() {
        assertEquals(0, PoolMath.chargeLevel(0, 1_000));
        assertEquals(1, PoolMath.chargeLevel(1, 1_000), "any charge shows a bar");
        assertEquals(1, PoolMath.chargeLevel(249, 1_000));
        assertEquals(1, PoolMath.chargeLevel(250, 1_000));
        assertEquals(2, PoolMath.chargeLevel(500, 1_000));
        assertEquals(3, PoolMath.chargeLevel(750, 1_000));
        assertEquals(3, PoolMath.chargeLevel(999, 1_000), "full bar only when full");
        assertEquals(4, PoolMath.chargeLevel(1_000, 1_000));
        assertEquals(0, PoolMath.chargeLevel(5, 0), "no capacity");
    }
}

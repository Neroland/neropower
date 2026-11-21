package za.co.neroland.neropower.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Spent Fuel Rack's one distinctive behaviour: a hot rod becomes a spent rod only after
 * {@code poolCoolTicks} calm ticks in the rack, and the clock stands still while the rack is hot.
 * Pure JVM.
 */
class PoolMathTest {

    private static final int COOL_TICKS = 20_000;

    @Test
    void aHotRodNeedsTheFullCalmTimeToCool() {
        int clock = 0;
        for (int t = 0; t < COOL_TICKS - 1; t++) {
            clock = PoolMath.advance(clock, true);
            assertFalse(PoolMath.cooled(clock, COOL_TICKS), "cooled early at tick " + t);
        }
        clock = PoolMath.advance(clock, true);
        assertTrue(PoolMath.cooled(clock, COOL_TICKS));
    }

    @Test
    void anOverheatingRackPausesTheClock() {
        int clock = 1_234;
        for (int t = 0; t < 100_000; t++) {
            clock = PoolMath.advance(clock, false);
        }
        assertEquals(1_234, clock, "no cooling progress while the rack is at WARNING or worse");
        assertFalse(PoolMath.cooled(clock, COOL_TICKS));
    }

    @Test
    void theClockNeverRunsBackwardsOrOverflows() {
        assertEquals(0, PoolMath.advance(-5, false));
        assertEquals(1, PoolMath.advance(-5, true));
        assertEquals(Integer.MAX_VALUE, PoolMath.advance(Integer.MAX_VALUE, true));
    }

    @Test
    void decayHeatAndWaterCoolingBalanceAtTheDefaults() {
        // Four hot rods (16/t) against five contacts (20/t): a full, well-flooded rack stays calm...
        assertTrue(PoolMath.heatOut(5, 4) >= PoolMath.heatIn(4, 4));
        // ...one with a single contact does not.
        assertTrue(PoolMath.heatOut(1, 4) < PoolMath.heatIn(4, 4));
        assertEquals(0, PoolMath.heatIn(0, 4));
        assertEquals(20, PoolMath.heatOut(9, 4), "contacts are capped at the five non-bottom faces");
        assertEquals(0, PoolMath.heatOut(-1, 4));
    }

    @Test
    void coolStepBucketsMatchTheBlockContract() {
        assertEquals(0, PoolMath.coolStep(true, false, 0, COOL_TICKS));
        assertEquals(1, PoolMath.coolStep(false, false, 0, COOL_TICKS));
        assertEquals(4, PoolMath.coolStep(false, true, 0, COOL_TICKS));
        assertEquals(3, PoolMath.coolStep(false, true, COOL_TICKS / 2, COOL_TICKS));
        assertEquals(2, PoolMath.coolStep(false, true, COOL_TICKS - 1, COOL_TICKS));
        for (int p = 0; p <= COOL_TICKS; p += 997) {
            int step = PoolMath.coolStep(false, true, p, COOL_TICKS);
            assertTrue(step >= 2 && step <= PoolMath.MAX_COOL_STEP);
        }
    }

    @Test
    void permilleIsClamped() {
        assertEquals(0, PoolMath.permille(-1, COOL_TICKS));
        assertEquals(500, PoolMath.permille(COOL_TICKS / 2, COOL_TICKS));
        assertEquals(1000, PoolMath.permille(Integer.MAX_VALUE, COOL_TICKS));
        assertEquals(1000, PoolMath.permille(5, 0));
    }
}

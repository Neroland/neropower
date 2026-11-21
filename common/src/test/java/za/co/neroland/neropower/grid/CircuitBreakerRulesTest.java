package za.co.neroland.neropower.grid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;

/**
 * Rule test for the Circuit Breaker (decision 008 annex): it passes at most 125 % of its limit and
 * nothing while tripped, its load climbs WARNING 60 % / UNSTABLE 80 % / FAILURE (trip) 100 % after the
 * dwell on each rung, a reset by redstone needs a rising edge with the option on, the comparator reads
 * 15 tripped else load / 67, and limits travel the button channel to three significant digits.
 */
class CircuitBreakerRulesTest {

    @Test
    void passesAtMostAQuarterOverTheLimitAndNothingTripped() {
        assertEquals(12_500L, BreakerLogic.passed(false, 50_000L, 100_000L, 10_000));
        assertEquals(8_000L, BreakerLogic.passed(false, 8_000L, 100_000L, 10_000));
        assertEquals(300L, BreakerLogic.passed(false, 8_000L, 300L, 10_000));
        assertEquals(0L, BreakerLogic.passed(true, 8_000L, 100_000L, 10_000));
    }

    @Test
    void tripsAfterSustainedOverload() {
        FailureController ladder = new FailureController(BreakerLogic.thresholds(10));
        int stress = BreakerLogic.stress(12_000L, 10_000);
        assertEquals(1200, stress);
        int tick = 0;
        FailureStage stage = FailureStage.STABLE;
        while (stage != FailureStage.FAILURE && tick < 100) {
            stage = ladder.tick(stress, 1000, true);
            tick++;
        }
        assertEquals(FailureStage.FAILURE, stage);
        assertTrue(tick >= 30, "three rungs of dwell first, took " + tick);
    }

    @Test
    void loadBelowTheWarningNeverClimbs() {
        FailureController ladder = new FailureController(BreakerLogic.thresholds(10));
        for (int i = 0; i < 500; i++) {
            assertEquals(FailureStage.STABLE, ladder.tick(BreakerLogic.stress(5_900L, 10_000), 1000, true));
        }
    }

    @Test
    void redstoneResetNeedsARisingEdge() {
        assertTrue(BreakerLogic.redstoneReset(true, true, false, true));
        assertFalse(BreakerLogic.redstoneReset(true, true, true, true));
        assertFalse(BreakerLogic.redstoneReset(false, true, false, true));
        assertFalse(BreakerLogic.redstoneReset(true, false, false, true));
    }

    @Test
    void comparatorAndLoadStep() {
        assertEquals(15, BreakerLogic.comparator(true, 0));
        assertEquals(14, BreakerLogic.comparator(false, 2000));
        assertEquals(8, BreakerLogic.comparator(false, 600));
        assertEquals(0, BreakerLogic.comparator(false, 0));
        assertEquals(4, BreakerLogic.loadStep(1000));
        assertEquals(2, BreakerLogic.loadStep(600));
    }

    @Test
    void limitsRideTheButtonChannel() {
        for (long v : new long[] {1, 999, 1_000, 10_000, 12_345, 2_000_000, 10_000_000}) {
            int[] ev = ScaledValue.encode(v);
            long back = ScaledValue.decode(ev[0], ev[1]);
            assertTrue(Math.abs(back - v) <= v / 200 + 1, v + " -> " + back);
        }
        assertEquals(10_000_000L, ScaledValue.decode(ScaledValue.encode(10_000_000L)[0], ScaledValue.encode(10_000_000L)[1]));
        int[] notches = BreakerLogic.notches();
        assertEquals(22, notches.length);
        assertArrayEquals(new int[] {1, 2, 5}, new int[] {notches[0], notches[1], notches[2]});
        assertEquals(10_000_000, notches[21]);
        assertEquals(1, BreakerLogic.clampLimit(0));
        assertEquals(10_000_000, BreakerLogic.clampLimit(Long.MAX_VALUE));
    }
}

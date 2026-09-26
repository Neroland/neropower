package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Walks the failure ladder with scripted heat and locks the rules of {@link FailureController}: one
 * rung per tick, the dwell before every climb, hysteresis on the way down, the overload clamp at
 * UNSTABLE, the output penalty, and the save / load round trip. Pure JVM — the controller touches no
 * level, block state or config; thresholds are built directly.
 */
class FailureControllerTest {

    /** 600 / 800 / 1000, 50 hysteresis, 10-tick dwell, 60% unstable output. */
    private static final FailureThresholds LADDER = new FailureThresholds(600, 800, 1000, 50, 10, 600);
    private static final int CAPACITY = 1000;

    private static FailureStage tick(FailureController c, int heat) {
        return c.tick(heat, CAPACITY, true);
    }

    private static List<String> record(FailureController c) {
        List<String> transitions = new ArrayList<>();
        c.setListener((from, to) -> transitions.add(from + ">" + to));
        return transitions;
    }

    @Test
    void startsStableWithFullOutput() {
        FailureController c = new FailureController(LADDER);
        assertEquals(FailureStage.STABLE, c.stage());
        assertEquals(0, c.ticksInStage());
        assertEquals(1000, c.outputPermille());
        assertFalse(c.justEntered());
    }

    @Test
    void walksEveryRungInOrderWithTheDwellBetweenClimbs() {
        FailureController c = new FailureController(LADDER);
        List<String> transitions = record(c);
        // Heat pinned at capacity from tick 1: the ladder still climbs one rung per dwell period.
        int tickCount = 0;
        FailureStage stage = FailureStage.STABLE;
        while (stage != FailureStage.FAILURE && tickCount < 100) {
            stage = tick(c, CAPACITY);
            tickCount++;
        }
        assertEquals(FailureStage.FAILURE, stage);
        // Dwell 10: STABLE→WARNING on tick 10, →UNSTABLE on tick 20, →FAILURE on tick 30.
        assertEquals(30, tickCount, "three climbs, each after a full dwell");
        assertEquals(List.of("STABLE>WARNING", "WARNING>UNSTABLE", "UNSTABLE>FAILURE"), transitions,
                "no rung skipped, even with heat at capacity throughout");
        assertTrue(c.justEntered(), "the tick that entered FAILURE reports it");
        assertEquals(0, c.outputPermille());
    }

    @Test
    void neverClimbsBeforeTheDwellHasPassed() {
        FailureController c = new FailureController(LADDER);
        for (int i = 1; i < 10; i++) {
            assertEquals(FailureStage.STABLE, tick(c, CAPACITY), "tick " + i + " is inside the dwell");
            assertFalse(c.justEntered());
        }
        assertEquals(FailureStage.WARNING, tick(c, CAPACITY), "tick 10 satisfies a 10-tick dwell");
        assertTrue(c.justEntered());
        assertEquals(0, c.ticksInStage(), "the dwell counter restarts on entry");
        assertEquals(FailureStage.WARNING, tick(c, CAPACITY));
        assertFalse(c.justEntered(), "justEntered is a one-tick pulse");
        assertEquals(1, c.ticksInStage());
    }

    @Test
    void climbsExactlyAtTheEntryThreshold() {
        FailureController c = new FailureController(LADDER);
        for (int i = 0; i < 20; i++) {
            tick(c, 599);
        }
        assertEquals(FailureStage.STABLE, c.stage(), "599 permille is below the 600 warning threshold");
        assertEquals(FailureStage.WARNING, tick(c, 600), "600 permille enters WARNING once the dwell has passed");
    }

    @Test
    void hysteresisHoldsTheStageUntilHeatDropsBelowThresholdMinusHysteresis() {
        FailureController c = new FailureController(LADDER);
        for (int i = 0; i < 10; i++) {
            tick(c, 700);
        }
        assertEquals(FailureStage.WARNING, c.stage());
        // Hovering just under the entry threshold does not leave the stage: exit is 600 - 50 = 550.
        assertEquals(FailureStage.WARNING, tick(c, 599));
        assertEquals(FailureStage.WARNING, tick(c, 550), "exactly the exit threshold still holds");
        assertEquals(FailureStage.STABLE, tick(c, 549), "one below the exit threshold drops a rung");
        assertTrue(c.justEntered());
    }

    @Test
    void fallsOneRungPerTickWithoutADwell() {
        FailureController c = new FailureController(LADDER);
        for (int i = 0; i < 20; i++) {
            tick(c, 900);
        }
        assertEquals(FailureStage.UNSTABLE, c.stage());
        List<String> transitions = record(c);
        assertEquals(FailureStage.WARNING, tick(c, 0), "cooling drops one rung at once");
        assertEquals(FailureStage.STABLE, tick(c, 0), "and the next tick drops the next");
        assertEquals(FailureStage.STABLE, tick(c, 0));
        assertEquals(List.of("UNSTABLE>WARNING", "WARNING>STABLE"), transitions);
    }

    @Test
    void overloadDisabledPinsTheLadderAtUnstable() {
        FailureController c = new FailureController(LADDER);
        for (int i = 0; i < 60; i++) {
            c.tick(CAPACITY, CAPACITY, false);
        }
        assertEquals(FailureStage.UNSTABLE, c.stage(), "with overload off the machine never fails");
        assertEquals(600, c.outputPermille(), "but it is throttled by the unstable penalty");
        assertTrue(c.ticksInStage() > 10, "it stays there, dwell long satisfied");
        // Turning overload on again at capacity completes the climb on the very next tick.
        assertEquals(FailureStage.FAILURE, c.tick(CAPACITY, CAPACITY, true));
    }

    @Test
    void overloadDisabledBacksAPersistedFailureOffToUnstable() {
        FailureController c = new FailureController(LADDER);
        c.load(FailureStage.FAILURE.code(), 5);
        assertEquals(FailureStage.FAILURE, c.stage());
        List<String> transitions = record(c);
        assertEquals(FailureStage.UNSTABLE, c.tick(CAPACITY, CAPACITY, false));
        assertEquals(List.of("FAILURE>UNSTABLE"), transitions);
        assertTrue(c.justEntered());
    }

    @Test
    void outputPenaltyFollowsTheStage() {
        FailureController c = new FailureController(LADDER);
        assertEquals(1000, c.outputPermille());
        for (int i = 0; i < 10; i++) {
            tick(c, CAPACITY);
        }
        assertEquals(FailureStage.WARNING, c.stage());
        assertEquals(1000, c.outputPermille(), "WARNING is an alarm, not a throttle");
        for (int i = 0; i < 10; i++) {
            tick(c, CAPACITY);
        }
        assertEquals(FailureStage.UNSTABLE, c.stage());
        assertEquals(600, c.outputPermille());
    }

    @Test
    void saveLoadRoundTripsStageAndDwellSilently() {
        FailureController c = new FailureController(LADDER);
        for (int i = 0; i < 13; i++) {
            tick(c, CAPACITY);
        }
        assertEquals(FailureStage.WARNING, c.stage());
        assertEquals(3, c.ticksInStage());

        FailureController restored = new FailureController(LADDER);
        List<String> transitions = record(restored);
        restored.load(c.saveStage(), c.saveTicksInStage());
        assertEquals(FailureStage.WARNING, restored.stage());
        assertEquals(3, restored.ticksInStage());
        assertFalse(restored.justEntered(), "a reload is not a transition");
        assertTrue(transitions.isEmpty(), "and fires no listener");
        // The restored dwell continues where it left off: 7 more ticks reach the 10-tick dwell.
        for (int i = 0; i < 6; i++) {
            assertEquals(FailureStage.WARNING, tick(restored, CAPACITY));
        }
        assertEquals(FailureStage.UNSTABLE, tick(restored, CAPACITY));
    }

    @Test
    void loadToleratesGarbage() {
        FailureController c = new FailureController(LADDER);
        c.load(99, -4);
        assertEquals(FailureStage.STABLE, c.stage(), "an unknown stage code reads as STABLE");
        assertEquals(0, c.ticksInStage(), "a negative dwell reads as 0");
    }

    @Test
    void permilleIsSafeForDegenerateCapacities() {
        assertEquals(0, FailureController.permille(500, 0), "no capacity: no heat fraction");
        assertEquals(0, FailureController.permille(-5, 1000), "negative heat reads as 0");
        assertEquals(1000, FailureController.permille(1000, 1000));
        assertEquals(1000, FailureController.permille(Integer.MAX_VALUE, Integer.MAX_VALUE), "no int overflow");
        assertEquals(499, FailureController.permille(Integer.MAX_VALUE / 2, Integer.MAX_VALUE),
                "MAX/2 is one short of half, so 499 (long maths, no overflow)");
    }

    @Test
    void stageCodesMatchNeroTechsFailureChannel() {
        assertEquals(0, FailureStage.STABLE.code());
        assertEquals(1, FailureStage.WARNING.code());
        assertEquals(2, FailureStage.UNSTABLE.code());
        assertEquals(3, FailureStage.FAILURE.code());
        assertEquals(FailureStage.UNSTABLE, FailureStage.byCode(2));
        assertEquals(FailureStage.STABLE, FailureStage.byCode(7));
    }
}

package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link FailureThresholds} validation: a strictly rising ladder within permille bounds, a
 * non-negative hysteresis and dwell, a penalty within permille — and the derived entry / exit
 * thresholds. Pure JVM.
 */
class FailureThresholdsTest {

    @Test
    void defaultsAreValidAndMatchTheConfigDefaults() {
        FailureThresholds d = FailureThresholds.DEFAULTS;
        assertEquals(600, d.warningPermille());
        assertEquals(800, d.unstablePermille());
        assertEquals(1000, d.failurePermille());
        assertEquals(50, d.hysteresisPermille());
        assertEquals(100, d.minDwellTicks());
        assertEquals(600, d.unstablePenaltyPermille());
    }

    @Test
    void ladderMustRiseStrictly() {
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(800, 600, 1000, 50, 100, 600),
                "warning above unstable");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 600, 1000, 50, 100, 600),
                "warning equal to unstable");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 800, 50, 100, 600),
                "unstable equal to failure");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 1000, 900, 50, 100, 600),
                "failure below unstable");
    }

    @Test
    void permilleFieldsStayWithinBounds() {
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(0, 800, 1000, 50, 100, 600),
                "warning must be positive");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1001, 50, 100, 600),
                "failure above 1000");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1000, -1, 100, 600),
                "negative hysteresis");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1000, 1001, 100, 600),
                "hysteresis above 1000");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1000, 50, -1, 600),
                "negative dwell");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1000, 50, 100, -1),
                "negative penalty");
        assertThrows(IllegalArgumentException.class, () -> new FailureThresholds(600, 800, 1000, 50, 100, 1001),
                "penalty above 1000");
    }

    @Test
    void edgeValuesAreAccepted() {
        new FailureThresholds(1, 2, 3, 0, 0, 0);
        new FailureThresholds(998, 999, 1000, 1000, 0, 1000);
    }

    @Test
    void entryAndExitThresholdsFollowTheLadder() {
        FailureThresholds t = new FailureThresholds(600, 800, 1000, 50, 100, 600);
        assertEquals(0, t.entryPermille(FailureStage.STABLE));
        assertEquals(600, t.entryPermille(FailureStage.WARNING));
        assertEquals(800, t.entryPermille(FailureStage.UNSTABLE));
        assertEquals(1000, t.entryPermille(FailureStage.FAILURE));
        assertEquals(Integer.MIN_VALUE, t.exitPermille(FailureStage.STABLE), "STABLE is never left downwards");
        assertEquals(550, t.exitPermille(FailureStage.WARNING));
        assertEquals(750, t.exitPermille(FailureStage.UNSTABLE));
        assertEquals(950, t.exitPermille(FailureStage.FAILURE));
    }

    @Test
    void exitThresholdIsFlooredAtZero() {
        FailureThresholds t = new FailureThresholds(10, 20, 30, 500, 0, 600);
        assertEquals(0, t.exitPermille(FailureStage.WARNING), "a hysteresis wider than the threshold floors at 0");
    }
}

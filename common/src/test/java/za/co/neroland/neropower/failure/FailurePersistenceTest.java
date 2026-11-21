package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Locks the persisted failure ladder (additive NBT keys {@code FailureStage} / {@code FailureTicks}):
 * absent keys read as STABLE / 0 (today's behaviour), every stage below FAILURE round-trips silently,
 * a saved FAILURE loads as UNSTABLE so a reload never fires a failure action, a negative dwell clamps to
 * 0 and an unknown code reads as STABLE. Pure JVM.
 */
class FailurePersistenceTest {

    private static final FailureThresholds LADDER = new FailureThresholds(600, 800, 1000, 50, 10, 600);

    private static List<String> record(FailureController c) {
        List<String> out = new ArrayList<>();
        c.setListener((from, to) -> out.add(from + ">" + to));
        return out;
    }

    @Test
    void absentKeysReadAsStable() {
        FailureController c = new FailureController(LADDER);
        c.load(0, 0); // what getIntOr(..., 0) yields for a 0.3.x save
        assertEquals(FailureStage.STABLE, c.stage());
        assertEquals(0, c.ticksInStage());
    }

    @Test
    void stagesBelowFailureRoundTripSilently() {
        for (FailureStage stage : new FailureStage[] {FailureStage.STABLE, FailureStage.WARNING, FailureStage.UNSTABLE}) {
            FailureController c = new FailureController(LADDER);
            List<String> transitions = record(c);
            c.load(stage.code(), 7);
            assertEquals(stage, c.stage());
            assertEquals(7, c.ticksInStage());
            assertEquals(stage.code(), c.saveStage());
            assertEquals(7, c.saveTicksInStage());
            assertFalse(c.justEntered());
            assertTrue(transitions.isEmpty());
        }
    }

    @Test
    void savedFailureLoadsAsUnstable() {
        FailureController c = new FailureController(LADDER);
        List<String> transitions = record(c);
        c.load(FailureStage.FAILURE.code(), 3);
        assertEquals(FailureStage.UNSTABLE, c.stage());
        assertFalse(c.justEntered(), "no failure action on the first tick after a reload");
        assertTrue(transitions.isEmpty());
    }

    @Test
    void negativeDwellAndUnknownCodeAreClamped() {
        FailureController c = new FailureController(LADDER);
        c.load(FailureStage.WARNING.code(), -40);
        assertEquals(0, c.ticksInStage());
        c.load(99, 5);
        assertEquals(FailureStage.STABLE, c.stage());
    }
}

package za.co.neroland.neropower.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Betavoltaic Cell's distinctive rules: a capsule gives a flat trickle for exactly its life and is
 * then spent once (turned into a Spent Beta Capsule, not voided); a full buffer pauses its clock; with no
 * live capsule there is no output.
 */
class BetavoltaicRulesTest {

    private static final int NE = 4;
    private static final int LIFE = 720_000;

    @Test
    void defaultLifeIsThirtyInGameDays() {
        assertEquals(30L * 24_000L, LIFE);
    }

    @Test
    void capsuleGivesFlatOutputForExactlyItsLifeThenIsSpentOnce() {
        BetavoltaicCore core = new BetavoltaicCore();
        int state = BetavoltaicCore.STATE_LIVE;
        long ne = 0;
        int spent = 0;
        int working = 0;
        for (long t = 0; t < LIFE + 5_000L; t++) {
            BetavoltaicCore.Step step = core.tick(state, Long.MAX_VALUE, NE, LIFE);
            if (step.working()) {
                assertEquals(NE, step.generate(), "flat: it does not fade");
                working++;
            }
            ne += step.generate();
            if (step.spendCapsule()) {
                spent++;
                state = BetavoltaicCore.STATE_SPENT;
            }
        }
        assertEquals(LIFE, working);
        assertEquals((long) LIFE * NE, ne, "2 880 000 NE per capsule at the defaults");
        assertEquals(1, spent, "the capsule is consumed exactly once");
    }

    @Test
    void noOutputWithoutALiveCapsule() {
        BetavoltaicCore core = new BetavoltaicCore();
        for (int state : new int[] {BetavoltaicCore.STATE_NONE, BetavoltaicCore.STATE_SPENT}) {
            for (int i = 0; i < 1_000; i++) {
                BetavoltaicCore.Step step = core.tick(state, Long.MAX_VALUE, NE, LIFE);
                assertEquals(0, step.generate());
                assertFalse(step.working());
                assertFalse(step.spendCapsule(), "nothing to spend");
                assertEquals(GenerationReadout.CAUSE_NO_FUEL, step.cause());
            }
        }
    }

    @Test
    void fullBufferPausesTheClock() {
        BetavoltaicCore core = new BetavoltaicCore();
        core.tick(BetavoltaicCore.STATE_LIVE, Long.MAX_VALUE, NE, LIFE);
        long before = core.used();
        for (int i = 0; i < 10_000; i++) {
            BetavoltaicCore.Step step = core.tick(BetavoltaicCore.STATE_LIVE, NE - 1L, NE, LIFE);
            assertEquals(0, step.generate());
            assertEquals(GenerationReadout.CAUSE_BUFFER_FULL, step.cause());
        }
        assertEquals(before, core.used(), "a full buffer wastes none of the capsule's life");
    }

    @Test
    void removingTheCapsuleResetsTheClockForTheNextOne() {
        BetavoltaicCore core = new BetavoltaicCore();
        for (int i = 0; i < 100; i++) {
            core.tick(BetavoltaicCore.STATE_LIVE, Long.MAX_VALUE, NE, LIFE);
        }
        assertEquals(100L, core.used());
        core.tick(BetavoltaicCore.STATE_NONE, Long.MAX_VALUE, NE, LIFE);
        assertEquals(0L, core.used());
        assertEquals(LIFE, core.left(LIFE));
    }

    @Test
    void aSavedFinishedCapsuleIsSpentOnLoad() {
        BetavoltaicCore core = new BetavoltaicCore();
        core.load(LIFE);
        BetavoltaicCore.Step step = core.tick(BetavoltaicCore.STATE_LIVE, Long.MAX_VALUE, NE, LIFE);
        assertTrue(step.spendCapsule());
        assertEquals(0, step.generate());
    }
}

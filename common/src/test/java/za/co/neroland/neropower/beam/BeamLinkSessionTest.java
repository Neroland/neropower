package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link BeamLinkSession}: one pending source per player, expiry after
 * {@link BeamLinkSession#TIMEOUT_TICKS}, take-consumes, clear and purge. Pure JVM.
 */
class BeamLinkSessionTest {

    private static final UUID ALICE = new UUID(1L, 1L);
    private static final UUID BOB = new UUID(2L, 2L);
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void beginThenPeekThenTake() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 10, 64, -5, 100L);

        var peeked = session.peek(ALICE, 150L);
        assertTrue(peeked.isPresent());
        assertTrue(peeked.get().at(OVERWORLD, 10, 64, -5));
        assertFalse(peeked.get().at("minecraft:the_nether", 10, 64, -5), "dimension is part of the identity");
        assertEquals(1, session.size(), "peek does not consume");

        var taken = session.take(ALICE, 150L);
        assertTrue(taken.isPresent());
        assertEquals(0, session.size(), "take consumes");
        assertTrue(session.take(ALICE, 150L).isEmpty());
    }

    @Test
    void entriesArePerPlayer() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 1, 1, 1, 0L);
        session.begin(BOB, OVERWORLD, 2, 2, 2, 0L);
        assertTrue(session.peek(ALICE, 1L).get().at(OVERWORLD, 1, 1, 1));
        assertTrue(session.peek(BOB, 1L).get().at(OVERWORLD, 2, 2, 2));
        session.clear(ALICE);
        assertTrue(session.peek(ALICE, 1L).isEmpty());
        assertTrue(session.peek(BOB, 1L).isPresent());
    }

    @Test
    void secondBeginReplacesTheFirst() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 1, 1, 1, 0L);
        session.begin(ALICE, OVERWORLD, 9, 9, 9, 5L);
        assertEquals(1, session.size());
        assertTrue(session.peek(ALICE, 6L).get().at(OVERWORLD, 9, 9, 9));
    }

    @Test
    void entriesExpireAfterTheTimeout() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 1, 1, 1, 1000L);
        long lastLive = 1000L + BeamLinkSession.TIMEOUT_TICKS - 1;
        assertTrue(session.peek(ALICE, lastLive).isPresent(), "live one tick before expiry");
        assertTrue(session.peek(ALICE, 1000L + BeamLinkSession.TIMEOUT_TICKS).isEmpty(), "gone at expiry");
        assertEquals(0, session.size(), "the touch purged it");
    }

    @Test
    void purgeDropsOnlyExpiredEntries() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 1, 1, 1, 0L);
        session.begin(BOB, OVERWORLD, 2, 2, 2, 400L);
        assertEquals(1, session.purge(700L), "Alice expired at 600, Bob lives until 1000");
        assertTrue(session.peek(ALICE, 700L).isEmpty());
        assertTrue(session.peek(BOB, 700L).isPresent());
        assertEquals(1, session.purge(1000L));
        assertEquals(0, session.size());
    }

    @Test
    void resetForgetsEverything() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, OVERWORLD, 1, 1, 1, 0L);
        session.begin(BOB, OVERWORLD, 2, 2, 2, 0L);
        session.reset();
        assertEquals(0, session.size());
    }
}

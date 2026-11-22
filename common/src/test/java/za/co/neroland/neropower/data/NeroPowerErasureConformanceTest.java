package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.neropower.beam.BeamLinkSession;

/**
 * POPIA/GDPR conformance for NeroPower's eraser, run through Core's reusable {@link ErasureConformance}
 * harness exactly as Core's own suite runs it: plain JVM, no game bootstrap, {@code null} server. The
 * production eraser needs a live server for the SavedData store and Core's backup, so the eraser
 * registered here binds {@link NeroPowerDataErasure#eraseWith} to a bare {@link NeroPowerErasureState} and a
 * fresh {@link BeamLinkSession}.
 *
 * <p>The beam link owner lives in block-entity NBT, which no plain-JVM test can construct; the probe models a
 * transmitter as the erasure sees it: a link owner and a link day, re-checked per epoch and on load, exactly as
 * {@code BeamTransmitterBlockEntity.checkLinkOwnerErasure} does. Every test uses random UUIDs and logs none.
 */
class NeroPowerErasureConformanceTest {

    /** A transmitter as far as erasure is concerned. */
    private static final class FakeTransmitter {
        private UUID linkOwner;
        private long linkDay = NeroPowerErasureState.UNKNOWN_LINK_DAY;
        private int checkedEpoch = -1;

        void link(UUID owner, long day) {
            this.linkOwner = owner;
            this.linkDay = day;
        }

        boolean hasOwner(UUID owner) {
            return owner.equals(this.linkOwner);
        }

        /** A chunk (re)load: the next tick re-checks whatever the epoch is. */
        void reload() {
            this.checkedEpoch = -1;
        }

        void tick(NeroPowerErasureState state) {
            int epoch = NeroPowerErasureState.erasureEpoch();
            if (this.checkedEpoch == epoch) {
                return;
            }
            this.checkedEpoch = epoch;
            if (this.linkOwner != null && state.erasedOnOrAfter(this.linkOwner, this.linkDay)) {
                this.linkOwner = null;
            }
        }
    }

    private final List<PlayerDataEraser> registered = new ArrayList<>();

    private void register(PlayerDataEraser eraser) {
        registered.add(eraser);
        PlayerDataErasure.register(eraser);
    }

    @AfterEach
    void unregisterErasers() {
        registered.forEach(PlayerDataErasure::unregister);
        registered.clear();
    }

    @Test
    void erasurePurgesEverythingNeroPowerStores() {
        UUID player = UUID.randomUUID();
        long today = 20_000L;
        NeroPowerErasureState state = new NeroPowerErasureState();
        BeamLinkSession session = new BeamLinkSession();
        FakeTransmitter loaded = new FakeTransmitter();
        FakeTransmitter unloaded = new FakeTransmitter();

        session.begin(player, "minecraft:overworld", 1, 2, 3, 0L);
        loaded.link(player, today - 10);
        unloaded.link(player, NeroPowerErasureState.UNKNOWN_LINK_DAY); // a pre-0.4.0 link: no day saved
        loaded.tick(state);

        register((server, uuid) -> {
            NeroPowerDataErasure.eraseWith(session, state, uuid, today);
            loaded.tick(state);
        });

        ErasureConformance.create()
                .probe("neropower:beam_link_session", uuid -> session.peek(uuid, 0L).isPresent())
                .probe("neropower:beam_link_owner_loaded", loaded::hasOwner)
                .probe("neropower:beam_link_owner_unloaded", uuid -> {
                    unloaded.reload();
                    unloaded.tick(state);
                    return unloaded.hasOwner(uuid);
                })
                .verify(null, player);

        assertTrue(state.isPending(player));
    }

    @Test
    void aChunkUnloadedFourHundredDaysStillDropsTheOwner() {
        UUID player = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        FakeTransmitter outpost = new FakeTransmitter();
        outpost.link(player, 100L);
        state.mark(player, 120L);
        // Nobody loads the outpost for 400 days; other players are erased meanwhile.
        for (int i = 0; i < 5; i++) {
            state.mark(UUID.randomUUID(), 200L + i * 50L);
        }
        outpost.reload();
        outpost.tick(state);
        assertFalse(outpost.hasOwner(player), "the tombstone did not expire (old 30-day bug)");
        assertTrue(state.isPending(player));
    }

    @Test
    void aReturningPlayersNewLinkSurvivesLaterErasures() {
        UUID player = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        state.mark(player, 300L);
        FakeTransmitter fresh = new FakeTransmitter();
        fresh.link(player, 301L); // linked after the erasure
        fresh.tick(state);
        state.mark(UUID.randomUUID(), 302L); // someone else's erasure bumps the epoch
        fresh.tick(state);
        fresh.reload();
        fresh.tick(state);
        assertTrue(fresh.hasOwner(player), "a link made after the erasure keeps its owner");

        FakeTransmitter sameDay = new FakeTransmitter();
        sameDay.link(player, 300L);
        sameDay.tick(state);
        assertFalse(sameDay.hasOwner(player), "a link from the erasure day itself is dropped (the private choice)");
    }

    @Test
    void tombstonesHoldNoPlainUuidAndLegacyRowsAreHashed() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        NeroPowerErasureState legacy = NeroPowerErasureState.fromLegacyRows(Map.of(a, 10L, b, 11L));
        assertEquals(2, legacy.size());
        assertTrue(legacy.isPending(a));
        assertTrue(legacy.erasedOnOrAfter(b, 11L));
        assertFalse(legacy.erasedOnOrAfter(b, 12L));
        assertFalse(legacy.storesPlain(a.toString()), "no plain UUID survives the load");
        assertFalse(legacy.storesPlain(b.toString()));

        NeroPowerErasureState fresh = new NeroPowerErasureState();
        fresh.mark(a, 5L);
        assertFalse(fresh.storesPlain(a.toString()));
        NeroPowerErasureState other = new NeroPowerErasureState();
        other.mark(b, 5L);
        assertFalse(other.isPending(a), "salts are per world: one world's tombstone means nothing in another");
    }

    @Test
    void erasureTargetsOnlyTheRequestedPlayer() {
        UUID erased = UUID.randomUUID();
        UUID retained = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        BeamLinkSession session = new BeamLinkSession();
        session.begin(erased, "minecraft:overworld", 0, 0, 0, 0L);
        session.begin(retained, "minecraft:overworld", 0, 0, 0, 0L);

        NeroPowerDataErasure.eraseWith(session, state, erased, 100L);

        assertTrue(session.peek(erased, 0L).isEmpty());
        assertTrue(session.peek(retained, 0L).isPresent(), "another player's session must survive");
        assertTrue(state.isPending(erased));
        assertFalse(state.isPending(retained));
    }

    @Test
    void markBumpsTheEpochAndRepeatsKeepTheLatestDay() {
        UUID player = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        int epoch = NeroPowerErasureState.erasureEpoch();
        state.mark(player, 10L);
        state.mark(player, 40L);
        assertEquals(epoch + 2, NeroPowerErasureState.erasureEpoch(), "every mark moves the epoch once");
        assertEquals(1, state.size());
        assertTrue(state.erasedOnOrAfter(player, 40L), "the later erasure day wins");
        assertFalse(state.isPending(null));
    }

    @Test
    void erasingAPlayerNobodyHasHeardOfIsHarmless() {
        NeroPowerErasureState state = new NeroPowerErasureState();
        BeamLinkSession session = new BeamLinkSession();
        NeroPowerDataErasure.eraseWith(session, state, UUID.randomUUID(), 5L);
        assertEquals(1, state.size());
        assertEquals(0, session.size());
    }
}

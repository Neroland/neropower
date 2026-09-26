package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataEraser;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

import za.co.neroland.neropower.beam.BeamLinkSession;

/**
 * POPIA/GDPR conformance for NeroPower's eraser, run through Core's reusable
 * {@link ErasureConformance} harness exactly as Core's own suite runs it: plain JVM, no game
 * bootstrap, {@code null} server. The production eraser ({@code NeroPowerDataErasure.erase}) needs a
 * live {@link net.minecraft.server.MinecraftServer} to fetch the SavedData store and refresh Core's
 * backup, so — as Core's test does for its own stores — the eraser registered here binds
 * {@link NeroPowerDataErasure#eraseWith} to a bare {@link NeroPowerErasureState} and a fresh
 * {@link BeamLinkSession}. The backup refresh is a {@code SavedDataRecovery} call with no logic of
 * its own; the in-game half is covered by runtime verification ({@code /neroland data erase}).
 *
 * <p>The beam link owner lives in block-entity NBT, which no plain-JVM test can construct; the
 * probe therefore models a transmitter as the harness sees it — an object holding a link-owner
 * UUID that consults the pending set on its next tick — so the run proves the whole mechanism:
 * mark → pending → owner dropped. Every test uses a random UUID and never logs it.
 */
class NeroPowerErasureConformanceTest {

    /** A transmitter as far as erasure is concerned: a link owner that is re-checked per epoch. */
    private static final class FakeTransmitter {
        private UUID linkOwner;
        private int checkedEpoch = -1;

        void link(UUID owner) {
            this.linkOwner = owner;
        }

        boolean hasOwner(UUID owner) {
            return owner.equals(this.linkOwner);
        }

        /** What {@code BeamTransmitterBlockEntity.checkLinkOwnerErasure} does on a tick. */
        void tick(NeroPowerErasureState state) {
            int epoch = NeroPowerErasureState.erasureEpoch();
            if (this.checkedEpoch == epoch) {
                return;
            }
            this.checkedEpoch = epoch;
            if (this.linkOwner != null && state.isPending(this.linkOwner)) {
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

        // Seed, so the probes are not vacuous: a pending link session and two linked transmitters.
        session.begin(player, "minecraft:overworld", 1, 2, 3, 0L);
        loaded.link(player);
        unloaded.link(player);
        loaded.tick(state); // the loaded one has already checked the current epoch

        register((server, uuid) -> {
            NeroPowerDataErasure.eraseWith(session, state, uuid, today);
            loaded.tick(state); // a loaded transmitter reacts on its next tick (epoch bumped)
        });

        ErasureConformance.create()
                .probe("neropower:beam_link_session", uuid -> session.peek(uuid, 0L).isPresent())
                .probe("neropower:beam_link_owner_loaded", loaded::hasOwner)
                .probe("neropower:beam_link_owner_unloaded", uuid -> {
                    unloaded.tick(state); // an unloaded one catches up on its first tick after load
                    return unloaded.hasOwner(uuid);
                })
                .verify(null, player);

        assertTrue(state.isPending(player), "the pending row must stay for the retention window");
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
    void pendingRowsExpireAfterThirtyDays() {
        UUID player = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        state.mark(player, 100L);

        assertEquals(0, state.purgeExpired(100L + NeroPowerErasureState.ERASURE_RETENTION_DAYS));
        assertTrue(state.isPending(player), "still pending on the last day of the window");
        assertEquals(1, state.purgeExpired(100L + NeroPowerErasureState.ERASURE_RETENTION_DAYS + 1));
        assertFalse(state.isPending(player));
        assertEquals(0, state.size());
    }

    @Test
    void markPurgesExpiredRowsAndBumpsTheEpoch() {
        UUID old = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        NeroPowerErasureState state = new NeroPowerErasureState();
        state.mark(old, 0L);
        int epoch = NeroPowerErasureState.erasureEpoch();

        state.mark(fresh, 31L);

        assertFalse(state.isPending(old), "a mark purges rows past the retention window");
        assertTrue(state.isPending(fresh));
        assertEquals(epoch + 1, NeroPowerErasureState.erasureEpoch(), "every mark moves the epoch once");
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

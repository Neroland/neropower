package za.co.neroland.neropower.data;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataErasure;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neropower.beam.BeamLinkSession;

/**
 * NeroPower's contribution to Core's shared per-player erasure (POPIA/GDPR, Stage 8): one
 * {@link za.co.neroland.nerolandcore.data.PlayerDataEraser} registered from
 * {@code NeroPowerCommon.init()} via {@link #register()}, so {@code /neroland data erase <uuid>}
 * (or a player's own {@code /neroland data eraseme}, or Core's inactivity sweep) purges the player
 * from NeroPower alongside every other Neroland mod.
 *
 * <p><b>What NeroPower stores per player, and what erasure does with it:</b>
 * <ol>
 *   <li><b>Beam link owner</b> — the UUID of the player who linked a Beam Transmitter / Relay, in
 *       that block entity's NBT ({@code LinkOwnerMost} / {@code LinkOwnerLeast}). There is no list of loaded block entities to
 *       sweep, so the eraser records the UUID in {@link NeroPowerErasureState} (pending set) and bumps
 *       its epoch: every loaded transmitter / relay drops a listed owner on its next tick, every
 *       unloaded one on its next load. <b>Retention of the pending row: 30 days</b>
 *       ({@link NeroPowerErasureState#ERASURE_RETENTION_DAYS}), purged on load and on each mark.
 *       The store's Core backup copy is refreshed at once ({@link SavedDataRecovery#backupNow}),
 *       as the {@code SavedDataRecovery} contract requires.</li>
 *   <li><b>Beam link session</b> — the transient, in-memory "first endpoint picked" entry keyed by
 *       the linking player ({@link BeamLinkSession}, 30-second timeout, never persisted). Cleared
 *       outright.</li>
 *   <li><b>Fission core owner</b> — a reactor's owner is NeroTech's {@code ownerId} on the shared
 *       machine base (only recorded when NeroTech's per-player pollution attribution is on). It is
 *       already erased by <i>NeroTech's</i> eraser through the same pending-set mechanism; NeroPower
 *       does not duplicate that.</li>
 *   <li><b>Scorch zones</b> hold no player data (a place, a radius, an expiry day) — nothing to
 *       erase.</li>
 * </ol>
 *
 * <p>No player identity is logged: Core logs an anonymous count only.
 */
public final class NeroPowerDataErasure {

    /** The backup label (the store id) — one string for the log and the backup file name. */
    private static final String ID_LABEL = NeroPowerErasureState.ID.toString();

    private NeroPowerDataErasure() {
    }

    /** Register NeroPower's eraser with Core. Call once from common init. */
    public static void register() {
        PlayerDataErasure.register(NeroPowerDataErasure::erase);
    }

    /** The production eraser: resolves the server's stores and delegates to {@link #eraseWith}. */
    static void erase(MinecraftServer server, UUID player) {
        NeroPowerErasureState state = NeroPowerErasureState.get(server);
        eraseWith(BeamLinkSession.INSTANCE, state, player, NeroPowerErasureState.todayEpochDay());
        SavedDataRecovery.backupNow(server.overworld(), NeroPowerErasureState.TYPE, state, ID_LABEL);
    }

    /**
     * The Minecraft-free core of the eraser, bound to explicit stores so the conformance test can
     * run it on bare instances: forget the player's pending link session and mark them for link-owner
     * erasure as of {@code today}.
     */
    public static void eraseWith(BeamLinkSession session, NeroPowerErasureState state, UUID player, long today) {
        session.clear(player);
        state.mark(player, today);
    }
}

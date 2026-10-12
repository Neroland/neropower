package za.co.neroland.neropower.progression;

import java.util.UUID;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerotech.pollution.PollutionAttributionPrefs;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * NeroPower's personal milestone records: player-scope Neroland Core gates
 * ({@code data/neropower/neroland_gates/*.json}) opened for a player the first time something
 * notable happens — a formed reactor burns fuel, a beam link is made, a battery bank's pooled
 * capacity reaches a configured size. They are <b>records</b>, not locks: nothing in NeroPower checks
 * them for access, and a quest pack reads them with a read-only {@code gate_open} objective.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the only player-keyed input is the machine's stored owner UUID
 * (NeroTech's {@code ownerId}, captured at placement; see {@code PRIVACY.md}) or the online player
 * who performed the action. An unowned machine records nothing, and an owner who has opted out of
 * attribution ({@link PollutionAttributionPrefs}, the NeroLink own-data control) is skipped too. The
 * UUID goes only into {@link ProgressionGates#open(MinecraftServer, UUID, Identifier)}; it is never
 * logged or broadcast. The gate rows live in Core's progression store and are erased through Core's
 * shared erasure hook.
 */
public final class MilestoneGates {

    /** A formed Fission Reactor burned fuel for the first time. */
    public static final Identifier CRITICALITY = gate("criticality");
    /** The player linked a Beam Transmitter to a target. */
    public static final Identifier BEAM_LINKED = gate("beam_linked");
    /** A Battery Bank's pooled capacity reached {@code bankCapacityMilestone}. */
    public static final Identifier BANK_CAPACITY = gate("bank_capacity");

    private MilestoneGates() {
    }

    private static Identifier gate(String path) {
        return Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path);
    }

    /**
     * Record {@code gate} for the machine owner {@code ownerId}, online or offline. Silently does
     * nothing when the owner is unknown or has opted out of attribution. Idempotent: an already-open
     * gate stays open and fires no event.
     *
     * @return {@code true} if the gate was newly opened
     */
    public static boolean record(ServerLevel level, @Nullable UUID ownerId, Identifier gate) {
        if (ownerId == null) {
            return false;
        }
        MinecraftServer server = level.getServer();
        if (server == null || PollutionAttributionPrefs.get(server).isOptedOut(ownerId)) {
            return false;
        }
        if (ProgressionGates.isOpen(server, ownerId, gate)) {
            return false;
        }
        return ProgressionGates.open(server, ownerId, gate);
    }

    /**
     * Record {@code gate} for an online player who performed the action themselves (no machine owner
     * involved). Idempotent.
     *
     * @return {@code true} if the gate was newly opened
     */
    public static boolean record(ServerPlayer player, Identifier gate) {
        if (ProgressionGates.isOpen(player, gate)) {
            return false;
        }
        return ProgressionGates.open(player, gate);
    }
}

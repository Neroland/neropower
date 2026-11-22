package za.co.neroland.neropower.data;

import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerolandcore.data.PlayerDataExport;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.beam.BeamLinkSession;

/**
 * NeroPower's part of a player's data export (POPIA s23 / GDPR Art. 15 and 20), the read-side twin of
 * {@link NeroPowerDataErasure}, registered with Core's {@link PlayerDataExport} from common init.
 *
 * <p>What it reports, matching the eraser item by item:
 * <ol>
 *   <li><b>Beam link owner</b>: the UUID is kept on each linked Beam Transmitter or Relay in the world,
 *       and NeroPower keeps no index of them (it would map every base). The export says so in plain
 *       words rather than scanning the world; erasure reaches them all through the tombstone.</li>
 *   <li><b>Beam link session</b>: the first endpoint the player picked, if a link is half made right
 *       now (dimension and block position, both the player's own), with the ticks left before it lapses.</li>
 *   <li><b>Fission core owner</b> is NeroTech's record and is exported by NeroTech; scorch zones hold no
 *       player data.</li>
 * </ol>
 *
 * <p>Reads only, on the server thread; never logs the player and never writes the export anywhere.
 */
public final class NeroPowerDataExport {

    /** What the export says about beam link ownership, which lives on the linked blocks themselves. */
    public static final String BEAM_LINKS_NOTE = "Beam Transmitters and Relays you linked store your player id "
            + "on the block itself. NeroPower keeps no list of them; erasing your data removes it from each one.";

    private NeroPowerDataExport() {
    }

    /** Register with Core's shared export. Call once from common init. */
    public static void register() {
        PlayerDataExport.register(NeroPowerCommon.MOD_ID, NeroPowerDataExport::export);
    }

    static JsonElement export(MinecraftServer server, UUID player) {
        return exportWith(BeamLinkSession.INSTANCE, player, server.overworld().getGameTime());
    }

    /** The Minecraft-free core, bound to an explicit session store so it can be tested on its own. */
    public static JsonObject exportWith(BeamLinkSession session, UUID player, long now) {
        JsonObject out = new JsonObject();
        out.addProperty("beamLinks", BEAM_LINKS_NOTE);
        Optional<BeamLinkSession.Pending> pending = session.peek(player, now);
        pending.ifPresent(p -> {
            JsonObject link = new JsonObject();
            link.addProperty("dimension", p.dimension());
            link.addProperty("x", p.x());
            link.addProperty("y", p.y());
            link.addProperty("z", p.z());
            link.addProperty("ticksLeft", Math.max(0L, p.expiresAt() - now));
            out.add("pendingBeamLink", link);
        });
        return out;
    }
}

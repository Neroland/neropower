package za.co.neroland.neropower.link;

import java.util.Optional;
import java.util.UUID;

/**
 * The Minecraft-free scoping rules behind {@link NeroPowerLinkModule}'s snapshots (POPIA/GDPR),
 * kept as pure statics so they are unit-tested exactly as the module applies them:
 * <ul>
 *   <li><b>Proximity</b> — a snapshot only ever enumerates machines in the requesting <i>online</i>
 *       player's dimension within {@value #PROXIMITY_BLOCKS} blocks of them, in loaded chunks. There
 *       is no server-wide roster; a player who is offline sees nothing.</li>
 *   <li><b>Owner</b> — a machine that recorded an owner (a beam transmitter / relay's link owner;
 *       a fission core's NeroTech owner, only set when per-player attribution is on) is shown only
 *       to that owner. A machine with no recorded owner is shown by proximity alone.</li>
 * </ul>
 */
public final class LinkScope {

    /** Proximity radius (blocks) around the requesting player. */
    public static final int PROXIMITY_BLOCKS = 128;

    /** The machine-id prefix every NeroPower failure-channel scope starts with. */
    public static final String SCOPE_PREFIX = "neropower:";

    private LinkScope() {
    }

    /** Whether {@code (x,y,z)} lies within {@link #PROXIMITY_BLOCKS} (Euclidean) of {@code (px,py,pz)}. */
    public static boolean withinProximity(int px, int py, int pz, int x, int y, int z) {
        long dx = (long) x - px;
        long dy = (long) y - py;
        long dz = (long) z - pz;
        return dx * dx + dy * dy + dz * dz <= (long) PROXIMITY_BLOCKS * PROXIMITY_BLOCKS;
    }

    /**
     * Whether a machine with {@code owner} (empty = none recorded) may appear in {@code requester}'s
     * snapshot: unowned machines are proximity-only, owned ones are the owner's alone.
     */
    public static boolean visibleTo(Optional<UUID> owner, UUID requester) {
        return owner.isEmpty() || owner.get().equals(requester);
    }

    /** Whether {@code requester} may act (acknowledge, SCRAM) on a machine with {@code owner}: owner only. */
    public static boolean mayAct(Optional<UUID> owner, UUID requester) {
        return owner.isPresent() && owner.get().equals(requester);
    }

    /** The chunk radius that covers {@link #PROXIMITY_BLOCKS} blocks (16-block chunks, rounded up). */
    public static int chunkRadius() {
        return (PROXIMITY_BLOCKS + 15) / 16;
    }

    /** Whether a NeroTech failure-channel scope ({@code <machineId>@<dim>:<pos>}) names a NeroPower machine. */
    public static boolean isNeroPowerScope(String scope) {
        return scope != null && scope.startsWith(SCOPE_PREFIX);
    }

    /** The {@code machineId} part of a failure-channel scope, or the whole string when it has no {@code @}. */
    public static String machineIdOf(String scope) {
        if (scope == null) {
            return "";
        }
        int at = scope.indexOf('@');
        return at < 0 ? scope : scope.substring(0, at);
    }
}

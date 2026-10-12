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
 *   <li><b>Actions</b> ({@link #mayAct}) — an owned machine obeys its owner only; an unowned one
 *       obeys an online player standing in its dimension within {@value #PROXIMITY_BLOCKS} blocks
 *       who also passes NeroPower's protection seam ({@code mayInteract}) — the same person who
 *       could walk up and do it by hand.</li>
 *   <li><b>Failure events</b> — go to the machine's owner only; an unowned machine's events go only
 *       to online players within proximity of it (resolved by the module from the machine itself,
 *       never from a published scope). Never a server-wide broadcast. Their scope
 *       ({@link #failureScope}) names a machine type and a dimension — never a position: a base's
 *       coordinates identify its builder.</li>
 * </ul>
 */
public final class LinkScope {

    /** Proximity radius (blocks) around the requesting player. */
    public static final int PROXIMITY_BLOCKS = 128;

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

    /** Whether {@code requester} is the machine's recorded owner (empty owner = nobody is). */
    public static boolean isOwner(Optional<UUID> owner, UUID requester) {
        return owner.isPresent() && owner.get().equals(requester);
    }

    /**
     * Whether {@code requester} may act (acknowledge, SCRAM) on a machine with {@code owner}:
     * <ul>
     *   <li>owned → the owner only, wherever they stand;</li>
     *   <li>unowned (NeroTech's opt-in attribution off at placement) → only when the requester is
     *       online, in the machine's dimension and within {@value #PROXIMITY_BLOCKS} blocks
     *       ({@code inProximity}, computed by the caller) <i>and</i> passes the protection seam
     *       ({@code mayInteract}).</li>
     * </ul>
     */
    public static boolean mayAct(Optional<UUID> owner, UUID requester, boolean inProximity, boolean mayInteract) {
        if (requester == null) {
            return false;
        }
        if (owner.isPresent()) {
            return owner.get().equals(requester);
        }
        return inProximity && mayInteract;
    }

    /** The chunk radius that covers {@link #PROXIMITY_BLOCKS} blocks (16-block chunks, rounded up). */
    public static int chunkRadius() {
        return (PROXIMITY_BLOCKS + 15) / 16;
    }

    /**
     * The scope a NeroLink {@code failure} event carries: {@code <machineId>@<dimensionId>}, e.g.
     * {@code neropower:fission_core@minecraft:overworld} — the same shape as NeroTech's
     * {@code nerotech:machine_failure} channel. No player and no position.
     */
    public static String failureScope(String machineId, String dimensionId) {
        return machineId + "@" + dimensionId;
    }
}

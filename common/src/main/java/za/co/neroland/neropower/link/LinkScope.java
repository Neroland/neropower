package za.co.neroland.neropower.link;

import java.util.Optional;
import java.util.UUID;

/**
 * The Minecraft-free scoping rules behind {@link NeroPowerLinkModule}'s snapshots (POPIA/GDPR),
 * kept as pure statics so they are unit-tested exactly as the module applies them:
 * <ul>
 *   <li><b>Proximity</b> — a snapshot only ever enumerates machines in the requesting <i>online</i>
 *       player's dimension, in loaded chunks within {@value #PROXIMITY_CHUNKS} chunks of theirs
 *       (Chebyshev, whole columns, about {@value #PROXIMITY_BLOCKS} blocks). Reach is decided on the
 *       chunk grid, never by exact distance, so the edge moves in 16-block steps and has no height:
 *       a machine cannot be placed more finely than its chunk by walking the edge.
 *       There is no server-wide roster; a player who is offline sees nothing.</li>
 *   <li><b>Owner</b> — a machine that recorded an owner (a beam transmitter / relay's link owner;
 *       a fission core's NeroTech owner, only set when per-player attribution is on) is shown only
 *       to that owner. A machine with no recorded owner is shown by proximity alone.</li>
 *   <li><b>Actions</b> ({@link #mayAct}) — remote control needs ownership: an owned machine obeys
 *       its owner only, and an unowned machine obeys nobody remotely. It can be used in person (its
 *       screen, the SCRAM Key).</li>
 *   <li><b>Failure events</b> — go to online players within proximity of the machine, filtered by the
 *       owner rule, plus a push-eligible copy for a recorded opt-in owner (see {@link PlantEvents}). Never
 *       a server-wide broadcast, and since link schema 2 the body names neither a position nor a
 *       dimension. {@link #failureScope} remains the NeroTech bus scope shape only.</li>
 * </ul>
 */
public final class LinkScope {

    /** Nominal proximity radius (blocks) around the requesting player: {@link #PROXIMITY_CHUNKS} chunks. */
    public static final int PROXIMITY_BLOCKS = 128;

    /** Reach on the chunk grid (Chebyshev, chunks): the machine's chunk within this many of the requester's. */
    public static final int PROXIMITY_CHUNKS = PROXIMITY_BLOCKS / 16;

    /** The "near" band (Chebyshev, chunks): about 32 blocks. */
    public static final int NEAR_CHUNKS = 2;

    /** Band names: the only distance information an entry carries. */
    public static final String BAND_NEAR = "near";
    public static final String BAND_IN_RANGE = "in_range";

    private LinkScope() {
    }

    /**
     * Whether a machine at {@code (x,y,z)} is in reach of a requester at {@code (px,py,pz)}: its chunk lies
     * within {@link #PROXIMITY_CHUNKS} chunks of the requester's (Chebyshev). Heights are ignored on purpose,
     * and two requesters in the same chunk always get the same answer.
     */
    public static boolean withinProximity(int px, int py, int pz, int x, int y, int z) {
        return chunkDistance(px, pz, x, z) <= PROXIMITY_CHUNKS;
    }

    /** Chebyshev distance in chunks between the chunks holding {@code (ax,az)} and {@code (bx,bz)}. */
    public static int chunkDistance(int ax, int az, int bx, int bz) {
        return Math.max(Math.abs((ax >> 4) - (bx >> 4)), Math.abs((az >> 4) - (bz >> 4)));
    }

    /** The coarse band of an in-reach machine: {@link #BAND_NEAR} within {@link #NEAR_CHUNKS} chunks, else in range. */
    public static String band(int px, int pz, int x, int z) {
        return chunkDistance(px, pz, x, z) <= NEAR_CHUNKS ? BAND_NEAR : BAND_IN_RANGE;
    }

    /**
     * The recorded owner used for every scoping rule (link sections, actions, events, map layers): the beam
     * link owner where NeroPower recorded one, otherwise the NeroTech machine owner (only set when NeroTech's
     * per-player attribution is on). Compared, never emitted.
     */
    public static Optional<UUID> ownerOf(Optional<UUID> linkOwner, Optional<UUID> machineOwner) {
        return linkOwner.isPresent() ? linkOwner : machineOwner;
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
     * Whether {@code requester} may act remotely (acknowledge, SCRAM, turbine trip) on a machine with
     * {@code owner}: only its recorded owner. An unowned machine accepts no remote action; it can be used in
     * person through its screen or the SCRAM Key (no control through walls from range).
     */
    public static boolean mayAct(Optional<UUID> owner, UUID requester) {
        return requester != null && owner.isPresent() && owner.get().equals(requester);
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

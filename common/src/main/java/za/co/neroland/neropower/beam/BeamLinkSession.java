package za.co.neroland.neropower.beam;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The server-side "first endpoint picked" memory for beam linking, keyed by the linking player's
 * UUID: crouch-use a transmitter (or relay) with the Configurator to store it here, then use a
 * receiver (or relay) to complete the link. <b>Transient by design</b> — a plain in-memory map
 * that is never persisted, never synced and never logged; an entry lives at most
 * {@value #TIMEOUT_TICKS} ticks and is dropped on the first touch after that (POPIA/GDPR: a
 * player UUID held for thirty seconds of an interaction, nothing more). Minecraft-free so it is
 * unit-testable; the game uses the shared {@link #INSTANCE} from the server thread only.
 *
 * <p>Integrators may also call {@link #clear(UUID)} from a logout hook; the timeout alone already
 * guarantees nothing outlives the interaction.
 */
public final class BeamLinkSession {

    /** How long a pending source stays valid (600 ticks = 30 s). */
    public static final long TIMEOUT_TICKS = 600L;

    /** The game's one session map (server thread only). */
    public static final BeamLinkSession INSTANCE = new BeamLinkSession();

    /**
     * A pending source endpoint: block position + dimension id (world data) and the game time at
     * which it expires.
     */
    public record Pending(String dimension, int x, int y, int z, long expiresAt) {

        /** Whether this entry is still valid at {@code now}. */
        public boolean live(long now) {
            return now < this.expiresAt;
        }

        /** Whether this entry names the block at {@code (x,y,z)} in {@code dimension}. */
        public boolean at(String dimension, int x, int y, int z) {
            return this.x == x && this.y == y && this.z == z && this.dimension.equals(dimension);
        }
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    /** Fresh, empty session map (tests build their own; the game uses {@link #INSTANCE}). */
    public BeamLinkSession() {
    }

    /** Remember {@code (x,y,z)} in {@code dimension} as {@code player}'s pending source, from {@code now}. */
    public Pending begin(UUID player, String dimension, int x, int y, int z, long now) {
        purge(now);
        Pending entry = new Pending(dimension, x, y, z, now + TIMEOUT_TICKS);
        this.pending.put(player, entry);
        return entry;
    }

    /** The player's live pending source at {@code now}, without consuming it. */
    public Optional<Pending> peek(UUID player, long now) {
        purge(now);
        return Optional.ofNullable(this.pending.get(player));
    }

    /** The player's live pending source at {@code now}, consumed (removed) on return. */
    public Optional<Pending> take(UUID player, long now) {
        purge(now);
        return Optional.ofNullable(this.pending.remove(player));
    }

    /** Drop the player's pending source, if any (also the hook for a logout). */
    public void clear(UUID player) {
        this.pending.remove(player);
    }

    /** Drop every entry that has expired by {@code now}. Returns how many were dropped. */
    public int purge(long now) {
        int dropped = 0;
        Iterator<Map.Entry<UUID, Pending>> it = this.pending.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().live(now)) {
                it.remove();
                dropped++;
            }
        }
        return dropped;
    }

    /** Number of entries held (expired ones included until the next purge). */
    public int size() {
        return this.pending.size();
    }

    /** Forget everything (server stop / world unload). */
    public void reset() {
        this.pending.clear();
    }
}

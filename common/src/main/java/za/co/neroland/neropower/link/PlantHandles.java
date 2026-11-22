package za.co.neroland.neropower.link;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The opaque per-request handles a plant section hands out (link schema 2).
 *
 * <p>Each section answer replaces the requester's previous table <i>for that section</i>: a fresh
 * random nonce plus the targets in response order. A handle is {@code <nonce>-<index base36>}; it
 * resolves only for the requester whose response minted it, only while that response is the
 * requester's latest for its section, and only for {@link #TTL_MS}. The targets (dimension + position)
 * never leave the server: the client sees the handle and nothing it could turn back into a place.
 * Held in memory only, dropped on logout and on erasure.
 *
 * <p>One table per section (not one per requester) so the app can read {@code fission},
 * {@code turbines} and {@code storage} in turn and still act on any of them.
 *
 * @param <T> the server-side target (a dimension + block position in production, anything in tests)
 */
final class PlantHandles<T> {

    /** How long a handle stays valid after its response was built. */
    static final long TTL_MS = 30_000L;

    private record Issue<T>(String nonce, long issuedAt, List<T> targets) {
    }

    private final Map<UUID, Map<String, Issue<T>>> latest = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private final SecureRandom random = new SecureRandom();

    PlantHandles(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Mint handles for one section response, replacing the requester's previous table for it.
     *
     * @return one handle per target, in the same order
     */
    List<String> issue(UUID requester, String section, List<T> targets) {
        String nonce = Long.toHexString(this.random.nextLong() & 0xFFFFFFFFFFFFL);
        List<T> copy = List.copyOf(targets);
        this.latest.computeIfAbsent(requester, k -> new ConcurrentHashMap<>())
                .put(section, new Issue<>(nonce, this.clock.getAsLong(), copy));
        List<String> handles = new ArrayList<>(copy.size());
        for (int i = 0; i < copy.size(); i++) {
            handles.add(nonce + "-" + Integer.toString(i, 36));
        }
        return handles;
    }

    /**
     * The target behind {@code handle}, or empty when the handle is malformed, expired, superseded or
     * was minted for another requester. Every failure looks the same to the caller.
     */
    Optional<T> resolve(UUID requester, String handle) {
        if (requester == null || handle == null) {
            return Optional.empty();
        }
        Map<String, Issue<T>> tables = this.latest.get(requester);
        if (tables == null) {
            return Optional.empty();
        }
        int dash = handle.lastIndexOf('-');
        if (dash <= 0 || dash == handle.length() - 1) {
            return Optional.empty();
        }
        String nonce = handle.substring(0, dash);
        String tail = handle.substring(dash + 1);
        long now = this.clock.getAsLong();
        for (Issue<T> issue : tables.values()) {
            if (!issue.nonce().equals(nonce)) {
                continue;
            }
            if (now - issue.issuedAt() > TTL_MS) {
                return Optional.empty();
            }
            int index;
            try {
                index = Integer.parseInt(tail, 36);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (index < 0 || index >= issue.targets().size() || !Integer.toString(index, 36).equals(tail)) {
                return Optional.empty();
            }
            return Optional.of(issue.targets().get(index));
        }
        return Optional.empty();
    }

    /** Drop one player's tables (logout, erasure, or an answer for an offline requester). */
    void forget(UUID player) {
        this.latest.remove(player);
    }

    /** Drop every table whose player is not in {@code online}. */
    void retainOnly(Set<UUID> online) {
        this.latest.keySet().retainAll(online);
    }

    /** Whether a table is held for {@code player} (erasure probe). */
    boolean holds(UUID player) {
        return this.latest.containsKey(player);
    }
}

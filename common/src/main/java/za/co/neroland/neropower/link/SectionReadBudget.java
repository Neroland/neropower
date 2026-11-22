package za.co.neroland.neropower.link;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;

import com.google.gson.JsonObject;

/**
 * The read budget for plant sections: at most {@link #MAX_READS} fresh
 * answers per player per section per sliding {@link #WINDOW_MS}. A read over the budget gets the last
 * fresh answer again (same handles, nothing re-scanned), so polling faster than the app's ten-second
 * cadence buys no extra positions to trilaterate from. A player with no earlier answer gets nothing.
 *
 * <p>In memory only, keyed by the requester's UUID and the section id. Dropped on erasure
 * ({@link #forget}); windows age out after a minute and a logged-out player's cached answers go with
 * the module's five-second sweep ({@link #retainOnly}). Never saved, synced or logged.
 */
final class SectionReadBudget {

    static final int MAX_READS = 6;
    static final long WINDOW_MS = 60_000L;

    private record Key(UUID player, String section) {
    }

    private final Map<Key, Deque<Long>> reads = new HashMap<>();
    private final Map<Key, JsonObject> last = new HashMap<>();
    private final LongSupplier clock;

    SectionReadBudget(LongSupplier clock) {
        this.clock = clock;
    }

    /** Spend one fresh read; false (nothing spent) when this player's budget for {@code section} is used up. */
    synchronized boolean tryRead(UUID player, String section) {
        if (player == null) {
            return false;
        }
        long now = this.clock.getAsLong();
        Deque<Long> times = this.reads.computeIfAbsent(new Key(player, section), k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.pollFirst();
        }
        if (times.size() >= MAX_READS) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    /** Remember the fresh answer that a later over-budget read repeats. */
    synchronized void remember(UUID player, String section, JsonObject answer) {
        if (player != null && answer != null) {
            this.last.put(new Key(player, section), answer.deepCopy());
        }
    }

    /** The last fresh answer for this player and section, if any. */
    synchronized Optional<JsonObject> lastAnswer(UUID player, String section) {
        JsonObject answer = player == null ? null : this.last.get(new Key(player, section));
        return answer == null ? Optional.empty() : Optional.of(answer.deepCopy());
    }

    /** Drop everything held for {@code player} (erasure, or the requester went offline). */
    synchronized void forget(UUID player) {
        this.reads.keySet().removeIf(k -> k.player().equals(player));
        this.last.keySet().removeIf(k -> k.player().equals(player));
    }

    /** Drop cached answers of players not online, and windows that can no longer refuse a read. */
    synchronized void retainOnly(java.util.Set<UUID> online) {
        long now = this.clock.getAsLong();
        this.last.keySet().removeIf(k -> !online.contains(k.player()));
        this.reads.values().removeIf(times -> times.isEmpty() || now - times.peekLast() >= WINDOW_MS);
    }

    /** Whether anything is held for {@code player} (erasure probe). */
    synchronized boolean holds(UUID player) {
        return this.reads.keySet().stream().anyMatch(k -> k.player().equals(player))
                || this.last.keySet().stream().anyMatch(k -> k.player().equals(player));
    }
}

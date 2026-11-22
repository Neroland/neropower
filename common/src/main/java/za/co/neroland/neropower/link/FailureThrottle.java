package za.co.neroland.neropower.link;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * At most {@link #MAX_EVENTS} {@code failure} events per machine per {@link #WINDOW_MS} (link schema 2).
 * Keyed by an in-process machine key (dimension + packed block position) — world data, never a player —
 * held in memory only and never sent anywhere. Bounded: windows older than the window are pruned.
 */
final class FailureThrottle {

    static final int MAX_EVENTS = 5;
    static final long WINDOW_MS = 60_000L;
    /** Hard cap on tracked machines, so a pathological server cannot grow this without bound. */
    static final int MAX_TRACKED = 4_096;

    private final Map<String, Deque<Long>> fired = new HashMap<>();
    private final LongSupplier clock;

    FailureThrottle(LongSupplier clock) {
        this.clock = clock;
    }

    /** Whether one more event may go out for {@code machineKey} now; records it if so. */
    synchronized boolean tryFire(String machineKey) {
        long now = this.clock.getAsLong();
        if (this.fired.size() >= MAX_TRACKED) {
            prune(now);
        }
        Deque<Long> times = this.fired.computeIfAbsent(machineKey, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= WINDOW_MS) {
            times.pollFirst();
        }
        if (times.size() >= MAX_EVENTS) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    synchronized void pruneExpired() {
        prune(this.clock.getAsLong());
    }

    private void prune(long now) {
        this.fired.values().removeIf(times -> times.isEmpty() || now - times.peekLast() >= WINDOW_MS);
    }

    synchronized int tracked() {
        return this.fired.size();
    }
}

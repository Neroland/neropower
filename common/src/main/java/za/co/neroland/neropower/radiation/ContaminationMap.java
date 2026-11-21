package za.co.neroland.neropower.radiation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Contamination per chunk for one dimension: packed chunk position ({@code ChunkPos.toLong()}) to a
 * level 0..{@value #MAX_LEVEL}. Pure and deterministic (sorted keys, no clock of its own): decay runs
 * as a pass started every {@value #DECAY_PERIOD_TICKS} ticks and processed round-robin within a
 * per-tick budget; pruning drops the lowest levels first (ties: lowest key). World data only: no
 * owner, no player.
 */
public final class ContaminationMap {

    /** Highest level a chunk holds. */
    public static final int MAX_LEVEL = 1000;
    /** A decay pass starts every minute of game time. */
    public static final int DECAY_PERIOD_TICKS = 1200;

    private final NavigableMap<Long, Short> levels = new TreeMap<>();
    /** The pass in progress: next key to visit, or null when no pass is running. */
    private Long cursor;
    private boolean passRunning;

    /** The level of a chunk (0 when clean). */
    public int level(long chunk) {
        Short v = this.levels.get(chunk);
        return v == null ? 0 : v;
    }

    /** Add (or, negative, remove) contamination; clamps to 0..MAX; a chunk at 0 is dropped. Returns the new level. */
    public int add(long chunk, int amount) {
        int now = Math.max(0, Math.min(MAX_LEVEL, level(chunk) + amount));
        if (now == 0) {
            this.levels.remove(chunk);
        } else {
            this.levels.put(chunk, (short) now);
        }
        return now;
    }

    /** Chunks holding contamination. */
    public int size() {
        return this.levels.size();
    }

    /** An immutable sorted copy (persistence, tests). */
    public Map<Long, Short> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(this.levels));
    }

    /** Drop every chunk inside a set (e.g. the gallery footprint). Returns how many were removed. */
    public int clear(Iterable<Long> chunks) {
        int removed = 0;
        for (Long chunk : chunks) {
            if (this.levels.remove(chunk) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** How much one decay step takes from {@code level}: max(1, level x permille / 1000); 0 when permille is 0. */
    public static int decayAmount(int level, int permille) {
        if (permille <= 0 || level <= 0) {
            return 0;
        }
        return Math.max(1, (int) ((long) level * permille / 1000L));
    }

    /**
     * One server tick of decay. A pass starts when {@code gameTime} is a multiple of the period; each
     * tick then decays at most {@code budget} chunks in key order until every chunk has decayed once.
     *
     * @return chunks changed this tick
     */
    public int decayTick(long gameTime, int permille, int budget) {
        if (gameTime % DECAY_PERIOD_TICKS == 0) {
            this.passRunning = true;
            this.cursor = null;
        }
        if (!this.passRunning || permille <= 0) {
            return 0;
        }
        NavigableMap<Long, Short> rest = this.cursor == null ? this.levels : this.levels.tailMap(this.cursor, true);
        Iterator<Map.Entry<Long, Short>> it = rest.entrySet().iterator();
        int changed = 0;
        int visited = 0;
        while (it.hasNext() && visited < Math.max(1, budget)) {
            Map.Entry<Long, Short> e = it.next();
            int next = e.getValue() - decayAmount(e.getValue(), permille);
            if (next <= 0) {
                it.remove();
            } else {
                e.setValue((short) next);
            }
            changed++;
            visited++;
        }
        if (it.hasNext()) {
            this.cursor = it.next().getKey();
        } else {
            this.passRunning = false;
            this.cursor = null;
        }
        return changed;
    }

    /** Drop the lowest entries until at most {@code cap} remain (ties: lowest key first). Returns how many. */
    public int prune(int cap) {
        int excess = this.levels.size() - Math.max(0, cap);
        if (excess <= 0) {
            return 0;
        }
        List<Map.Entry<Long, Short>> order = new ArrayList<>(this.levels.entrySet());
        order.sort(Map.Entry.<Long, Short>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
        for (int i = 0; i < excess; i++) {
            this.levels.remove(order.get(i).getKey());
        }
        return excess;
    }

    /**
     * Up to {@code limit} chunks at or above {@code min}, in key order starting after {@code after}
     * (exclusive; null = from the start) and wrapping round once. Used by the round-robin effect pass.
     */
    public List<Long> nextAtLeast(Long after, int min, int limit) {
        List<Long> out = new ArrayList<>();
        if (limit <= 0 || this.levels.isEmpty()) {
            return out;
        }
        NavigableMap<Long, Short> first = after == null ? this.levels : this.levels.tailMap(after, false);
        collect(first, min, limit, out);
        if (out.size() < limit && after != null) {
            collect(this.levels.headMap(after, true), min, limit, out);
        }
        return out;
    }

    private static void collect(NavigableMap<Long, Short> range, int min, int limit, List<Long> out) {
        for (Map.Entry<Long, Short> e : range.entrySet()) {
            if (out.size() >= limit) {
                return;
            }
            if (e.getValue() >= min) {
                out.add(e.getKey());
            }
        }
    }

    /** Load persisted entries (levels clamped; zero and negative dropped). */
    public void load(Map<Long, Short> entries) {
        this.levels.clear();
        entries.forEach((chunk, level) -> {
            int v = Math.min(MAX_LEVEL, level);
            if (v > 0) {
                this.levels.put(chunk, (short) v);
            }
        });
    }
}

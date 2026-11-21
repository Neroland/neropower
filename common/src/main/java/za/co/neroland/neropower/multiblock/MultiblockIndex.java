package za.co.neroland.neropower.multiblock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory index of formed NeroPower multiblocks, per dimension, so a change to <b>any</b> member block
 * marks its controller dirty (the controller itself only hears its own six faces). Never saved: a
 * controller registers when it forms (and on its first tick after load), unregisters when it unforms,
 * is removed or its chunk unloads. Also holds the per-dimension revalidation budget
 * ({@value #REVALIDATIONS_PER_TICK} per tick; the rest wait a tick with their dirty flag kept).
 * Server thread only; the dimension key is the level's dimension key. World data, no player data.
 */
public final class MultiblockIndex {

    /** Revalidations allowed per dimension per tick. */
    public static final int REVALIDATIONS_PER_TICK = 4;

    private record Entry(Bounds bounds, Runnable onDirty) {
    }

    private static final Map<Object, Map<Long, Entry>> BY_DIMENSION = new HashMap<>();
    private static final Map<Object, long[]> BUDGET = new HashMap<>();

    private MultiblockIndex() {
    }

    /** Register (or replace) the structure whose controller is at {@code controller} (packed position). */
    public static synchronized void register(Object dimension, long controller, Bounds bounds, Runnable onDirty) {
        BY_DIMENSION.computeIfAbsent(dimension, d -> new HashMap<>()).put(controller, new Entry(bounds, onDirty));
    }

    /** Forget a controller's structure. */
    public static synchronized void unregister(Object dimension, long controller) {
        Map<Long, Entry> entries = BY_DIMENSION.get(dimension);
        if (entries != null) {
            entries.remove(controller);
            if (entries.isEmpty()) {
                BY_DIMENSION.remove(dimension);
            }
        }
    }

    /** Whether a controller is registered. */
    public static synchronized boolean registered(Object dimension, long controller) {
        Map<Long, Entry> entries = BY_DIMENSION.get(dimension);
        return entries != null && entries.containsKey(controller);
    }

    /** Mark every structure whose bounds contain the position dirty; returns how many were marked. */
    public static int markDirty(Object dimension, int x, int y, int z) {
        List<Runnable> hits = new ArrayList<>(2);
        synchronized (MultiblockIndex.class) {
            Map<Long, Entry> entries = BY_DIMENSION.get(dimension);
            if (entries == null) {
                return 0;
            }
            for (Entry entry : entries.values()) {
                if (entry.bounds().contains(x, y, z)) {
                    hits.add(entry.onDirty());
                }
            }
        }
        hits.forEach(Runnable::run); // outside the lock: a host only sets a flag
        return hits.size();
    }

    /** Take one revalidation from this dimension's budget for {@code gameTime}; false when spent. */
    public static synchronized boolean tryRevalidate(Object dimension, long gameTime) {
        long[] budget = BUDGET.computeIfAbsent(dimension, d -> new long[] {Long.MIN_VALUE, 0});
        if (budget[0] != gameTime) {
            budget[0] = gameTime;
            budget[1] = 0;
        }
        if (budget[1] >= REVALIDATIONS_PER_TICK) {
            return false;
        }
        budget[1]++;
        return true;
    }

    /** Drop everything (server stop, tests). */
    public static synchronized void clear() {
        BY_DIMENSION.clear();
        BUDGET.clear();
    }
}

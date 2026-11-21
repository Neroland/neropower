package za.co.neroland.neropower.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /neropower budget}: server tick time spent in NeroPower machines, per block-entity type and in
 * total. Sampling is cheap and opt-in: the clock is read only while a window opened by the command is
 * running (a volatile flag check otherwise), and the window closes itself after its tick count. Keys
 * are block-entity type ids; no positions, owners or players are recorded.
 */
public final class BudgetProbe {

    /** Default window: 10 seconds. */
    public static final int DEFAULT_TICKS = 200;
    /** Longest window: 5 minutes. */
    public static final int MAX_TICKS = 6_000;

    /** One row of a report. */
    public record Row(String type, double msPerTick, double callsPerTick) {
    }

    /** A report: rows sorted by cost (highest first), the total, ticks sampled and whether still running. */
    public record Report(List<Row> rows, double totalMsPerTick, int ticks, boolean running) {
    }

    private static volatile boolean sampling;
    private static int ticksLeft;
    private static int ticksSampled;
    private static final Map<String, long[]> BY_TYPE = new TreeMap<>();

    private BudgetProbe() {
    }

    /** Whether a window is open (the only check a machine tick pays when idle). */
    public static boolean sampling() {
        return sampling;
    }

    /** Open a fresh window of {@code ticks} server ticks (clamped 1..{@value #MAX_TICKS}). */
    public static synchronized void start(int ticks) {
        BY_TYPE.clear();
        ticksSampled = 0;
        ticksLeft = Math.max(1, Math.min(MAX_TICKS, ticks));
        sampling = true;
    }

    /** Record one machine tick of {@code nanos} for a block-entity type id. */
    public static synchronized void record(String type, long nanos) {
        if (!sampling) {
            return;
        }
        long[] acc = BY_TYPE.computeIfAbsent(type, t -> new long[2]);
        acc[0] += Math.max(0L, nanos);
        acc[1]++;
    }

    /** Count one server tick of the window; closes it when done. Called once per server tick. */
    public static synchronized void serverTick() {
        if (!sampling) {
            return;
        }
        ticksSampled++;
        if (--ticksLeft <= 0) {
            sampling = false;
        }
    }

    /** The current (or last) window's numbers. */
    public static synchronized Report report() {
        List<Row> rows = new ArrayList<>();
        double total = 0D;
        int ticks = Math.max(1, ticksSampled);
        for (Map.Entry<String, long[]> e : BY_TYPE.entrySet()) {
            double ms = e.getValue()[0] / 1_000_000D / ticks;
            rows.add(new Row(e.getKey(), ms, e.getValue()[1] / (double) ticks));
            total += ms;
        }
        rows.sort((a, b) -> Double.compare(b.msPerTick(), a.msPerTick()));
        return new Report(List.copyOf(rows), total, ticksSampled, sampling);
    }
}

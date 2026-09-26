package za.co.neroland.neropower.beam;

import java.util.List;
import java.util.function.Predicate;

/**
 * The beamed-power arithmetic, Minecraft-free so it is unit-testable: distance loss, the fixed
 * orbital-hop loss, the source-side cost of what a target actually accepted, and the line-of-sight
 * verdict over a {@link BeamPath} walk. Every function is pure; the block entities only feed it
 * config values, buffer sizes and per-cell facts.
 *
 * <p>Loss model: a beam keeps {@code (1 - lossPermillePerBlock / 1000) ^ distance} of what leaves the
 * transmitter, so a 3‰-per-block beam over 100 blocks delivers about 74% and over the 128-block
 * default range about 68%. An orbital hop (cross-dimension, no line of sight) pays a single fixed
 * {@code hopLossPermille} instead of any distance term.
 */
public final class BeamMath {

    public static final int PERMILLE = 1_000;

    private BeamMath() {
    }

    /**
     * The fraction of energy that survives {@code distance} blocks at {@code lossPermillePerBlock}
     * loss per block, in {@code [0, 1]}. Zero distance or zero loss keeps everything; a loss of 1000‰
     * or more per block delivers nothing beyond the source cell.
     */
    public static double lossFactor(double distance, int lossPermillePerBlock) {
        if (distance <= 0.0) {
            return 1.0;
        }
        int loss = clampPermille(lossPermillePerBlock);
        if (loss == 0) {
            return 1.0;
        }
        if (loss >= PERMILLE) {
            return 0.0;
        }
        double kept = 1.0 - loss / (double) PERMILLE;
        return Math.pow(kept, distance);
    }

    /** The fraction that survives one orbital hop: {@code 1 - hopLossPermille / 1000}. */
    public static double orbitalFactor(int hopLossPermille) {
        return 1.0 - clampPermille(hopLossPermille) / (double) PERMILLE;
    }

    /**
     * How much the target receives when {@code sent} NE leaves the source under {@code factor}
     * (floored, never negative). A factor of 0 delivers nothing; a factor of 1 delivers it all.
     */
    public static long delivered(long sent, double factor) {
        if (sent <= 0L || factor <= 0.0) {
            return 0L;
        }
        if (factor >= 1.0) {
            return sent;
        }
        return (long) Math.floor(sent * factor);
    }

    /**
     * What the source must give up so that the target keeps {@code accepted} NE under {@code factor}
     * — the inverse of {@link #delivered}, rounded up (the beam never gives energy away for free) and
     * capped at {@code offered}, the amount the source put on the table this pass.
     */
    public static long sourceCost(long accepted, double factor, long offered) {
        if (accepted <= 0L || offered <= 0L) {
            return 0L;
        }
        if (factor <= 0.0) {
            return 0L; // nothing could have been delivered, so nothing is owed
        }
        // The epsilon absorbs binary rounding (700 / 0.7 = 1000.0000000000001) so an exact division
        // is not charged an extra unit.
        long cost = factor >= 1.0 ? accepted : (long) Math.ceil(accepted / factor - 1e-9);
        return Math.min(Math.max(cost, accepted), offered);
    }

    /** The per-pass budget: at most {@code perPass}, never more than the source holds, never negative. */
    public static long budget(long perPass, long stored) {
        if (perPass <= 0L || stored <= 0L) {
            return 0L;
        }
        return Math.min(perPass, stored);
    }

    /** Euclidean distance between two block centres (integer block coordinates). */
    public static double distance(int x0, int y0, int z0, int x1, int y1, int z1) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Whether the two block positions lie within {@code range} blocks of each other (inclusive). */
    public static boolean withinRange(int x0, int y0, int z0, int x1, int y1, int z1, int range) {
        if (range < 0) {
            return false;
        }
        long dx = x1 - x0;
        long dy = y1 - y0;
        long dz = z1 - z0;
        return dx * dx + dy * dy + dz * dz <= (long) range * range;
    }

    /** The whole-beam loss as permille, for the GUI readout ({@code 0} lossless, {@code 1000} dark). */
    public static int lossPermille(double factor) {
        double f = Math.max(0.0, Math.min(1.0, factor));
        return (int) Math.round((1.0 - f) * PERMILLE);
    }

    // --- line of sight ---------------------------------------------------------------------

    /**
     * Whether one cell on the walk blocks the beam: only a <b>loaded</b>, non-air cell that is not
     * itself a beam endpoint (a relay standing in the line does not shadow a beam passing it).
     * Unloaded cells are never inspected — checking would load the chunk — so they never block.
     */
    public static boolean blocks(boolean loaded, boolean air, boolean beamEndpoint) {
        return loaded && !air && !beamEndpoint;
    }

    /**
     * Whether a beam along {@code path} ({@link BeamPath#cellsBetween} — both endpoints already
     * excluded) is clear: no cell {@code blocked} accepts. An empty path (adjacent endpoints) is
     * always clear. Short-circuits on the first blocked cell.
     */
    public static boolean clear(List<BeamPath.Cell> path, Predicate<BeamPath.Cell> blocked) {
        for (BeamPath.Cell cell : path) {
            if (blocked.test(cell)) {
                return false;
            }
        }
        return true;
    }

    private static int clampPermille(int permille) {
        return Math.max(0, Math.min(PERMILLE, permille));
    }
}

package za.co.neroland.neropower.beam;

import java.util.ArrayList;
import java.util.List;

/**
 * The line-of-sight voxel walk for a beam, Minecraft-free so it is unit-testable: an
 * Amanatides–Woo DDA from the centre of one block to the centre of another, yielding every block
 * cell the segment passes through <b>between</b> the two endpoints (both endpoints excluded — they
 * are the transmitter and the receiver themselves). The block entity then asks the level whether
 * each cell is clear.
 *
 * <p>Ties (the segment leaving a cell exactly through an edge or corner, as every axis-diagonal
 * does) are broken X, then Y, then Z, so the walk is deterministic and never skips a cell — a
 * diagonal beam is treated as passing through the cells on both sides of the edge, which is the
 * conservative reading for "is anything in the way".
 */
public final class BeamPath {

    private BeamPath() {
    }

    /** A block cell on the walk (integer block coordinates). */
    public record Cell(int x, int y, int z) {
    }

    /**
     * Every cell strictly between the block at {@code (x0,y0,z0)} and the block at {@code (x1,y1,z1)},
     * walking centre to centre, in order from the source. Identical or adjacent endpoints yield an
     * empty list.
     */
    public static List<Cell> cellsBetween(int x0, int y0, int z0, int x1, int y1, int z1) {
        List<Cell> out = new ArrayList<>();
        if (x0 == x1 && y0 == y1 && z0 == z1) {
            return out;
        }
        // Segment from centre to centre; the block-cell walk starts in (x0,y0,z0).
        double sx = x0 + 0.5;
        double sy = y0 + 0.5;
        double sz = z0 + 0.5;
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;

        int cx = x0;
        int cy = y0;
        int cz = z0;
        int stepX = Integer.signum(x1 - x0);
        int stepY = Integer.signum(y1 - y0);
        int stepZ = Integer.signum(z1 - z0);

        double tMaxX = firstCrossing(sx, cx, dx, stepX);
        double tMaxY = firstCrossing(sy, cy, dy, stepY);
        double tMaxZ = firstCrossing(sz, cz, dz, stepZ);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);

        // Manhattan length bounds the number of cell transitions; +1 is slack against rounding.
        int guard = Math.abs(x1 - x0) + Math.abs(y1 - y0) + Math.abs(z1 - z0) + 1;
        while (guard-- > 0) {
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                cx += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY <= tMaxZ) {
                cy += stepY;
                tMaxY += tDeltaY;
            } else {
                cz += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (cx == x1 && cy == y1 && cz == z1) {
                break; // arrived: the target cell itself is not part of the path
            }
            out.add(new Cell(cx, cy, cz));
        }
        return out;
    }

    /**
     * The parametric {@code t} (0..1 along the segment) at which the segment first leaves cell
     * {@code cell} along one axis, starting at {@code start} with total axis delta {@code delta}.
     */
    private static double firstCrossing(double start, int cell, double delta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - start) / delta;
    }
}

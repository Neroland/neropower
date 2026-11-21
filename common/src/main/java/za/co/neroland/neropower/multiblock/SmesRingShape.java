package za.co.neroland.neropower.multiblock;

import java.util.EnumMap;

/**
 * The storage ring: a horizontal hollow square, one block wide and one layer high, outer edge
 * {@value #MIN_EDGE}..{@value #MAX_EDGE}, of {@code smes_coil} with one {@code smes_controller}
 * replacing a coil in the middle of a side (never a corner). The inside of the ring is not checked:
 * the ring is the structure. At most 4 x 15 - 4 = 56 positions are read.
 *
 * <p>Counts: {@link Cell#SMES_COIL} = coils in the ring.
 */
public final class SmesRingShape {

    public static final int MIN_EDGE = 5;
    public static final int MAX_EDGE = 15;

    private SmesRingShape() {
    }

    /** Evaluate the ring whose controller sits at {@code (x, y, z)}. */
    public static ShapeResult evaluate(Probe probe, int x, int y, int z) {
        int sx;
        int sz;
        if (probe.at(x - 1, y, z) == Cell.SMES_COIL && probe.at(x + 1, y, z) == Cell.SMES_COIL) {
            sx = 1;
            sz = 0;
        } else if (probe.at(x, y, z - 1) == Cell.SMES_COIL && probe.at(x, y, z + 1) == Cell.SMES_COIL) {
            sx = 0;
            sz = 1;
        } else {
            return ShapeResult.unformed(x, y, z);
        }
        int back = 0;
        while (back < MAX_EDGE && probe.at(x - sx * (back + 1), y, z - sz * (back + 1)) == Cell.SMES_COIL) {
            back++;
        }
        int fwd = 0;
        while (fwd < MAX_EDGE && probe.at(x + sx * (fwd + 1), y, z + sz * (fwd + 1)) == Cell.SMES_COIL) {
            fwd++;
        }
        int edge = back + fwd + 1;
        if (edge < MIN_EDGE || edge > MAX_EDGE) {
            return ShapeResult.unformed(x, y, z);
        }
        int ax = x - sx * back;
        int az = z - sz * back;
        int bx = x + sx * fwd;
        int bz = z + sz * fwd;
        // The perpendicular: whichever side both corners continue on (positive first).
        int px = sz;
        int pz = sx;
        for (int sign : new int[] {1, -1}) {
            if (probe.at(ax + px * sign, y, az + pz * sign) != Cell.SMES_COIL
                    || probe.at(bx + px * sign, y, bz + pz * sign) != Cell.SMES_COIL) {
                continue;
            }
            int far = edge - 1;
            Bounds bounds = Bounds.of(ax, y, az, bx + px * sign * far, y, bz + pz * sign * far);
            ShapeResult result = check(probe, bounds, x, y, z, edge);
            if (result != null) {
                return result;
            }
        }
        return ShapeResult.unformed(x, y, z);
    }

    private static ShapeResult check(Probe probe, Bounds bounds, int x, int y, int z, int edge) {
        if (!probe.loaded(bounds)) {
            return null;
        }
        int coils = 0;
        for (int px = bounds.minX(); px <= bounds.maxX(); px++) {
            for (int pz = bounds.minZ(); pz <= bounds.maxZ(); pz++) {
                boolean border = px == bounds.minX() || px == bounds.maxX() || pz == bounds.minZ() || pz == bounds.maxZ();
                if (!border || (px == x && pz == z)) {
                    continue;
                }
                if (probe.at(px, y, pz) != Cell.SMES_COIL) {
                    return null;
                }
                coils++;
            }
        }
        EnumMap<Cell, Integer> counts = new EnumMap<>(Cell.class);
        counts.put(Cell.SMES_COIL, coils);
        int half = (edge - 1) / 2;
        return ShapeResult.formed(edge, bounds, bounds.minX() + half, y, bounds.minZ() + half, counts);
    }
}

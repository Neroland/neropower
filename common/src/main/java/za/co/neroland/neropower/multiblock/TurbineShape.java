package za.co.neroland.neropower.multiblock;

import java.util.EnumMap;

/**
 * The steam turbine: a 5x5 cross-section {@value #MIN_LENGTH}..{@value #MAX_LENGTH} long along the
 * helm's axis. The outer surface is {@code turbine_shroud}, with the {@code turbine_helm} at the centre
 * of one end cap and 1..{@value #MAX_STEAM_PORTS} {@code turbine_steam_port}s on the side faces (never
 * an edge or an end cap). Every interior slice is 3x3: a {@code turbine_spindle} in the centre and a
 * ring that is either all air (a vane slice: the vane sets ride the spindle) or all eight
 * {@code turbine_stator}s (a stator slice). Length is read by walking the spindle from the helm.
 *
 * <p>Counts: {@link Cell#TURBINE_SPINDLE} = interior slices, {@link Cell#TURBINE_STATOR} = stator
 * blocks, {@link Cell#AIR} = ring air (vane slices x 8), {@link Cell#TURBINE_STEAM_PORT}.
 */
public final class TurbineShape {

    public static final int WIDTH = 5;
    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 16;
    public static final int MAX_STEAM_PORTS = 8;

    private TurbineShape() {
    }

    /**
     * Evaluate a turbine whose helm sits at {@code (x, y, z)} and whose body runs along the unit step
     * {@code (ax, ay, az)} (away from the helm's front).
     */
    public static ShapeResult evaluate(Probe probe, int x, int y, int z, int ax, int ay, int az) {
        int[] u;
        int[] v;
        if (ax != 0) {
            u = new int[] {0, 1, 0};
            v = new int[] {0, 0, 1};
        } else if (ay != 0) {
            u = new int[] {1, 0, 0};
            v = new int[] {0, 0, 1};
        } else {
            u = new int[] {1, 0, 0};
            v = new int[] {0, 1, 0};
        }
        // Length: walk the spindle from the helm.
        int slices = 0;
        while (slices < MAX_LENGTH - 2
                && probe.at(x + ax * (slices + 1), y + ay * (slices + 1), z + az * (slices + 1)) == Cell.TURBINE_SPINDLE) {
            slices++;
        }
        int length = slices + 2;
        if (length < MIN_LENGTH) {
            return ShapeResult.unformed(x, y, z);
        }
        int ex = x + ax * (length - 1);
        int ey = y + ay * (length - 1);
        int ez = z + az * (length - 1);
        Bounds bounds = Bounds.of(x - 2 * (u[0] + v[0]), y - 2 * (u[1] + v[1]), z - 2 * (u[2] + v[2]),
                ex + 2 * (u[0] + v[0]), ey + 2 * (u[1] + v[1]), ez + 2 * (u[2] + v[2]));
        if (!ShapeScan.withinBudget(bounds) || !probe.loaded(bounds)) {
            return ShapeResult.unformed(x, y, z);
        }
        EnumMap<Cell, Integer> counts = new EnumMap<>(Cell.class);
        for (int k = 0; k < length; k++) {
            boolean cap = k == 0 || k == length - 1;
            int sliceAir = 0;
            int sliceStator = 0;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    int px = x + ax * k + u[0] * i + v[0] * j;
                    int py = y + ay * k + u[1] * i + v[1] * j;
                    int pz = z + az * k + u[2] * i + v[2] * j;
                    if (k == 0 && i == 0 && j == 0) {
                        continue; // the helm
                    }
                    Cell cell = probe.at(px, py, pz);
                    boolean side = Math.abs(i) == 2 || Math.abs(j) == 2;
                    if (cap || side) {
                        if (cell == Cell.TURBINE_SHROUD) {
                            continue;
                        }
                        boolean faceOnly = !cap && (Math.abs(i) == 2 ^ Math.abs(j) == 2);
                        if (faceOnly && cell == Cell.TURBINE_STEAM_PORT) {
                            counts.merge(cell, 1, Integer::sum);
                            continue;
                        }
                        return ShapeResult.unformed(x, y, z);
                    }
                    if (i == 0 && j == 0) {
                        if (cell != Cell.TURBINE_SPINDLE) {
                            return ShapeResult.unformed(x, y, z);
                        }
                        counts.merge(cell, 1, Integer::sum);
                    } else if (cell == Cell.AIR) {
                        sliceAir++;
                    } else if (cell == Cell.TURBINE_STATOR) {
                        sliceStator++;
                    } else {
                        return ShapeResult.unformed(x, y, z);
                    }
                }
            }
            if (!cap) {
                if (sliceAir != 0 && sliceStator != 0) {
                    return ShapeResult.unformed(x, y, z); // a ring is all air or all stators
                }
                counts.merge(Cell.AIR, sliceAir, Integer::sum);
                counts.merge(Cell.TURBINE_STATOR, sliceStator, Integer::sum);
            }
        }
        int ports = counts.getOrDefault(Cell.TURBINE_STEAM_PORT, 0);
        if (ports < 1 || ports > MAX_STEAM_PORTS) {
            return ShapeResult.unformed(x, y, z);
        }
        int mid = (length - 1) / 2;
        return ShapeResult.formed(length, bounds, x + ax * mid, y + ay * mid, z + az * mid, counts);
    }

    /** Vane slices in a result (air rings). */
    public static int vaneSlices(ShapeResult result) {
        return result.count(Cell.AIR) / 8;
    }

    /** Stator slices in a result. */
    public static int statorSlices(ShapeResult result) {
        return result.count(Cell.TURBINE_STATOR) / 8;
    }
}

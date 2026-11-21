package za.co.neroland.neropower.multiblock;

import java.util.EnumMap;

/**
 * The fission reactor: a hollow cube of {@code fission_casing} with edge 7, 5 or 3 (scanned largest
 * first so a large shell is never mistaken for its smaller core). The controller ({@code fission_core})
 * sits in one wall, facing out; the shell extends behind it. Interior positions hold air or reactor
 * interior blocks (control rods, moderators, breeder blanket), all counted. Wall faces (never edges or
 * corners) may hold up to {@value #MAX_STEAM_PORTS} steam ports and one control computer; the 7-edge
 * shell needs the control computer. Generalised from the 0.3.x {@code FissionStructure}: for the blocks
 * that existed then, the 3 and 5 results are identical (regression test).
 */
public final class ReactorShape {

    /** Shell edges, largest first. */
    public static final int[] SIZES = {7, 5, 3};
    /** Smallest and largest shell edge. */
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 7;
    /** Steam ports allowed in one shell. */
    public static final int MAX_STEAM_PORTS = 4;

    private ReactorShape() {
    }

    /**
     * Evaluate the largest formed shell for a controller at {@code (x, y, z)} whose front faces
     * {@code (fx, fy, fz)} (a unit step); the shell lies behind the front.
     */
    public static ShapeResult evaluate(Probe probe, int x, int y, int z, int fx, int fy, int fz) {
        for (int size : SIZES) {
            ShapeResult result = evaluate(probe, x, y, z, fx, fy, fz, size);
            if (result != null) {
                return result;
            }
        }
        return ShapeResult.unformed(x, y, z);
    }

    /** One size, or null when it does not form. */
    static ShapeResult evaluate(Probe probe, int x, int y, int z, int fx, int fy, int fz, int size) {
        int half = (size - 1) / 2;
        int cx = x - fx * half;
        int cy = y - fy * half;
        int cz = z - fz * half;
        Bounds bounds = new Bounds(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
        if (!ShapeScan.withinBudget(bounds) || !probe.loaded(bounds)) {
            return null; // never force-load chunks for validation
        }
        EnumMap<Cell, Integer> counts = new EnumMap<>(Cell.class);
        for (int px = bounds.minX(); px <= bounds.maxX(); px++) {
            for (int py = bounds.minY(); py <= bounds.maxY(); py++) {
                for (int pz = bounds.minZ(); pz <= bounds.maxZ(); pz++) {
                    if (px == x && py == y && pz == z) {
                        continue; // the controller occupies its wall slot
                    }
                    Cell cell = probe.at(px, py, pz);
                    int faces = bounds.faces(px, py, pz);
                    if (faces > 0) {
                        if (cell == Cell.FISSION_CASING) {
                            continue;
                        }
                        if (faces == 1 && (cell == Cell.REACTOR_STEAM_PORT || cell == Cell.REACTOR_CONTROL_COMPUTER)) {
                            counts.merge(cell, 1, Integer::sum);
                            continue;
                        }
                        return null;
                    }
                    if (cell == Cell.AIR) {
                        continue;
                    }
                    if (!cell.reactorInterior()) {
                        return null;
                    }
                    counts.merge(cell, 1, Integer::sum);
                }
            }
        }
        int computers = counts.getOrDefault(Cell.REACTOR_CONTROL_COMPUTER, 0);
        if (counts.getOrDefault(Cell.REACTOR_STEAM_PORT, 0) > MAX_STEAM_PORTS || computers > 1) {
            return null;
        }
        if (size == MAX_SIZE && computers == 0) {
            return null;
        }
        return ShapeResult.formed(size, bounds, cx, cy, cz, counts);
    }

    /** Control-rod assemblies of either tier in a result. */
    public static int rods(ShapeResult result) {
        return result.count(Cell.CONTROL_ROD) + result.count(Cell.EUROPIUM_CONTROL_ROD);
    }
}

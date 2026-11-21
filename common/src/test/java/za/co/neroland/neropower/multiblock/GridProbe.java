package za.co.neroland.neropower.multiblock;

import java.util.HashMap;
import java.util.Map;

/** A test world: a sparse grid of cells (default air) with an optional unloaded position. */
final class GridProbe implements Probe {

    private final Map<Long, Cell> cells = new HashMap<>();
    private long unloaded = Long.MIN_VALUE;
    private boolean hasUnloaded;

    static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    GridProbe set(int x, int y, int z, Cell cell) {
        this.cells.put(key(x, y, z), cell);
        return this;
    }

    GridProbe unload(int x, int y, int z) {
        this.unloaded = key(x, y, z);
        this.hasUnloaded = true;
        return this;
    }

    /** Fill the surface of a cube with {@code shell} and its interior with air. */
    GridProbe cube(Bounds b, Cell shell) {
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int y = b.minY(); y <= b.maxY(); y++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    set(x, y, z, b.faces(x, y, z) > 0 ? shell : Cell.AIR);
                }
            }
        }
        return this;
    }

    @Override
    public Cell at(int x, int y, int z) {
        long k = key(x, y, z);
        if (this.hasUnloaded && k == this.unloaded) {
            return Cell.UNLOADED;
        }
        return this.cells.getOrDefault(k, Cell.AIR);
    }

    @Override
    public boolean loaded(Bounds b) {
        if (!this.hasUnloaded) {
            return true;
        }
        int x = (int) (this.unloaded >> 42);
        int y = (int) ((this.unloaded >> 21) & 0x1FFFFF);
        int z = (int) (this.unloaded & 0x1FFFFF);
        x = x << 11 >> 11;
        y = y << 11 >> 11;
        z = z << 11 >> 11;
        return !b.contains(x, y, z);
    }
}

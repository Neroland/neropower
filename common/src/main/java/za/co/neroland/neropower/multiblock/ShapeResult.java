package za.co.neroland.neropower.multiblock;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A shape evaluation. When {@code valid} is false the size is 0, the bounds collapse to the controller
 * and the counts are empty.
 *
 * @param valid   whether the structure is formed
 * @param size    the size that formed (cube edge, turbine length or ring edge), 0 when unformed
 * @param bounds  the structure's box (the controller alone when unformed)
 * @param centreX interior centre x (the failure epicentre)
 * @param centreY interior centre y
 * @param centreZ interior centre z
 * @param counts  cells counted inside the structure, by kind
 */
public record ShapeResult(boolean valid, int size, Bounds bounds, int centreX, int centreY, int centreZ,
        Map<Cell, Integer> counts) {

    /** The unformed result for a controller at a position. */
    public static ShapeResult unformed(int x, int y, int z) {
        return new ShapeResult(false, 0, new Bounds(x, y, z, x, y, z), x, y, z, Map.of());
    }

    /** A formed result; the counts are copied. */
    public static ShapeResult formed(int size, Bounds bounds, int cx, int cy, int cz, EnumMap<Cell, Integer> counts) {
        return new ShapeResult(true, size, bounds, cx, cy, cz, Collections.unmodifiableMap(new EnumMap<>(counts)));
    }

    /** How many of {@code cell} were counted. */
    public int count(Cell cell) {
        Integer n = this.counts.get(cell);
        return n == null ? 0 : n;
    }
}

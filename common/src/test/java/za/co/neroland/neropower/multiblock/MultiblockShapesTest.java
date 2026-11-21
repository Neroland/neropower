package za.co.neroland.neropower.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The turbine and storage-ring shapes of the shared multiblock base: sizes, interior rules, port
 * placement, the scan budget. Pure JVM.
 */
class MultiblockShapesTest {

    /** A turbine along +z from a helm at (0, 64, 0): length L, slices listed as stator (true) or vane. */
    private static GridProbe turbine(int length, boolean... statorSlices) {
        GridProbe g = new GridProbe();
        for (int k = 0; k < length; k++) {
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    boolean cap = k == 0 || k == length - 1;
                    boolean side = Math.abs(i) == 2 || Math.abs(j) == 2;
                    Cell cell;
                    if (cap || side) {
                        cell = Cell.TURBINE_SHROUD;
                    } else if (i == 0 && j == 0) {
                        cell = Cell.TURBINE_SPINDLE;
                    } else {
                        cell = statorSlices[k - 1] ? Cell.TURBINE_STATOR : Cell.AIR;
                    }
                    g.set(i, 64 + j, k, cell);
                }
            }
        }
        g.set(0, 64, 0, Cell.TURBINE_HELM);
        g.set(2, 64, 1, Cell.TURBINE_STEAM_PORT);
        return g;
    }

    @Test
    void turbineFormsAndCountsSlices() {
        GridProbe g = turbine(6, false, false, true, true);
        ShapeResult r = TurbineShape.evaluate(g, 0, 64, 0, 0, 0, 1);
        assertTrue(r.valid());
        assertEquals(6, r.size());
        assertEquals(2, TurbineShape.vaneSlices(r));
        assertEquals(2, TurbineShape.statorSlices(r));
        assertEquals(1, r.count(Cell.TURBINE_STEAM_PORT));
    }

    @Test
    void turbineRejectsMixedRingsPortsOnEdgesAndNoPort() {
        GridProbe mixed = turbine(5, false, true, false);
        mixed.set(1, 64, 2, Cell.AIR); // one stator missing from a stator ring
        assertFalse(TurbineShape.evaluate(mixed, 0, 64, 0, 0, 0, 1).valid());

        GridProbe edge = turbine(5, false, true, false);
        edge.set(2, 66, 2, Cell.TURBINE_STEAM_PORT);
        assertFalse(TurbineShape.evaluate(edge, 0, 64, 0, 0, 0, 1).valid());

        GridProbe none = turbine(5, false, true, false);
        none.set(2, 64, 1, Cell.TURBINE_SHROUD);
        assertFalse(TurbineShape.evaluate(none, 0, 64, 0, 0, 0, 1).valid());
    }

    @Test
    void turbineLengthBounds() {
        assertFalse(TurbineShape.evaluate(turbine(3, false), 0, 64, 0, 0, 0, 1).valid(), "too short");
        boolean[] fourteen = new boolean[14];
        fourteen[0] = true;
        ShapeResult longest = TurbineShape.evaluate(turbine(16, fourteen), 0, 64, 0, 0, 0, 1);
        assertTrue(longest.valid());
        assertEquals(ShapeScan.MAX_POSITIONS, longest.bounds().volume());
    }

    /** A ring of edge E in the XZ plane at y 64, corner (0, 0); controller mid south side. */
    private static GridProbe ring(int edge) {
        GridProbe g = new GridProbe();
        for (int x = 0; x < edge; x++) {
            for (int z = 0; z < edge; z++) {
                if (x == 0 || z == 0 || x == edge - 1 || z == edge - 1) {
                    g.set(x, 64, z, Cell.SMES_COIL);
                }
            }
        }
        g.set(edge / 2, 64, 0, Cell.SMES_CONTROLLER);
        return g;
    }

    @Test
    void ringFormsAtEverySize() {
        for (int edge = SmesRingShape.MIN_EDGE; edge <= SmesRingShape.MAX_EDGE; edge++) {
            ShapeResult r = SmesRingShape.evaluate(ring(edge), edge / 2, 64, 0);
            assertTrue(r.valid(), "edge " + edge);
            assertEquals(edge, r.size());
            assertEquals(4 * edge - 5, r.count(Cell.SMES_COIL));
        }
    }

    @Test
    void ringRejectsGapsSecondControllerAndSmallRings() {
        GridProbe gap = ring(7);
        gap.set(6, 64, 3, Cell.AIR);
        assertFalse(SmesRingShape.evaluate(gap, 3, 64, 0).valid());
        GridProbe two = ring(7);
        two.set(3, 64, 6, Cell.SMES_CONTROLLER);
        assertFalse(SmesRingShape.evaluate(two, 3, 64, 0).valid());
        assertFalse(SmesRingShape.evaluate(ring(4), 2, 64, 0).valid());
    }
}

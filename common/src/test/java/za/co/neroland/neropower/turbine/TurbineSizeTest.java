package za.co.neroland.neropower.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.Probe;
import za.co.neroland.neropower.multiblock.ShapeResult;
import za.co.neroland.neropower.multiblock.TurbineShape;

/** Turbine minimum and maximum sizes (5 x 5, length 4 to 16) on the shared shape, along x and z. */
class TurbineSizeTest {

    private static final class Grid implements Probe {
        private final Map<Long, Cell> cells = new HashMap<>();

        void set(int x, int y, int z, Cell cell) {
            this.cells.put(((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF), cell);
        }

        @Override
        public Cell at(int x, int y, int z) {
            return this.cells.getOrDefault(((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF),
                    Cell.AIR);
        }
    }

    /** A turbine of {@code length} from a helm at the origin along +x (ax = 1) or +z. */
    private static Grid build(int length, boolean alongX) {
        Grid g = new Grid();
        for (int k = 0; k < length; k++) {
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    boolean shell = k == 0 || k == length - 1 || Math.abs(i) == 2 || Math.abs(j) == 2;
                    Cell cell = shell ? Cell.TURBINE_SHROUD
                            : i == 0 && j == 0 ? Cell.TURBINE_SPINDLE
                            : k % 2 == 0 ? Cell.TURBINE_STATOR : Cell.AIR;
                    if (alongX) {
                        g.set(k, 64 + i, j, cell);
                    } else {
                        g.set(i, 64 + j, k, cell);
                    }
                }
            }
        }
        if (alongX) {
            g.set(0, 64, 0, Cell.TURBINE_HELM);
            g.set(1, 64, 2, Cell.TURBINE_STEAM_PORT);
        } else {
            g.set(0, 64, 0, Cell.TURBINE_HELM);
            g.set(2, 64, 1, Cell.TURBINE_STEAM_PORT);
        }
        return g;
    }

    private static ShapeResult eval(Grid g, boolean alongX) {
        return alongX ? TurbineShape.evaluate(g, 0, 64, 0, 1, 0, 0) : TurbineShape.evaluate(g, 0, 64, 0, 0, 0, 1);
    }

    @Test
    void minimumAndMaximumLengthsForm() {
        for (boolean alongX : new boolean[] {true, false}) {
            ShapeResult min = eval(build(TurbineShape.MIN_LENGTH, alongX), alongX);
            assertTrue(min.valid(), "length 4 forms");
            assertEquals(4, min.size());
            assertEquals(1, TurbineShape.vaneSlices(min));
            assertEquals(1, TurbineShape.statorSlices(min));
            ShapeResult max = eval(build(TurbineShape.MAX_LENGTH, alongX), alongX);
            assertTrue(max.valid(), "length 16 forms");
            assertEquals(16, max.size());
        }
    }

    @Test
    void tooShortAndTooLongDoNotForm() {
        for (boolean alongX : new boolean[] {true, false}) {
            assertFalse(eval(build(3, alongX), alongX).valid(), "length 3 is too short");
            assertFalse(eval(build(17, alongX), alongX).valid(), "length 17 is too long");
        }
    }
}

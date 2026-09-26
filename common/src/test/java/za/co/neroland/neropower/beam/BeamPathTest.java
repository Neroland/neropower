package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.beam.BeamPath.Cell;

/**
 * Locks {@link BeamPath}'s voxel walk: axis-aligned segments visit exactly the cells between the
 * endpoints, diagonals step one axis at a time without gaps, and the endpoints themselves are
 * never part of the path. Pure JVM.
 */
class BeamPathTest {

    @Test
    void sameOrAdjacentCellsHaveNoInterior() {
        assertTrue(BeamPath.cellsBetween(1, 2, 3, 1, 2, 3).isEmpty());
        assertTrue(BeamPath.cellsBetween(0, 0, 0, 1, 0, 0).isEmpty());
        assertTrue(BeamPath.cellsBetween(0, 0, 0, 0, -1, 0).isEmpty());
        assertTrue(BeamPath.cellsBetween(0, 0, 0, 1, 1, 0).isEmpty() || BeamPath.cellsBetween(0, 0, 0, 1, 1, 0).size() == 1,
                "a one-block diagonal passes at most the one corner cell");
    }

    @Test
    void axisAlignedWalksEveryCellBetween() {
        assertEquals(List.of(new Cell(1, 0, 0), new Cell(2, 0, 0), new Cell(3, 0, 0)),
                BeamPath.cellsBetween(0, 0, 0, 4, 0, 0));
        assertEquals(List.of(new Cell(0, 9, 5), new Cell(0, 8, 5)),
                BeamPath.cellsBetween(0, 10, 5, 0, 7, 5), "negative Y direction");
        assertEquals(List.of(new Cell(-3, 2, -1), new Cell(-3, 2, -2)),
                BeamPath.cellsBetween(-3, 2, 0, -3, 2, -3), "negative Z direction");
    }

    @Test
    void endpointsAreExcluded() {
        List<Cell> cells = BeamPath.cellsBetween(2, 3, 4, 9, 6, -2);
        assertFalse(cells.contains(new Cell(2, 3, 4)));
        assertFalse(cells.contains(new Cell(9, 6, -2)));
        assertFalse(cells.isEmpty());
    }

    @Test
    void planarDiagonalStepsOneAxisAtATimeWithoutGaps() {
        List<Cell> cells = BeamPath.cellsBetween(0, 0, 0, 3, 3, 0);
        // A 45° line through cell centres leaves each cell exactly through a corner; the X-then-Y
        // tie-break visits both cells adjacent to every corner, so the on-line cells are all there...
        assertTrue(cells.contains(new Cell(1, 1, 0)));
        assertTrue(cells.contains(new Cell(2, 2, 0)));
        // ...and every step moves by exactly one along exactly one axis.
        Cell previous = new Cell(0, 0, 0);
        for (Cell cell : cells) {
            int moved = Math.abs(cell.x() - previous.x()) + Math.abs(cell.y() - previous.y())
                    + Math.abs(cell.z() - previous.z());
            assertEquals(1, moved, "single-axis unit step from " + previous + " to " + cell);
            assertTrue(cell.x() >= 0 && cell.x() <= 3 && cell.y() >= 0 && cell.y() <= 3 && cell.z() == 0,
                    "stays inside the segment's bounding box: " + cell);
            previous = cell;
        }
        // The last cell must be adjacent to the (excluded) target.
        assertEquals(1, Math.abs(3 - previous.x()) + Math.abs(3 - previous.y()));
        assertEquals(5, cells.size(), "Manhattan length 6 minus the target cell");
    }

    @Test
    void shallowDiagonalHugsTheLine() {
        // From (0,0,0) to (6,2,0): the line y = x/3 passes cells (1,0) (2,0/1) (3,1) (4,1) (5,1/2).
        List<Cell> cells = BeamPath.cellsBetween(0, 0, 0, 6, 2, 0);
        assertTrue(cells.contains(new Cell(1, 0, 0)));
        assertTrue(cells.contains(new Cell(3, 1, 0)));
        assertTrue(cells.contains(new Cell(4, 1, 0)));
        assertFalse(cells.contains(new Cell(1, 2, 0)), "far off the line");
        assertFalse(cells.contains(new Cell(5, 0, 0)), "far off the line");
        assertFalse(cells.contains(new Cell(6, 2, 0)), "target excluded");
        for (Cell cell : cells) {
            double t = cell.x() + 0.5;
            double lineY = t / 3.0;
            assertTrue(Math.abs((cell.y() + 0.5) - lineY) <= 1.0, "within one block of the line: " + cell);
        }
    }

    @Test
    void fullThreeAxisDiagonalIsConnectedAndBounded() {
        List<Cell> cells = BeamPath.cellsBetween(0, 0, 0, 4, 4, 4);
        Cell previous = new Cell(0, 0, 0);
        for (Cell cell : cells) {
            int moved = Math.abs(cell.x() - previous.x()) + Math.abs(cell.y() - previous.y())
                    + Math.abs(cell.z() - previous.z());
            assertEquals(1, moved);
            previous = cell;
        }
        assertEquals(11, cells.size(), "Manhattan length 12 minus the target cell");
        assertTrue(cells.contains(new Cell(2, 2, 2)));
    }

    @Test
    void walkIsSymmetricInCellSet() {
        List<Cell> forward = BeamPath.cellsBetween(1, 1, 1, 8, 3, 5);
        List<Cell> backward = BeamPath.cellsBetween(8, 3, 5, 1, 1, 1);
        assertEquals(forward.size(), backward.size());
        for (Cell cell : forward) {
            assertTrue(backward.contains(cell), "reverse walk visits " + cell);
        }
    }
}

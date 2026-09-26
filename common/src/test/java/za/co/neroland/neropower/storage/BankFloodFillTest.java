package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link BankFloodFill} on a plain packed-long grid: coordinate packing round-trips (negatives
 * included), membership starts only from cells touching the controller, walks face-to-face through
 * cells, ignores diagonals and gaps, stays inside the radius cube and never includes the controller.
 * Pure JVM.
 */
class BankFloodFillTest {

    private static final long CONTROLLER = BankFloodFill.pack(10, 64, -20);

    private static Set<Long> grid(int[]... cells) {
        Set<Long> set = new HashSet<>();
        for (int[] c : cells) {
            set.add(BankFloodFill.pack(c[0], c[1], c[2]));
        }
        return set;
    }

    private static int[] rel(int dx, int dy, int dz) {
        return new int[] {10 + dx, 64 + dy, -20 + dz};
    }

    @Test
    void packingRoundTrips() {
        for (int[] p : new int[][] {{0, 0, 0}, {10, 64, -20}, {-30_000_000, -2_048, 30_000_000},
                {33_554_431, 2_047, -33_554_432}}) {
            long packed = BankFloodFill.pack(p[0], p[1], p[2]);
            assertEquals(p[0], BankFloodFill.unpackX(packed), "x");
            assertEquals(p[1], BankFloodFill.unpackY(packed), "y");
            assertEquals(p[2], BankFloodFill.unpackZ(packed), "z");
        }
    }

    @Test
    void noCellTouchingTheControllerMeansNoBank() {
        Set<Long> cells = grid(rel(2, 0, 0), rel(1, 1, 0));
        assertTrue(BankFloodFill.collect(CONTROLLER, 2, cells::contains).isEmpty());
    }

    @Test
    void walksFaceToFaceThroughCellsOnly() {
        // A line east of the controller with a gap after the second cell.
        Set<Long> cells = grid(rel(1, 0, 0), rel(2, 0, 0), rel(4, 0, 0));
        List<Long> members = BankFloodFill.collect(CONTROLLER, 4, cells::contains);
        assertEquals(2, members.size());
        assertTrue(members.contains(BankFloodFill.pack(11, 64, -20)));
        assertTrue(members.contains(BankFloodFill.pack(12, 64, -20)));
        assertFalse(members.contains(BankFloodFill.pack(14, 64, -20)), "beyond the gap");
    }

    @Test
    void diagonalsDoNotConnect() {
        Set<Long> cells = grid(rel(1, 0, 0), rel(2, 1, 0), rel(2, 1, 1));
        List<Long> members = BankFloodFill.collect(CONTROLLER, 3, cells::contains);
        assertEquals(1, members.size(), "the diagonal pair is unreachable");
    }

    @Test
    void staysInsideTheRadiusCubeAndSkipsTheController() {
        // A full 5x5x5 of cells around the controller (radius 2), plus a ring at distance 3.
        int[][] all = new int[7 * 7 * 7][];
        int n = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    all[n++] = rel(dx, dy, dz);
                }
            }
        }
        Set<Long> cells = grid(all);
        cells.remove(CONTROLLER); // the controller's own block is not a cell
        List<Long> members = BankFloodFill.collect(CONTROLLER, 2, cells::contains);
        assertEquals(5 * 5 * 5 - 1, members.size());
        assertFalse(members.contains(CONTROLLER));
        for (long m : members) {
            assertTrue(Math.abs(BankFloodFill.unpackX(m) - 10) <= 2);
            assertTrue(Math.abs(BankFloodFill.unpackY(m) - 64) <= 2);
            assertTrue(Math.abs(BankFloodFill.unpackZ(m) + 20) <= 2);
        }
        assertEquals(members.size(), new HashSet<>(members).size(), "no duplicates");
    }

    @Test
    void discoveryOrderIsDeterministic() {
        Set<Long> cells = grid(rel(0, -1, 0), rel(0, 1, 0), rel(1, 0, 0), rel(2, 0, 0));
        List<Long> first = BankFloodFill.collect(CONTROLLER, 2, cells::contains);
        List<Long> second = BankFloodFill.collect(CONTROLLER, 2, cells::contains);
        assertEquals(first, second);
        // Faces are visited down, up, north, south, west, east — so the cell below comes first.
        assertEquals(BankFloodFill.pack(10, 63, -20), first.get(0));
        assertEquals(BankFloodFill.pack(10, 65, -20), first.get(1));
        assertEquals(BankFloodFill.pack(11, 64, -20), first.get(2));
        assertEquals(BankFloodFill.pack(12, 64, -20), first.get(3));
    }

    @Test
    void zeroRadiusYieldsNothing() {
        Set<Long> cells = grid(rel(1, 0, 0));
        assertTrue(BankFloodFill.collect(CONTROLLER, 0, cells::contains).isEmpty());
    }
}

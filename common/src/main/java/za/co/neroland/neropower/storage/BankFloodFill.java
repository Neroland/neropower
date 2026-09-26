package za.co.neroland.neropower.storage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * The Battery Bank membership rule, Minecraft-free: a 6-neighbour flood fill over battery cells,
 * seeded by the cells touching the controller and bounded by a cube of {@code radius} around it.
 * Positions are packed {@code long}s in the {@code BlockPos.asLong} layout (26-bit x, 12-bit y,
 * 26-bit z) so the fill can be tested on a plain grid and driven in-world without translation.
 *
 * <p>Deterministic: the result is in discovery order (breadth-first from the controller's faces in
 * D-U-N-S-W-E order), so the pool's round-robin cursor keeps meaning across rescans of an unchanged
 * bank.
 */
public final class BankFloodFill {

    private static final int X_BITS = 26;
    private static final int Z_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long X_MASK = (1L << X_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final long Z_MASK = (1L << Z_BITS) - 1L;
    private static final int Z_OFFSET = Y_BITS;
    private static final int X_OFFSET = Y_BITS + Z_BITS;

    /** The six face offsets, in vanilla {@code Direction} order (down, up, north, south, west, east). */
    private static final int[][] FACES = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private BankFloodFill() {
    }

    /** Pack a block position ({@code BlockPos.asLong} layout). */
    public static long pack(int x, int y, int z) {
        return ((x & X_MASK) << X_OFFSET) | ((z & Z_MASK) << Z_OFFSET) | (y & Y_MASK);
    }

    public static int unpackX(long packed) {
        return (int) (packed << (64 - X_OFFSET - X_BITS) >> (64 - X_BITS));
    }

    public static int unpackY(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int unpackZ(long packed) {
        return (int) (packed << (64 - Z_OFFSET - Z_BITS) >> (64 - Z_BITS));
    }

    /**
     * Collect every cell connected to the controller at {@code controller}: start from the cells on
     * its six faces, then walk face-to-face through cells only, never leaving the cube of
     * {@code radius} around the controller and never counting the controller itself.
     *
     * @param controller packed controller position
     * @param radius     cube half-size in blocks (1 = 3×3×3)
     * @param isCell     whether the packed position holds a battery cell
     * @return packed member positions in discovery order (empty when no cell touches the controller)
     */
    public static List<Long> collect(long controller, int radius, LongPredicate isCell) {
        List<Long> members = new ArrayList<>();
        if (radius <= 0) {
            return members;
        }
        int cx = unpackX(controller);
        int cy = unpackY(controller);
        int cz = unpackZ(controller);
        Set<Long> seen = new HashSet<>();
        seen.add(controller);
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(controller);
        while (!queue.isEmpty()) {
            long current = queue.poll();
            int x = unpackX(current);
            int y = unpackY(current);
            int z = unpackZ(current);
            for (int[] face : FACES) {
                int nx = x + face[0];
                int ny = y + face[1];
                int nz = z + face[2];
                if (Math.abs(nx - cx) > radius || Math.abs(ny - cy) > radius || Math.abs(nz - cz) > radius) {
                    continue;
                }
                long next = pack(nx, ny, nz);
                if (!seen.add(next)) {
                    continue;
                }
                if (isCell.test(next)) {
                    members.add(next);
                    queue.add(next);
                }
            }
        }
        return members;
    }
}

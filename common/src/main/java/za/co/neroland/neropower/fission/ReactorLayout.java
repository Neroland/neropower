package za.co.neroland.neropower.fission;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.Probe;

/**
 * What a formed reactor holds beyond its shape: the moderators' fuel-channel contacts, the europium
 * rods and breeder blankets inside, and where its steam ports and control computer sit. Pure (reads
 * {@link Cell}s through a {@link Probe}), computed once per revalidation from the formed bounds, never
 * per tick.
 *
 * <p><b>Fuel channels.</b> Each interior moderator counts its face-adjacent <i>interior</i> air
 * blocks (0–6): a moderator packed against other blocks or the shell moderates nothing, so layout
 * matters. Graphite weighs 1 per contact, a Moderator Tank 2.
 *
 * @param graphiteContacts fuel-channel contacts summed over every Graphite Moderator
 * @param tankContacts     fuel-channel contacts summed over every Moderator Tank
 * @param europiumRods     Europium Control Rod Assemblies inside
 * @param blankets         Breeder Blankets inside
 * @param steamPorts       wall positions of the Reactor Steam Ports, as {x, y, z}
 * @param computer         wall position of the Reactor Control Computer, or null
 */
public record ReactorLayout(int graphiteContacts, int tankContacts, int europiumRods, int blankets,
        List<int[]> steamPorts, @Nullable int[] computer) {

    /** Graphite weight per fuel-channel contact. */
    public static final int GRAPHITE_WEIGHT = 1;
    /** Moderator Tank weight per fuel-channel contact. */
    public static final int TANK_WEIGHT = 2;

    /** Nothing inside, no ports (an unformed reactor). */
    public static final ReactorLayout EMPTY = new ReactorLayout(0, 0, 0, 0, List.of(), null);

    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    /** The weighted moderator score: {@code graphite contacts x 1 + tank contacts x 2}. */
    public int moderatorScore() {
        return this.graphiteContacts * GRAPHITE_WEIGHT + this.tankContacts * TANK_WEIGHT;
    }

    /** The heavy-water part of the score alone (what a Braided Fuel Rod needs). */
    public int heavyWaterScore() {
        return this.tankContacts * TANK_WEIGHT;
    }

    /**
     * Scan a formed reactor's box. {@code bounds} must be the shell's bounds (the shape already
     * validated it); the box is read once into an array.
     */
    public static ReactorLayout scan(Probe probe, Bounds bounds) {
        int sx = bounds.maxX() - bounds.minX() + 1;
        int sy = bounds.maxY() - bounds.minY() + 1;
        int sz = bounds.maxZ() - bounds.minZ() + 1;
        Cell[] cells = new Cell[sx * sy * sz];
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    cells[(x * sy + y) * sz + z] = probe.at(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z);
                }
            }
        }
        int graphite = 0;
        int tank = 0;
        int europium = 0;
        int blankets = 0;
        List<int[]> ports = new ArrayList<>();
        int[] computer = null;
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    int wx = bounds.minX() + x;
                    int wy = bounds.minY() + y;
                    int wz = bounds.minZ() + z;
                    Cell cell = cells[(x * sy + y) * sz + z];
                    int faces = bounds.faces(wx, wy, wz);
                    if (faces == 1) {
                        if (cell == Cell.REACTOR_STEAM_PORT) {
                            ports.add(new int[] {wx, wy, wz});
                        } else if (cell == Cell.REACTOR_CONTROL_COMPUTER && computer == null) {
                            computer = new int[] {wx, wy, wz};
                        }
                        continue;
                    }
                    if (faces != 0) {
                        continue;
                    }
                    switch (cell) {
                        case GRAPHITE_MODERATOR -> graphite += channels(cells, sx, sy, sz, x, y, z);
                        case MODERATOR_TANK -> tank += channels(cells, sx, sy, sz, x, y, z);
                        case EUROPIUM_CONTROL_ROD -> europium++;
                        case BREEDER_BLANKET -> blankets++;
                        default -> {
                        }
                    }
                }
            }
        }
        return new ReactorLayout(graphite, tank, europium, blankets, List.copyOf(ports), computer);
    }

    /** Face-adjacent interior air blocks of the interior position (x, y, z) (box-relative). */
    private static int channels(Cell[] cells, int sx, int sy, int sz, int x, int y, int z) {
        int n = 0;
        for (int[] d : NEIGHBOURS) {
            int nx = x + d[0];
            int ny = y + d[1];
            int nz = z + d[2];
            if (nx <= 0 || ny <= 0 || nz <= 0 || nx >= sx - 1 || ny >= sy - 1 || nz >= sz - 1) {
                continue; // the shell is never a fuel channel
            }
            if (cells[(nx * sy + ny) * sz + nz] == Cell.AIR) {
                n++;
            }
        }
        return n;
    }
}

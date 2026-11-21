package za.co.neroland.neropower.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The reactor moved onto the shared multiblock base without changing behaviour: for 20 fixed layouts
 * (3 and 5 shells, with and without rods, broken edges, wrong interior, two controllers, every facing,
 * an unloaded corner) {@link ReactorShape} gives the same validity, shell size, rod count, bounds and
 * centre as a verbatim copy of the 0.3.x {@code FissionStructure.check} algorithm. Pure JVM.
 */
class FissionStructureRegressionTest {

    /** The 0.3.x result, as the old record carried it. */
    record Legacy(boolean valid, int size, int rods, Bounds bounds, int cx, int cy, int cz) {
    }

    /** Verbatim port of the 0.3.x algorithm (sizes 5 then 3; casing shell; rods counted; else air). */
    static Legacy legacy(Probe p, int x, int y, int z, int fx, int fy, int fz) {
        for (int size : new int[] {5, 3}) {
            int half = (size - 1) / 2;
            int cx = x - fx * half;
            int cy = y - fy * half;
            int cz = z - fz * half;
            Bounds b = new Bounds(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
            if (!p.loaded(b)) {
                continue;
            }
            int rods = 0;
            boolean ok = true;
            outer:
            for (int px = b.minX(); px <= b.maxX(); px++) {
                for (int py = b.minY(); py <= b.maxY(); py++) {
                    for (int pz = b.minZ(); pz <= b.maxZ(); pz++) {
                        boolean surface = b.faces(px, py, pz) > 0;
                        if (px == x && py == y && pz == z) {
                            continue;
                        }
                        Cell c = p.at(px, py, pz);
                        if (surface) {
                            if (c != Cell.FISSION_CASING) {
                                ok = false;
                                break outer;
                            }
                        } else if (c == Cell.CONTROL_ROD) {
                            rods++;
                        } else if (c != Cell.AIR) {
                            ok = false;
                            break outer;
                        }
                    }
                }
            }
            if (ok) {
                return new Legacy(true, size, rods, b, cx, cy, cz);
            }
        }
        return new Legacy(false, 0, 0, new Bounds(x, y, z, x, y, z), x, y, z);
    }

    /** A layout: a world and the controller with its facing. */
    record Layout(String name, GridProbe probe, int x, int y, int z, int fx, int fy, int fz) {
    }

    /** A shell of {@code size} behind a core at (0, 64, 0) facing (fx, fy, fz). */
    private static Layout shell(String name, int size, int fx, int fy, int fz) {
        int half = (size - 1) / 2;
        int cx = -fx * half;
        int cy = 64 - fy * half;
        int cz = -fz * half;
        GridProbe g = new GridProbe().cube(new Bounds(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half),
                Cell.FISSION_CASING);
        g.set(0, 64, 0, Cell.FISSION_CORE);
        return new Layout(name, g, 0, 64, 0, fx, fy, fz);
    }

    static List<Layout> layouts() {
        List<Layout> all = new ArrayList<>();
        all.add(shell("3 empty north", 3, 0, 0, -1));
        all.add(shell("3 empty south", 3, 0, 0, 1));
        all.add(shell("3 empty east", 3, 1, 0, 0));
        all.add(shell("3 empty west", 3, -1, 0, 0));
        all.add(shell("3 empty up", 3, 0, 1, 0));
        Layout l = shell("3 one rod", 3, 0, 0, 1);
        l.probe().set(0, 64, -1, Cell.CONTROL_ROD);
        all.add(l);
        all.add(shell("5 empty", 5, 0, 0, 1));
        l = shell("5 one rod", 5, 0, 0, 1);
        l.probe().set(0, 64, -2, Cell.CONTROL_ROD);
        all.add(l);
        l = shell("5 full of rods", 5, 1, 0, 0);
        for (int dx = -3; dx <= -1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    l.probe().set(dx, 64 + dy, dz, Cell.CONTROL_ROD);
                }
            }
        }
        all.add(l);
        l = shell("3 broken edge", 3, 0, 0, 1);
        l.probe().set(1, 65, 0, Cell.AIR);
        all.add(l);
        l = shell("5 broken corner", 5, 0, 0, 1);
        l.probe().set(2, 66, -4, Cell.AIR);
        all.add(l);
        l = shell("3 wrong interior", 3, 0, 0, 1);
        l.probe().set(0, 64, -1, Cell.OTHER);
        all.add(l);
        l = shell("5 wrong interior", 5, 0, 0, 1);
        l.probe().set(1, 63, -3, Cell.OTHER);
        all.add(l);
        l = shell("3 two controllers", 3, 0, 0, 1);
        l.probe().set(0, 64, -2, Cell.FISSION_CORE);
        all.add(l);
        l = shell("5 two controllers", 5, 0, 0, 1);
        l.probe().set(2, 64, -2, Cell.FISSION_CORE);
        all.add(l);
        all.add(new Layout("bare core", new GridProbe().set(0, 64, 0, Cell.FISSION_CORE), 0, 64, 0, 0, 0, 1));
        l = shell("5 unloaded corner", 5, 0, 0, 1);
        l.probe().unload(-2, 62, -4);
        all.add(l);
        l = shell("3 unloaded", 3, 0, 0, 1);
        l.probe().unload(-1, 63, -2);
        all.add(l);
        l = shell("5 shell around a 3 shell", 5, 0, 0, 1);
        l.probe().cube(new Bounds(-1, 63, -3, 1, 65, -1), Cell.FISSION_CASING);
        all.add(l);
        l = shell("3 rod in the wall", 3, 0, 0, 1);
        l.probe().set(-1, 64, -1, Cell.CONTROL_ROD);
        all.add(l);
        return all;
    }

    @Test
    void sharedShapeMatchesTheLegacyAlgorithmOnEveryLayout() {
        List<Layout> layouts = layouts();
        assertEquals(20, layouts.size());
        int formed = 0;
        for (Layout l : layouts) {
            Legacy old = legacy(l.probe(), l.x(), l.y(), l.z(), l.fx(), l.fy(), l.fz());
            ShapeResult now = ReactorShape.evaluate(l.probe(), l.x(), l.y(), l.z(), l.fx(), l.fy(), l.fz());
            assertEquals(old.valid(), now.valid(), l.name() + " valid");
            assertEquals(old.size(), now.size(), l.name() + " size");
            assertEquals(old.rods(), ReactorShape.rods(now), l.name() + " rods");
            assertEquals(old.bounds(), now.bounds(), l.name() + " bounds");
            assertEquals(old.cx(), now.centreX(), l.name() + " cx");
            assertEquals(old.cy(), now.centreY(), l.name() + " cy");
            assertEquals(old.cz(), now.centreZ(), l.name() + " cz");
            if (now.valid()) {
                formed++;
            }
        }
        assertTrue(formed >= 8, "the fixture set exercises formed shells too: " + formed);
    }

    @Test
    void sevenShellNeedsTheControlComputer() {
        Layout l = shell("7", 7, 0, 0, 1);
        ShapeResult without = ReactorShape.evaluate(l.probe(), 0, 64, 0, 0, 0, 1);
        assertFalse(without.valid() && without.size() == 7, "no computer, no 7 shell");
        l.probe().set(3, 64, -3, Cell.REACTOR_CONTROL_COMPUTER); // east wall, face centre
        ShapeResult with = ReactorShape.evaluate(l.probe(), 0, 64, 0, 0, 0, 1);
        assertTrue(with.valid());
        assertEquals(7, with.size());
        assertEquals(1, with.count(Cell.REACTOR_CONTROL_COMPUTER));
    }

    @Test
    void portsSitOnFacesNeverEdges() {
        Layout l = shell("5 port", 5, 0, 0, 1);
        l.probe().set(2, 64, -2, Cell.REACTOR_STEAM_PORT);
        assertTrue(ReactorShape.evaluate(l.probe(), 0, 64, 0, 0, 0, 1).valid());
        l.probe().set(2, 66, -2, Cell.REACTOR_STEAM_PORT); // an edge
        assertFalse(ReactorShape.evaluate(l.probe(), 0, 64, 0, 0, 0, 1).valid());
    }
}

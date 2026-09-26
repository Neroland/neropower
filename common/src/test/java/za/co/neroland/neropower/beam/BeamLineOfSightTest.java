package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.beam.BeamPath.Cell;

/**
 * Locks beam line-of-sight break / restore through {@link BeamMath#clear} over a real
 * {@link BeamPath} walk, with a toy world of solid cells: a clear line passes, one solid cell
 * breaks it, removing that cell restores it, and endpoints / relays / unloaded cells never block
 * ({@link BeamMath#blocks}). Pure JVM.
 */
class BeamLineOfSightTest {

    /** A toy world: solid cells, relay (beam endpoint) cells, and unloaded cells. */
    private static final class World {
        final Set<Cell> solid = new HashSet<>();
        final Set<Cell> relays = new HashSet<>();
        final Set<Cell> unloaded = new HashSet<>();

        boolean blocked(Cell cell) {
            boolean loaded = !unloaded.contains(cell);
            boolean relay = relays.contains(cell);
            boolean air = !solid.contains(cell) && !relay;
            return BeamMath.blocks(loaded, air, relay);
        }
    }

    private static final List<Cell> PATH = BeamPath.cellsBetween(0, 64, 0, 20, 64, 0);

    @Test
    void clearLinePasses() {
        World world = new World();
        assertTrue(BeamMath.clear(PATH, world::blocked));
    }

    @Test
    void oneSolidCellBreaksTheBeamAndRemovingItRestores() {
        World world = new World();
        Cell wall = new Cell(10, 64, 0);
        world.solid.add(wall);
        assertFalse(BeamMath.clear(PATH, world::blocked), "a block in the beam breaks it");
        world.solid.remove(wall);
        assertTrue(BeamMath.clear(PATH, world::blocked), "breaking the block restores it");
    }

    @Test
    void endpointsAreNeverPartOfThePath() {
        World world = new World();
        world.solid.add(new Cell(0, 64, 0));
        world.solid.add(new Cell(20, 64, 0));
        assertTrue(BeamMath.clear(PATH, world::blocked), "the transmitter and receiver themselves never block");
    }

    @Test
    void relaysInTheLineDoNotShadowTheBeam() {
        World world = new World();
        world.relays.add(new Cell(7, 64, 0));
        assertTrue(BeamMath.clear(PATH, world::blocked));
        assertFalse(BeamMath.blocks(true, false, true));
    }

    @Test
    void unloadedCellsAreNeverInspected() {
        World world = new World();
        Cell far = new Cell(15, 64, 0);
        world.solid.add(far);
        world.unloaded.add(far);
        assertTrue(BeamMath.clear(PATH, world::blocked), "an unloaded cell is not checked, so never blocks");
        assertFalse(BeamMath.blocks(false, false, false));
    }

    @Test
    void solidCellOffTheLineDoesNotBlock() {
        World world = new World();
        world.solid.add(new Cell(10, 65, 0));
        assertTrue(BeamMath.clear(PATH, world::blocked));
    }

    @Test
    void adjacentEndpointsAreAlwaysClear() {
        assertTrue(BeamMath.clear(BeamPath.cellsBetween(0, 0, 0, 1, 0, 0), cell -> true));
    }
}

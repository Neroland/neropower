package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the beam hazard's hit rule ({@link BeamMath#segmentHitsBox}): an entity is hurt only when the
 * line between the two endpoints passes through its box, never because it stands inside the cuboid
 * the ends span. Also bounds the per-pass query walk. Pure JVM.
 */
class BeamHazardTest {

    private static final double R = BeamMath.BEAM_RADIUS;

    /** A player-sized box (0.6 x 1.8) whose feet centre is at (x, y, z). */
    private static boolean hit(double ax, double ay, double az, double bx, double by, double bz,
            double x, double y, double z) {
        return BeamMath.segmentHitsBox(ax, ay, az, bx, by, bz, x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3, R);
    }

    @Test
    void entityOnADiagonalBeamIsHit() {
        // beam from (0.5, 64.5, 0.5) to (200.5, 120.5, 200.5); the point at t = 0.5 is (100.5, 92.5, 100.5)
        assertTrue(hit(0.5, 64.5, 0.5, 200.5, 120.5, 200.5, 100.5, 91.8, 100.5));
    }

    @Test
    void entityThreeBlocksOffADiagonalBeamIsNotHit() {
        assertFalse(hit(0.5, 64.5, 0.5, 200.5, 120.5, 200.5, 103.5, 91.8, 100.5));
        assertFalse(hit(0.5, 64.5, 0.5, 200.5, 120.5, 200.5, 100.5, 91.8, 97.5));
    }

    @Test
    void entityInsideTheCuboidButFarFromTheLineIsNotHit() {
        // a corner of the 200 x 56 x 200 box, well inside the old bounding-box rule
        assertFalse(hit(0.5, 64.5, 0.5, 200.5, 120.5, 200.5, 190.5, 65.0, 10.5));
        assertFalse(hit(0.5, 64.5, 0.5, 200.5, 120.5, 200.5, 10.5, 118.0, 190.5));
    }

    @Test
    void straightBeamHitsWhatStandsInItAndNothingBeside() {
        assertTrue(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, 20.5, 64.0, 0.5));
        assertFalse(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, 20.5, 64.0, 2.5));
        assertFalse(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, 20.5, 66.0, 0.5), "standing above the beam");
    }

    @Test
    void nothingBeyondTheEndsIsHit() {
        assertFalse(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, 45.5, 64.0, 0.5));
        assertFalse(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, -5.5, 64.0, 0.5));
        assertTrue(hit(0.5, 64.5, 0.5, 40.5, 64.5, 0.5, 40.5, 64.0, 0.5), "a box at an end is hit");
    }

    @Test
    void walkIsBoundedAndCapped() {
        assertEquals(1, BeamMath.hazardSteps(0.0));
        assertEquals(1, BeamMath.hazardSteps(16.0));
        assertEquals(2, BeamMath.hazardSteps(16.5));
        assertEquals(24, BeamMath.hazardSteps(384.0), "Phased Array range walks 24 thin boxes");
        assertTrue(BeamMath.HAZARD_CAP > 0 && BeamMath.HAZARD_CAP <= 32);
    }
}

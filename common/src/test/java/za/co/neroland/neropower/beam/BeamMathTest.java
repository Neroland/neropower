package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link BeamMath}: the per-block loss curve, the orbital hop, the delivered / source-cost
 * round trip (the beam never gives energy away), the per-pass budget cap and range. Pure JVM.
 */
class BeamMathTest {

    private static final double EPS = 1e-9;

    @Test
    void lossFactorFollowsThePerBlockCurve() {
        assertEquals(1.0, BeamMath.lossFactor(0, 3), EPS, "zero distance is lossless");
        assertEquals(1.0, BeamMath.lossFactor(200, 0), EPS, "zero loss is lossless");
        assertEquals(0.997, BeamMath.lossFactor(1, 3), EPS, "one block at 3 permille");
        assertEquals(Math.pow(0.997, 128), BeamMath.lossFactor(128, 3), EPS, "default range at default loss");
        assertTrue(BeamMath.lossFactor(128, 3) > 0.68 && BeamMath.lossFactor(128, 3) < 0.69,
                "about 68% survives 128 blocks at 3 permille");
        assertEquals(0.0, BeamMath.lossFactor(1, 1000), EPS, "total loss per block delivers nothing");
        assertEquals(0.0, BeamMath.lossFactor(5, 5000), EPS, "over-range permille clamps to total loss");
        assertEquals(1.0, BeamMath.lossFactor(5, -10), EPS, "negative permille clamps to lossless");
    }

    @Test
    void orbitalHopIsAFlatFactor() {
        assertEquals(0.7, BeamMath.orbitalFactor(300), EPS);
        assertEquals(1.0, BeamMath.orbitalFactor(0), EPS);
        assertEquals(0.0, BeamMath.orbitalFactor(1000), EPS);
        assertEquals(0.0, BeamMath.orbitalFactor(4000), EPS, "clamped");
    }

    @Test
    void deliveredFloorsAndNeverGoesNegative() {
        assertEquals(2000, BeamMath.delivered(2000, 1.0));
        assertEquals(1400, BeamMath.delivered(2000, 0.7));
        assertEquals(1994, BeamMath.delivered(2000, 0.997));
        assertEquals(0, BeamMath.delivered(2000, 0.0));
        assertEquals(0, BeamMath.delivered(0, 0.5));
        assertEquals(0, BeamMath.delivered(-5, 0.5));
        assertEquals(2000, BeamMath.delivered(2000, 1.5), "factor above 1 never amplifies");
    }

    @Test
    void sourceCostRoundsUpAndIsCappedByTheOffer() {
        // Full acceptance: the source pays exactly what it offered.
        assertEquals(2000, BeamMath.sourceCost(BeamMath.delivered(2000, 0.7), 0.7, 2000));
        // Partial acceptance: pay for what was kept, rounded up, never less than the kept amount.
        assertEquals(1000, BeamMath.sourceCost(700, 0.7, 2000));
        assertEquals(2, BeamMath.sourceCost(1, 0.7, 2000), "ceil(1/0.7) = 2");
        assertEquals(700, BeamMath.sourceCost(700, 1.0, 2000), "lossless costs what was kept");
        assertEquals(2000, BeamMath.sourceCost(1999, 0.7, 2000), "never more than offered");
        assertEquals(0, BeamMath.sourceCost(0, 0.7, 2000));
        assertEquals(0, BeamMath.sourceCost(500, 0.0, 2000), "nothing deliverable, nothing owed");
        assertEquals(0, BeamMath.sourceCost(500, 0.7, 0));
    }

    @Test
    void sourceNeverProfits() {
        for (int permille = 0; permille <= 100; permille += 7) {
            double factor = BeamMath.lossFactor(37, permille);
            long sent = 2000;
            long delivered = BeamMath.delivered(sent, factor);
            long cost = BeamMath.sourceCost(delivered, factor, sent);
            assertTrue(cost >= delivered, "cost >= delivered at " + permille);
            assertTrue(cost <= sent, "cost <= sent at " + permille);
        }
    }

    @Test
    void budgetIsTheSmallerOfPerPassAndStored() {
        assertEquals(2000, BeamMath.budget(2000, 50_000));
        assertEquals(300, BeamMath.budget(2000, 300));
        assertEquals(0, BeamMath.budget(2000, 0));
        assertEquals(0, BeamMath.budget(0, 5000));
        assertEquals(0, BeamMath.budget(-1, 5000));
    }

    @Test
    void distanceAndRange() {
        assertEquals(5.0, BeamMath.distance(0, 0, 0, 3, 4, 0), EPS);
        assertEquals(0.0, BeamMath.distance(7, 7, 7, 7, 7, 7), EPS);
        assertTrue(BeamMath.withinRange(0, 0, 0, 128, 0, 0, 128), "inclusive at the limit");
        assertFalse(BeamMath.withinRange(0, 0, 0, 129, 0, 0, 128));
        assertFalse(BeamMath.withinRange(0, 0, 0, 91, 91, 0, 128), "diagonal 128.7 blocks");
        assertFalse(BeamMath.withinRange(0, 0, 0, 0, 0, 0, -1));
    }

    @Test
    void lossPermilleReadout() {
        assertEquals(0, BeamMath.lossPermille(1.0));
        assertEquals(300, BeamMath.lossPermille(0.7));
        assertEquals(1000, BeamMath.lossPermille(0.0));
        assertEquals(1000, BeamMath.lossPermille(-1.0), "clamped");
        assertEquals(0, BeamMath.lossPermille(2.0), "clamped");
    }
}

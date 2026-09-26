package za.co.neroland.neropower.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the fission balance maths: the burn-up curve's parsing and interpolation, the control-rod
 * factor's bounds, the output/heat products, and the poison stall/recover hysteresis through
 * {@link PoisonModel}. Pure JVM — none of it touches a level, item or config.
 */
class FissionMathTest {

    // --- curve --------------------------------------------------------------------

    @Test
    void defaultCurveHitsItsKnotsExactly() {
        assertEquals(1200, FissionMath.outputFactor(0));
        assertEquals(1000, FissionMath.outputFactor(200));
        assertEquals(800, FissionMath.outputFactor(600));
        assertEquals(300, FissionMath.outputFactor(1000));
    }

    @Test
    void interpolatesLinearlyBetweenKnots() {
        assertEquals(1100, FissionMath.outputFactor(100), "halfway from 1200 to 1000");
        assertEquals(900, FissionMath.outputFactor(400), "halfway from 1000 to 800");
        assertEquals(550, FissionMath.outputFactor(800), "halfway from 800 to 300");
    }

    @Test
    void clampsBurnupOutsideTheRange() {
        assertEquals(1200, FissionMath.outputFactor(-50));
        assertEquals(300, FissionMath.outputFactor(5000));
    }

    @Test
    void heatFactorSitsAFixedOffsetAboveOutput() {
        for (int burnup = 0; burnup <= 1000; burnup += 50) {
            assertEquals(FissionMath.outputFactor(burnup) + FissionMath.HEAT_OFFSET, FissionMath.heatFactor(burnup),
                    "burnup " + burnup);
        }
        assertTrue(FissionMath.heatFactor(0) > FissionMath.heatFactor(1000), "fresh rods run hottest");
    }

    @Test
    void parserSkipsGarbageAndSortsKnots() {
        FissionMath.Curve curve = FissionMath.parseCurve(" 1000=100, junk, 0=500 ,=7, 500=abc, 500=300 ");
        assertEquals(3, curve.knots());
        assertEquals(500, curve.at(0));
        assertEquals(300, curve.at(500));
        assertEquals(100, curve.at(1000));
        assertEquals(400, curve.at(250));
    }

    @Test
    void parserFallsBackToTheDefaultWithTooFewKnots() {
        assertEquals(FissionMath.DEFAULT.at(0), FissionMath.parseCurve("").at(0));
        assertEquals(FissionMath.DEFAULT.at(600), FissionMath.parseCurve(null).at(600));
        assertEquals(FissionMath.DEFAULT.at(1000), FissionMath.parseCurve("100=100").at(1000));
    }

    @Test
    void duplicateKnotsKeepTheLastValue() {
        FissionMath.Curve curve = FissionMath.parseCurve("0=100,0=200,1000=0");
        assertEquals(2, curve.knots());
        assertEquals(200, curve.at(0));
    }

    // --- control rods --------------------------------------------------------------

    @Test
    void controlRodFactorFallsLinearlyThenFloors() {
        assertEquals(1.0D, FissionMath.controlRodFactor(0, 27, 150), 1e-9);
        assertEquals(0.85D, FissionMath.controlRodFactor(1, 27, 150), 1e-9);
        assertEquals(0.55D, FissionMath.controlRodFactor(3, 27, 150), 1e-9);
        assertEquals(FissionMath.CONTROL_ROD_FLOOR, FissionMath.controlRodFactor(10, 27, 150), 1e-9);
        assertEquals(FissionMath.CONTROL_ROD_FLOOR, FissionMath.controlRodFactor(27, 27, 1000), 1e-9);
    }

    @Test
    void controlRodCountIsCappedByTheInterior() {
        // A 3³ shell has one interior block: a second assembly cannot exist there.
        assertEquals(FissionMath.controlRodFactor(1, 1, 150), FissionMath.controlRodFactor(5, 1, 150), 1e-9);
        assertEquals(1.0D, FissionMath.controlRodFactor(-3, 27, 150), 1e-9);
        assertEquals(1.0D, FissionMath.controlRodFactor(3, 27, -10), 1e-9);
    }

    @Test
    void controlRodFactorIsAlwaysWithinBounds() {
        for (int rods = 0; rods <= 30; rods++) {
            for (int permille = 0; permille <= 1000; permille += 125) {
                double factor = FissionMath.controlRodFactor(rods, 27, permille);
                assertTrue(factor >= FissionMath.CONTROL_ROD_FLOOR && factor <= 1.0D,
                        "rods=" + rods + " permille=" + permille + " -> " + factor);
            }
        }
    }

    // --- shell geometry -----------------------------------------------------------

    @Test
    void interiorVolumeAndUsableSlots() {
        assertEquals(0, FissionMath.interiorVolume(0));
        assertEquals(1, FissionMath.interiorVolume(3));
        assertEquals(27, FissionMath.interiorVolume(5));
        assertEquals(0, FissionMath.usableRodSlots(0));
        assertEquals(2, FissionMath.usableRodSlots(3));
        assertEquals(4, FissionMath.usableRodSlots(5));
        assertEquals(4, FissionMath.usableRodSlots(7), "never more than the slot count");
    }

    // --- output / heat -------------------------------------------------------------

    @Test
    void outputProductMatchesTheSpec() {
        // 120 NE/rod x (1.2 + 1.0) x 0.85 x 1.0 x 1000/1000 = 224.4 -> 224
        assertEquals(224L, FissionMath.outputPerTick(120, 2.2D, 0.85D, 1.0D, 1000));
        assertEquals(135L, FissionMath.outputPerTick(120, 2.2D, 0.85D, 1.0D, 600), "unstable penalty: 134.64 -> 135");
        assertEquals(0L, FissionMath.outputPerTick(120, 2.2D, 0.85D, 1.0D, 0), "FAILURE yields nothing");
        assertEquals(0L, FissionMath.outputPerTick(120, 0.0D, 1.0D, 1.0D, 1000), "no rods, no power");
    }

    @Test
    void heatIsAtLeastOneWhileAnyRodIsLoaded() {
        assertEquals(0, FissionMath.heatPerTick(6, 0.0D, 1.0D));
        assertEquals(1, FissionMath.heatPerTick(6, 0.01D, 0.15D));
        assertEquals(8, FissionMath.heatPerTick(6, 1.4D, 1.0D), "6 x 1.4 = 8.4 -> 8");
    }

    // --- poison ---------------------------------------------------------------------

    @Test
    void poisonBuildsStallsAndRecoversWithHysteresis() {
        PoisonModel model = new PoisonModel();
        int stall = 900;
        // Build at 2/tick: 450 ticks to the stall line.
        for (int i = 0; i < 449; i++) {
            assertFalse(model.tick(true, 2, 3, stall), "tick " + i);
        }
        assertTrue(model.tick(true, 2, 3, stall), "reaches 900 on tick 450");
        assertEquals(900, model.poison());
        // Stalled: accumulation is ignored, poison decays at 3/tick, and the core stays stalled
        // until it reaches the recovery line (600) — 100 ticks.
        for (int i = 0; i < 99; i++) {
            assertTrue(model.tick(true, 2, 3, stall), "still in the pit at tick " + i);
        }
        assertFalse(model.tick(true, 2, 3, stall), "out of the pit at 600");
        assertEquals(PoisonModel.recoveryLine(stall), model.poison());
        // Back to accumulating from 600: nowhere near the stall line the next tick.
        assertFalse(model.tick(true, 2, 3, stall));
        assertEquals(602, model.poison());
    }

    @Test
    void poisonNeverLeavesItsRange() {
        PoisonModel model = new PoisonModel();
        for (int i = 0; i < 2000; i++) {
            model.tick(true, 50, 0, 1000);
            assertTrue(model.poison() <= 1000);
        }
        for (int i = 0; i < 2000; i++) {
            model.tick(false, 50, 50, 1000);
            assertTrue(model.poison() >= 0);
        }
        assertEquals(0, model.poison());
        assertFalse(model.stalled());
    }

    @Test
    void poisonDecaysWhenNotAccumulating() {
        PoisonModel model = new PoisonModel();
        for (int i = 0; i < 100; i++) {
            model.tick(true, 2, 3, 900);
        }
        assertEquals(200, model.poison());
        model.tick(false, 2, 3, 900);
        assertEquals(197, model.poison());
    }

    @Test
    void recoveryLineFloorsAtZero() {
        assertEquals(600, PoisonModel.recoveryLine(900));
        assertEquals(0, PoisonModel.recoveryLine(100));
    }

    @Test
    void loadRestoresStateSilently() {
        PoisonModel model = new PoisonModel();
        model.load(950, true);
        assertTrue(model.stalled());
        assertEquals(950, model.poison());
        model.load(-5, false);
        assertEquals(0, model.poison());
        model.load(5000, false);
        assertEquals(1000, model.poison());
    }
}

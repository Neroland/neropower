package za.co.neroland.neropower.environmental;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Locks {@link StirlingMath}: the cold-face halving of the usable gradient, the capped and floored
 * per-tick draw, and the bonus-scaled output. Pure JVM.
 */
class StirlingMathTest {

    @Test
    void usableGradientHalvesWithoutAColdFace() {
        assertEquals(200, StirlingMath.usableGradient(200, true));
        assertEquals(100, StirlingMath.usableGradient(200, false));
        assertEquals(0, StirlingMath.usableGradient(-40, true), "a colder-than-ambient neighbour yields nothing");
        assertEquals(0, StirlingMath.usableGradient(1, false), "integer halving rounds down");
    }

    @Test
    void drawIsAShareOfTheGradient() {
        // 200 gradient × 100‰ = 20, well under a cap of 100.
        assertEquals(20, StirlingMath.drawFor(200, 100, 100));
        assertEquals(50, StirlingMath.drawFor(200, 250, 100));
    }

    @Test
    void drawIsCappedAtMaxDraw() {
        assertEquals(8, StirlingMath.drawFor(200, 100, 8));
        assertEquals(8, StirlingMath.drawFor(10_000, 1000, 8));
    }

    @Test
    void drawIsFlooredAtOneWhileAnyGradientExists() {
        assertEquals(1, StirlingMath.drawFor(3, 100, 8), "0.3 rounds up to the floor of 1");
        assertEquals(0, StirlingMath.drawFor(0, 100, 8));
        assertEquals(0, StirlingMath.drawFor(-5, 100, 8));
        assertEquals(0, StirlingMath.drawFor(200, 0, 8), "no draw share, no draw");
        assertEquals(0, StirlingMath.drawFor(200, 100, 0), "no cap, no draw");
    }

    @Test
    void outputScalesWithHeatDrawn() {
        assertEquals(120, StirlingMath.outputFor(8, 15, 0));
        assertEquals(15, StirlingMath.outputFor(1, 15, 0));
        assertEquals(0, StirlingMath.outputFor(0, 15, 0));
        assertEquals(0, StirlingMath.outputFor(8, 0, 0));
    }

    @Test
    void coldFaceBonusRaisesOutput() {
        assertEquals(180, StirlingMath.outputFor(8, 15, 500), "+50%");
        assertEquals(240, StirlingMath.outputFor(8, 15, 1000), "+100%");
        assertEquals(120, StirlingMath.outputFor(8, 15, -500), "a negative bonus reads as none");
    }

    @Test
    void endToEndDefaultsAgainstAHotReactor() {
        // Defaults: 100‰ draw, cap 8, 15 NE/unit, +500‰ with a cold face. A reactor 300 above ambient:
        int usable = StirlingMath.usableGradient(300, true);
        int draw = StirlingMath.drawFor(usable, 100, 8);
        assertEquals(8, draw, "30 wanted, capped at 8");
        assertEquals(180, StirlingMath.outputFor(draw, 15, 500));
        // The same reactor without a cold face: 150 usable -> 15 wanted -> still capped at 8, no bonus.
        int dryDraw = StirlingMath.drawFor(StirlingMath.usableGradient(300, false), 100, 8);
        assertEquals(8, dryDraw);
        assertEquals(120, StirlingMath.outputFor(dryDraw, 15, 0));
    }
}

package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Rule test for the Momentum Wheel: it loses {@code lossPermillePerSecond} of its charge
 * every second, at least 1 NE so it always comes to rest, never below zero; nothing is added; the renderer
 * bucket is fill / 50 permille. Pure JVM.
 */
class MomentumWheelDecayTest {

    @Test
    void defaultLossIsTwoPermilleASecond() {
        assertEquals(4_000L, MomentumMath.lossPerSecond(2_000_000L, 2));
        assertEquals(2_000L, MomentumMath.lossPerSecond(1_000_000L, 2));
    }

    @Test
    void lossIsOffAtZeroAndNothingIsLostFromEmpty() {
        assertEquals(0L, MomentumMath.lossPerSecond(1_000_000L, 0));
        assertEquals(0L, MomentumMath.lossPerSecond(0L, 2));
    }

    @Test
    void aSmallChargeStillSpinsDownToRest() {
        long stored = 2_000_000L;
        int seconds = 0;
        while (stored > 0 && seconds < 100_000) {
            long loss = MomentumMath.lossPerSecond(stored, 2);
            assertTrue(loss >= 1 && loss <= stored, "loss is positive and never exceeds the charge");
            stored -= loss;
            seconds++;
        }
        assertEquals(0L, stored, "the wheel comes to rest");
        assertTrue(seconds > 1_000, "but slowly: 0.2 %/s");
    }

    @Test
    void chargeBucketIsFillOverFiftyPermille() {
        assertEquals(0, MomentumMath.chargeBucket(0L, 2_000_000L));
        assertEquals(10, MomentumMath.chargeBucket(1_000_000L, 2_000_000L));
        assertEquals(20, MomentumMath.chargeBucket(2_000_000L, 2_000_000L));
        assertEquals(0, MomentumMath.chargeBucket(5L, 0L));
    }
}

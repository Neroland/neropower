package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the {@link Explode#effectiveRadius} clamp: the machine's request, never above the config cap
 * ({@code failureRadiusCap}), never below 1. Pure JVM — the helper is static and touches no level,
 * block state or config.
 */
class ExplodeRadiusTest {

    @Test
    void requestUnderTheCapIsHonoured() {
        assertEquals(4, Explode.effectiveRadius(4, 8));
        assertEquals(8, Explode.effectiveRadius(8, 8), "exactly at the cap is fine");
    }

    @Test
    void capClampsLargeRequests() {
        assertEquals(8, Explode.effectiveRadius(12, 8));
        assertEquals(1, Explode.effectiveRadius(12, 1), "the config minimum is a 1-block blast");
    }

    @Test
    void neverBelowOneEvenForDegenerateInput() {
        assertEquals(1, Explode.effectiveRadius(0, 8), "a zero request still blasts 1");
        assertEquals(1, Explode.effectiveRadius(-5, 8));
        assertEquals(1, Explode.effectiveRadius(4, 0), "an out-of-range cap can't zero the blast");
    }

    @Test
    void monotoneInTheRequestUpToTheCap() {
        int cap = 8;
        int previous = Explode.effectiveRadius(1, cap);
        for (int requested = 2; requested <= 20; requested++) {
            int current = Explode.effectiveRadius(requested, cap);
            assertTrue(current >= previous, "radius must never shrink as the request grows (" + requested + ")");
            assertTrue(current <= cap, "radius must never exceed the cap (" + requested + ")");
            previous = current;
        }
    }
}

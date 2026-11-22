package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;

/**
 * Rule test for the Storage Ring: starved of cryo coolant its warmth climbs the shared failure
 * ladder one rung at a time (WARNING, UNSTABLE halves the I/O, FAILURE), the quench loses half the charge
 * (drain default), locks the ring out, and it re-arms only after the lockout and once cold again. No blast:
 * the core has no terrain path at all. Pure JVM, driven the way the controller drives it.
 */
class SmesQuenchTest {

    private static final int WARMING = 2;
    private static final int COOLING = 5;
    private static final int LOCKOUT = 1_200;

    private static SmesCore charged() {
        SmesCore core = new SmesCore();
        core.applyStructure(true, 16, 50_000_000L);
        core.beginTick(Long.MAX_VALUE);
        core.insert(600_000_000L, false);
        return core;
    }

    /** One controller tick; returns the stage. */
    private static FailureStage tick(SmesCore core, FailureController ladder, boolean starved) {
        core.tickWarmth(starved, WARMING, COOLING);
        FailureStage stage = ladder.tick(core.warmth(), SmesCore.MAX_WARMTH, true);
        if (stage == FailureStage.FAILURE && ladder.justEntered()) {
            core.quench(500, LOCKOUT);
        }
        FailureThresholds t = ladder.thresholds();
        core.tickLockout(t.warningPermille() - t.hysteresisPermille());
        return stage;
    }

    @Test
    void starvedRingQuenchesThroughEveryRung() {
        SmesCore core = charged();
        FailureController ladder = new FailureController(FailureThresholds.DEFAULTS);
        boolean sawWarning = false;
        boolean sawUnstable = false;
        int ticks = 0;
        while (!core.quenched() && ticks < 5_000) {
            FailureStage stage = tick(core, ladder, true);
            sawWarning |= stage == FailureStage.WARNING;
            if (stage == FailureStage.UNSTABLE) {
                sawUnstable = true;
                assertEquals(16_000L, core.ioLimit(2_000, 200_000, true), "I/O halves at UNSTABLE");
            }
            ticks++;
        }
        assertTrue(sawWarning && sawUnstable, "telegraphed on every rung");
        assertTrue(core.quenched());
        assertEquals(FailureStage.FAILURE, ladder.stage());
        assertEquals(300_000_000L, core.energy(), "drain quench loses half the charge");
        assertFalse(core.online());
        assertEquals(0L, core.ioLimit(2_000, 200_000, false), "offline while locked out");
    }

    @Test
    void cooledRingNeverWarms() {
        SmesCore core = charged();
        FailureController ladder = new FailureController(FailureThresholds.DEFAULTS);
        for (int i = 0; i < 10_000; i++) {
            assertEquals(FailureStage.STABLE, tick(core, ladder, false));
        }
        assertEquals(600_000_000L, core.energy());
        assertEquals(0, core.warmth());
    }

    @Test
    void rearmsOnlyAfterLockoutAndOnceCold() {
        SmesCore core = charged();
        FailureController ladder = new FailureController(FailureThresholds.DEFAULTS);
        while (!core.quenched()) {
            tick(core, ladder, true);
        }
        // Still starved: the lockout runs out but the warm coils keep it offline.
        for (int i = 0; i < LOCKOUT + 10; i++) {
            tick(core, ladder, true);
        }
        assertTrue(core.quenched(), "warm coils do not re-arm");
        // Coolant back: it cools below WARNING - hysteresis and re-arms.
        int ticks = 0;
        while (core.quenched() && ticks < 2_000) {
            tick(core, ladder, false);
            ticks++;
        }
        assertFalse(core.quenched());
        assertTrue(core.warmth() < 550);
        assertTrue(core.online());
        assertEquals(300_000_000L, core.energy(), "no second loss, nothing restored");
    }

    @Test
    void quenchLossIsBoundedAndNeverNegative() {
        SmesCore core = charged();
        core.quench(1_000, 0);
        assertEquals(0L, core.energy());
        core.quench(500, 0);
        assertEquals(0L, core.energy());
    }
}

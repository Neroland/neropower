package za.co.neroland.neropower.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;

/**
 * The turbine's distinctive rules on the plain JVM, at decision-005 defaults: rotor inertia (spin-up and
 * coast-down), the overspeed trip through the shared failure ladder, the stator ceiling, the governor and
 * the condenser's vacuum behaviour.
 */
class TurbineRulesTest {

    private static final int NE_PER_MB = 8;
    private static final int UP = 3;
    private static final int DOWN = 1;
    private static final int TRIP = 2100;

    /** Two cindersteel vane slices (140 mB/t at 650) and one tier 2 stator slice (8 x 40 = 320 NE/t). */
    private static TurbineMath.Rotor rotor() {
        return TurbineMath.Rotor.of(new int[] {140}, new int[] {650}, 8 * 40);
    }

    @Test
    void spinUpTakesThirtySecondsAndCoastDownNinety() {
        int rpm = 0;
        int ticks = 0;
        while (rpm < TurbineMath.RATED_RPM) {
            rpm = TurbineMath.stepRpm(rpm, TurbineMath.RATED_RPM, UP, DOWN);
            ticks++;
        }
        assertEquals(600, ticks, "0 to 1800 at 3 rpm/t");
        ticks = 0;
        while (rpm > 0) {
            rpm = TurbineMath.stepRpm(rpm, 0, UP, DOWN);
            ticks++;
        }
        assertEquals(1800, ticks, "1800 to 0 at 1 rpm/t");
    }

    @Test
    void outputScalesWithSpeedAndACoastingRotorMakesNothing() {
        assertEquals(0L, TurbineMath.output(300, 0, 1000));
        assertEquals(150L, TurbineMath.output(300, 900, 1000));
        assertEquals(300L, TurbineMath.output(300, 1800, 1000));
        assertEquals(300L, TurbineMath.output(300, 2200, 1000), "no bonus above rated speed");
        // No steam: plan is idle and the target is 0, so nothing is extracted while the rotor coasts.
        TurbineMath.Plan idle = TurbineMath.plan(rotor(), 0, 16_000, false, 600, NE_PER_MB, 100_000, false, false);
        assertEquals(0L, idle.extracted());
        assertEquals(0, idle.targetRpm());
    }

    @Test
    void statorCeilingCapsOutputAndTheSurplusOverspeeds() {
        // 140 mB x 8 x 0.65 = 728 NE potential, but the stators take 320.
        TurbineMath.Plan plan = TurbineMath.plan(rotor(), 10_000, 16_000, false, 600, NE_PER_MB, 100_000, false, false);
        assertEquals(140L, plan.steamPassed());
        assertEquals(728L, plan.potential());
        assertEquals(320L, plan.extracted(), "coil ceiling");
        assertEquals(TurbineMath.MAX_TARGET_RPM, plan.targetRpm(), "1800 x 728 / 320 capped at 2400");

        // Enough stators: rated speed, full conversion.
        TurbineMath.Rotor big = TurbineMath.Rotor.of(new int[] {140}, new int[] {650}, 8 * 100);
        TurbineMath.Plan fine = TurbineMath.plan(big, 10_000, 16_000, false, 600, NE_PER_MB, 100_000, false, false);
        assertEquals(728L, fine.extracted());
        assertEquals(TurbineMath.RATED_RPM, fine.targetRpm());
    }

    @Test
    void overspeedClimbsTheLadderWithTelegraphAndTrips() {
        assertEquals(0, TurbineMath.overspeedPermille(1800, TRIP));
        assertEquals(600, TurbineMath.overspeedPermille(1980, TRIP));
        assertEquals(800, TurbineMath.overspeedPermille(2040, TRIP));
        assertEquals(1000, TurbineMath.overspeedPermille(2100, TRIP));

        FailureController ladder = new FailureController(FailureThresholds.DEFAULTS);
        int rpm = TurbineMath.RATED_RPM;
        boolean sawWarning = false;
        boolean sawUnstable = false;
        int ticks = 0;
        FailureStage stage = FailureStage.STABLE;
        while (stage != FailureStage.FAILURE && ticks < 2_000) {
            rpm = TurbineMath.stepRpm(rpm, TurbineMath.MAX_TARGET_RPM, UP, DOWN);
            stage = ladder.tick(TurbineMath.overspeedPermille(rpm, TRIP), FailureThresholds.PERMILLE, true);
            sawWarning |= stage == FailureStage.WARNING;
            sawUnstable |= stage == FailureStage.UNSTABLE;
            ticks++;
        }
        assertEquals(FailureStage.FAILURE, stage);
        assertTrue(sawWarning && sawUnstable, "every rung is visible before the trip");
        assertTrue(ticks >= 2 * FailureThresholds.DEFAULTS.minDwellTicks(), "dwell gives time to react");

        // Tripped: the plan closes the inlets and the rotor coasts; reset only below 600 rpm.
        TurbineMath.Plan tripped = TurbineMath.plan(rotor(), 10_000, 16_000, false, 600, NE_PER_MB, 100_000, false, true);
        assertEquals(0L, tripped.steamPassed());
        assertEquals(0, tripped.targetRpm());
        assertFalse(TurbineMath.canReset(rpm));
        assertTrue(TurbineMath.canReset(599));
    }

    @Test
    void governorHoldsRatedSpeedAndBleedsTheSurplus() {
        TurbineMath.Plan plan = TurbineMath.plan(rotor(), 10_000, 16_000, false, 600, NE_PER_MB, 100_000, true, false);
        assertTrue(plan.targetRpm() <= TurbineMath.RATED_RPM);
        assertEquals(140L, plan.steamPassed(), "the inlet keeps flowing (the reactor never backs up)");
        assertTrue(plan.steamUsed() < plan.steamPassed());
        assertTrue(plan.potential() <= 320L, "the governed potential fits the stators");
        assertEquals(plan.potential(), plan.extracted());
    }

    @Test
    void exhaustWithNowhereToGoStallsAndVentingLosesYield() {
        TurbineMath.Plan blocked = TurbineMath.plan(rotor(), 10_000, 0, false, 600, NE_PER_MB, 100_000, false, false);
        assertEquals(0L, blocked.steamPassed());
        TurbineMath.Plan vented = TurbineMath.plan(rotor(), 10_000, 0, true, 600, NE_PER_MB, 100_000, false, false);
        assertEquals(140L, vented.vented());
        assertEquals(600, vented.exhaustPermille());
        assertEquals(436L, vented.potential(), "140 x 8 x 0.65 x 0.6");
    }

    @Test
    void steamModeYieldMatchesDecisionTable() {
        long steamPerRod = 3_000_000L;
        assertEquals(13_200_000L, TurbineMath.potential(steamPerRod, NE_PER_MB, 550, 1000));
        assertEquals(15_600_000L, TurbineMath.potential(steamPerRod, NE_PER_MB, 650, 1000));
        assertEquals(18_000_000L, TurbineMath.potential(steamPerRod, NE_PER_MB, 750, 1000));
        assertEquals(10_800_000L, TurbineMath.potential(steamPerRod, NE_PER_MB, 750, 600));
    }

    @Test
    void condenserInVacuumNeedsRadiatorsOrCryo() {
        int vacuum = TurbineMath.atmosphereFactor(TurbineMath.DEFAULT_ATMOSPHERE_FACTORS, "VACUUM");
        assertEquals(0, vacuum);
        long air = TurbineMath.airRate(200, vacuum);
        TurbineMath.Condense none = TurbineMath.condense(1_000, 16_000, air, 0, 0, 20);
        assertEquals(0L, none.condensed());
        assertEquals(TurbineMath.SINK_NONE, none.sink());

        TurbineMath.Condense radiators = TurbineMath.condense(1_000, 16_000, air, 2 * 30, 0, 20);
        assertEquals(60L, radiators.condensed());
        assertEquals(TurbineMath.SINK_RADIATOR, radiators.sink());

        TurbineMath.Condense cryo = TurbineMath.condense(1_000, 16_000, air, 0, 5, 20);
        assertEquals(100L, cryo.condensed(), "5 mB coolant x 20");
        assertEquals(5L, cryo.cryoUsed());
        assertEquals(TurbineMath.SINK_CRYO, cryo.sink());
    }

    @Test
    void condenserOnAirSpendsCoolantLastAndIsOneToOne() {
        assertEquals(1000, TurbineMath.atmosphereFactor(TurbineMath.DEFAULT_ATMOSPHERE_FACTORS, "EARTHLIKE"));
        assertEquals(600, TurbineMath.atmosphereFactor(TurbineMath.DEFAULT_ATMOSPHERE_FACTORS, "SULFUROUS"));
        assertEquals(1000, TurbineMath.atmosphereFactor(TurbineMath.DEFAULT_ATMOSPHERE_FACTORS, "SOMETHING_NEW"));
        TurbineMath.Condense c = TurbineMath.condense(150, 16_000, 200, 30, 100, 20);
        assertEquals(150L, c.condensed(), "never more water than exhaust");
        assertEquals(0L, c.cryoUsed(), "air covered it");
        TurbineMath.Condense full = TurbineMath.condense(150, 40, 200, 0, 0, 20);
        assertEquals(40L, full.condensed(), "bounded by water room");
    }
}

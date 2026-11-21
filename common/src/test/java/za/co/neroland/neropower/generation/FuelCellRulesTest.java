package za.co.neroland.neropower.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The Hydrogen Fuel Cell's distinctive rules: it breathes the air at exactly half rate where the
 * atmosphere is Earth-like or nitrogen-oxygen (and nowhere else), oxygen in the tank gives full rate at
 * one unit per two of hydrogen, and with no hydrogen there is no output at all.
 */
class FuelCellRulesTest {

    private static final int TICKS = 20;
    private static final int NE = 200;
    private static final int WATER = 50;
    private static final long BIG = Long.MAX_VALUE / 4;

    /** Totals of a run of {@code ticks} ticks with the given tanks. */
    private record Run(long ne, long hydrogenUsed, long oxygenUsed, long water, int lastCause, int lastMode) {
    }

    private static Run run(long hydrogen, long oxygen, boolean breathable, int ticks) {
        FuelCellCore core = new FuelCellCore();
        long h = hydrogen;
        long o = oxygen;
        long ne = 0;
        long water = 0;
        FuelCellCore.Step step = null;
        for (int i = 0; i < ticks; i++) {
            step = core.tick(h, o, breathable, BIG, BIG, TICKS, NE, WATER);
            h -= step.drainHydrogen();
            o -= step.drainOxygen();
            water += step.fillWater();
            ne += step.generate();
        }
        return new Run(ne, hydrogen - h, oxygen - o, water,
                step == null ? 0 : step.cause(), step == null ? FuelCellCore.MODE_NONE : step.mode());
    }

    @Test
    void breathableOnlyForEarthlikeAndNitrogenOxygen() {
        assertTrue(FuelCellCore.breathable("EARTHLIKE"));
        assertTrue(FuelCellCore.breathable("NITROGEN_OXYGEN"));
        for (String air : new String[] {"VACUUM", "THIN_CO2", "SULFUROUS", "NITROGEN_METHANE"}) {
            assertFalse(FuelCellCore.breathable(air), air);
        }
        assertFalse(FuelCellCore.breathable(null));
    }

    @Test
    void oxygenFedRunsAtFullRateOnOneOxygenPerTwoHydrogen() {
        int units = 10;
        Run r = run(units * 100L, units * 50L, false, units * TICKS);
        assertEquals((long) units * NE, r.ne(), "200 NE per hydrogen unit");
        assertEquals(units * 100L, r.hydrogenUsed());
        assertEquals(units * 50L, r.oxygenUsed(), "one oxygen unit for every two of hydrogen");
        assertEquals((long) units * WATER, r.water());
        assertEquals(NE / TICKS, r.ne() / (units * TICKS), "10 NE/t");
    }

    @Test
    void breathableAirGivesHalfRateAndUsesNoOxygen() {
        int ticks = 40 * TICKS;
        Run fed = run(BIG, BIG, true, ticks);
        Run air = run(BIG, 0L, true, ticks);
        assertEquals(fed.ne(), air.ne() * 2, "air is exactly half rate");
        assertEquals(0L, air.oxygenUsed());
        assertEquals(fed.hydrogenUsed(), air.hydrogenUsed() * 2, "half the hydrogen per tick on air");
        assertEquals(FuelCellCore.MODE_AIR, air.lastMode());
        // Same NE per unit of hydrogen either way: air is slower, not wasteful.
        assertEquals(fed.ne() * 100L / fed.hydrogenUsed(), air.ne() * 100L / air.hydrogenUsed());
    }

    @Test
    void unbreathableAirWithoutOxygenStops() {
        for (String atmosphere : new String[] {"VACUUM", "THIN_CO2", "SULFUROUS", "NITROGEN_METHANE"}) {
            Run r = run(BIG, 0L, FuelCellCore.breathable(atmosphere), 200);
            assertEquals(0L, r.ne(), atmosphere);
            assertEquals(0L, r.hydrogenUsed(), atmosphere + ": no hydrogen is burnt without oxygen");
            assertEquals(GenerationReadout.CAUSE_NO_OXYGEN, r.lastCause(), atmosphere);
        }
    }

    @Test
    void noOutputWithoutHydrogen() {
        for (boolean breathable : new boolean[] {true, false}) {
            Run r = run(0L, BIG, breathable, 500);
            assertEquals(0L, r.ne());
            assertEquals(0L, r.oxygenUsed());
            assertEquals(0L, r.water());
            assertEquals(GenerationReadout.CAUSE_NO_FUEL, r.lastCause());
        }
        // Less than a whole unit does not start a charge either.
        assertEquals(0L, run(99L, BIG, true, 100).ne());
    }

    @Test
    void fullBufferPausesTheChargeWithoutLosingIt() {
        FuelCellCore core = new FuelCellCore();
        long ne = core.tick(100, 50, false, BIG, BIG, TICKS, NE, WATER).generate();
        for (int i = 0; i < 5; i++) {
            FuelCellCore.Step held = core.tick(0, 0, false, BIG, 0L, TICKS, NE, WATER);
            assertEquals(0, held.generate());
            assertEquals(GenerationReadout.CAUSE_BUFFER_FULL, held.cause());
        }
        for (int i = 0; i < TICKS * 2; i++) {
            ne += core.tick(0, 0, false, BIG, BIG, TICKS, NE, WATER).generate();
        }
        assertEquals(NE, ne, "the paused charge still pays its whole 200 NE");
    }

    @Test
    void fullWaterTankStopsBeforeBurning() {
        FuelCellCore core = new FuelCellCore();
        FuelCellCore.Step step = core.tick(BIG, BIG, true, WATER - 1, BIG, TICKS, NE, WATER);
        assertEquals(0L, step.drainHydrogen());
        assertEquals(GenerationReadout.CAUSE_OUTPUT_FULL, step.cause());
    }

    @Test
    void tickOutputSumsExactly() {
        for (int total : new int[] {0, 1, 7, 199, 200, 1001}) {
            for (int ticks : new int[] {1, 3, 20, 40, 77}) {
                long sum = 0;
                for (int i = 0; i < ticks; i++) {
                    sum += FuelCellCore.tickOutput(total, ticks, i);
                }
                assertEquals(total, sum, total + " over " + ticks);
            }
        }
    }
}

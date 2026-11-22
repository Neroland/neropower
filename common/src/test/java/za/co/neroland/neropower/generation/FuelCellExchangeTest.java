package za.co.neroland.neropower.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerotech.machine.MediumTransfer;

import za.co.neroland.neropower.turbine.PortTransfer;

/**
 * The Hydrogen Fuel Cell's pipe-less exchange: a cell with an abutting hydrogen / oxygen supply and an
 * abutting plain water tank keeps running long past the 80 units its own water tank holds, because every
 * {@code portTransferInterval} ticks it pushes water out of its I/O faces (auto-eject) and pulls gas in
 * through its auto-input faces, by {@link PortTransfer}'s rules through NeroTech's conserving
 * {@link MediumTransfer#move}. Without the exchange the same cell stops at 80 units. No mB is created.
 */
class FuelCellExchangeTest {

    private static final int TICKS_PER_UNIT = 20;
    private static final int NE = 60;
    private static final int WATER = 50;
    private static final long TANK = 4_000L;
    private static final int INTERVAL = PortTransfer.DEFAULT_INTERVAL;

    /** A plain bounded tank. */
    private static final class Tank implements MediumTransfer.Store {
        final long capacity;
        long amount;

        Tank(long capacity, long amount) {
            this.capacity = capacity;
            this.amount = amount;
        }

        @Override
        public long drain(long max, boolean simulate) {
            long d = Math.min(max, this.amount);
            if (!simulate) {
                this.amount -= d;
            }
            return d;
        }

        @Override
        public long fill(long amount, boolean simulate) {
            long f = Math.min(amount, this.capacity - this.amount);
            if (!simulate) {
                this.amount += f;
            }
            return f;
        }
    }

    private record Result(long units, long water, long drained, long hydrogenLeft, long supplyLeft) {
    }

    private static Result run(boolean exchange, int ticks) {
        FuelCellCore core = new FuelCellCore();
        Tank hydrogen = new Tank(TANK, 0L);
        Tank oxygen = new Tank(TANK, 0L);
        Tank water = new Tank(TANK, 0L);
        Tank h2Supply = new Tank(1_000_000L, 1_000_000L);
        Tank o2Supply = new Tank(1_000_000L, 1_000_000L);
        Tank drain = new Tank(1_000_000L, 0L);
        long units = 0;
        // the abutting supply is the only gas source
        for (int t = 0; t < ticks; t++) {
            FuelCellCore.Step step = core.tick(hydrogen.amount, oxygen.amount, false, water.capacity - water.amount,
                    Long.MAX_VALUE / 4, TICKS_PER_UNIT, NE, WATER);
            hydrogen.drain(step.drainHydrogen(), false);
            oxygen.drain(step.drainOxygen(), false);
            assertEquals(step.fillWater(), water.fill(step.fillWater(), false), "the cell never makes water it cannot hold");
            if (step.drainHydrogen() > 0) {
                units++;
            }
            if (PortTransfer.due(t, PortTransfer.phase(7, INTERVAL), INTERVAL)) {
                long budget = PortTransfer.budget(INTERVAL);
                // gas always arrives (a neighbour pushing it); only the water push is under test
                if (exchange && PortTransfer.pushes(SideMode.IO, true, false, true)) {
                    MediumTransfer.move(water, drain, budget);
                }
                if (PortTransfer.pulls(SideMode.IO, true)) {
                    MediumTransfer.move(h2Supply, hydrogen, budget);
                    MediumTransfer.move(o2Supply, oxygen, budget);
                }
            }
        }
        return new Result(units, water.amount, drain.amount, hydrogen.amount, h2Supply.amount);
    }

    @Test
    void withoutTheExchangeTheCellStopsAtEightyUnits() {
        Result r = run(false, 400 * TICKS_PER_UNIT);
        assertEquals(TANK / WATER, r.units(), "4000 mB of water at 50 mB a unit");
        assertEquals(TANK, r.water(), "the water tank is full and nothing takes it");
    }

    @Test
    void withAnAbuttingSupplyAndDrainItRunsPastEightyUnits() {
        int units = 400;
        Result r = run(true, units * TICKS_PER_UNIT + 2 * INTERVAL);
        assertTrue(r.units() >= units - 1, "ran " + r.units() + " units, not stuck at 80");
        assertTrue(r.water() < TANK, "the water tank never fills");
        assertEquals(r.units() * WATER, r.water() + r.drained(), "every mB of water is either in the cell or the drain");
        assertEquals(1_000_000L - r.units() * FuelCellCore.GAS_UNIT_MB, r.supplyLeft() + r.hydrogenLeft(),
                "hydrogen is only moved, never made");
    }
}

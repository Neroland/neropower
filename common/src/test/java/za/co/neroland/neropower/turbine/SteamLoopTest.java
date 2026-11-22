package za.co.neroland.neropower.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.nerotech.machine.MediumTransfer;

import za.co.neroland.neropower.fission.SteamBoil;

/**
 * End-to-end rule: a steam-mode reactor runs a closed loop with <b>no pipe</b>. Reactor
 * Steam Port A abuts a Turbine Steam Port inlet, the turbine's exhaust port abuts a Steam Condenser, and
 * the condenser abuts Reactor Steam Port B. Every tick the core boils (shared tanks, {@link SteamBoil}), the
 * turbine passes steam to exhaust ({@link TurbineMath#plan}), the condenser condenses
 * ({@link TurbineMath#condense}); every {@code portTransferInterval} ticks, each block at its own phase,
 * the faces exchange by {@link PortTransfer}'s rules through NeroTech's conserving
 * {@link MediumTransfer#move}. Asserted over N intervals: the reactor never stalls and not one mB is
 * created or lost. The same layout without shared tanks stalls.
 */
class SteamLoopTest {

    private static final String WATER = "minecraft:water";
    private static final String CRYO = "nerotech:cryo_coolant";
    private static final String STEAM = "neropower:steam";
    private static final String EXHAUST = "neropower:exhaust_steam";

    /** Six rods at 6 heat/t, 4 mB steam per heat: 144 mB/t. */
    private static final int HEAT = 36;
    private static final int STEAM_PER_HEAT = 4;
    private static final long TANK = 16_000L;
    private static final int INTERVAL = PortTransfer.DEFAULT_INTERVAL;
    private static final int TICKS = 400 * INTERVAL;

    /** What a face shows on the fluid surface: fills go to {@code in}, drains come from {@code out}. */
    private record Face(List<TestTank> in, List<TestTank> out) {
        long fill(String fluid, long amount, boolean simulate) {
            return this.in.isEmpty() ? 0L : PortPool.fill(this.in, fluid, amount, simulate);
        }

        long drain(long amount, boolean simulate) {
            return this.out.isEmpty() ? 0L : PortPool.drain(this.out, amount, simulate);
        }

        String fluid() {
            return this.out.isEmpty() ? null : PortPool.fluid(this.out);
        }
    }

    private static MediumTransfer.Store pool(List<TestTank> tanks, String fluid) {
        return new MediumTransfer.Store() {
            @Override
            public long drain(long max, boolean simulate) {
                return PortPool.drainFluid(tanks, fluid, max, simulate);
            }

            @Override
            public long fill(long amount, boolean simulate) {
                return PortPool.fill(tanks, fluid, amount, simulate);
            }
        };
    }

    private static MediumTransfer.Store face(Face face, String fluid) {
        return new MediumTransfer.Store() {
            @Override
            public long drain(long max, boolean simulate) {
                return fluid.equals(face.fluid()) ? face.drain(max, simulate) : 0L;
            }

            @Override
            public long fill(long amount, boolean simulate) {
                return face.fill(fluid, amount, simulate);
            }
        };
    }

    /** A port or condenser pushing {@code own} into {@code neighbour} on its default I/O face, as {@code PortExchange.push}. */
    private static long push(List<TestTank> own, Face neighbour, boolean guarded) {
        String fluid = PortPool.fluid(own);
        if (fluid == null) {
            return 0L;
        }
        boolean takesWater = guarded && neighbour.fill(WATER, 1L, true) > 0L;
        if (!PortTransfer.pushes(SideMode.IO, true, guarded, takesWater)) {
            return 0L;
        }
        return MediumTransfer.move(pool(own, fluid), face(neighbour, fluid), PortTransfer.budget(INTERVAL));
    }

    /** A reactor port pulling coolant from {@code neighbour}, as {@code PortExchange.pull}. */
    private static long pullCoolant(List<TestTank> own, Face neighbour) {
        String fluid = neighbour.fluid();
        if (!PortTransfer.pulls(SideMode.IO, true) || fluid == null || !(WATER.equals(fluid) || CRYO.equals(fluid))) {
            return 0L;
        }
        return MediumTransfer.move(face(neighbour, fluid), pool(own, fluid), PortTransfer.budget(INTERVAL));
    }

    private static long sum(List<TestTank> tanks) {
        long s = 0L;
        for (TestTank t : tanks) {
            s += t.amount();
        }
        return s;
    }

    private static final class Plant {
        final TestTank coolantA = TestTank.of(TANK, WATER, CRYO);
        final TestTank coolantB = TestTank.of(TANK, WATER, CRYO).with(WATER, TANK);
        final TestTank steamA = TestTank.of(TANK, STEAM);
        final TestTank steamB = TestTank.of(TANK, STEAM);
        final TestTank inlet = TestTank.of(TANK, STEAM);
        final TestTank exhaust = TestTank.of(TANK, EXHAUST);
        final TestTank condExhaust = TestTank.of(TANK, EXHAUST);
        final TestTank condCryo = TestTank.of(TANK, CRYO);
        final TestTank condWater = TestTank.of(TANK, WATER);

        final boolean shared;
        final List<TestTank> all;
        long boiled;

        Plant(boolean shared) {
            this.shared = shared;
            this.all = List.of(this.coolantA, this.coolantB, this.steamA, this.steamB, this.inlet, this.exhaust,
                    this.condExhaust, this.condCryo, this.condWater);
        }

        List<TestTank> coolant(boolean portA) {
            return this.shared ? List.of(this.coolantA, this.coolantB) : List.of(portA ? this.coolantA : this.coolantB);
        }

        List<TestTank> steam(boolean portA) {
            return this.shared ? List.of(this.steamA, this.steamB) : List.of(portA ? this.steamA : this.steamB);
        }

        // Faces as the neighbours see them.
        Face turbineInlet() {
            return new Face(List.of(this.inlet), List.of());
        }

        Face turbineExhaust() {
            return new Face(List.of(), List.of(this.exhaust));
        }

        Face condenser() {
            return new Face(List.of(this.condExhaust, this.condCryo), List.of(this.condWater));
        }

        Face reactorPort(boolean portA) {
            return new Face(coolant(portA), steam(portA));
        }

        long total() {
            return sum(this.all);
        }

        /** One tick; returns the heat that found no water or no steam room (a stall when > 0). */
        int tick(long time, TurbineMath.Rotor rotor) {
            // The core: with shared tanks one boil over the pool; without, one per port as before shared tanks.
            int residual = HEAT;
            List<List<TestTank>[]> groups = new java.util.ArrayList<>();
            if (this.shared) {
                groups.add(pair(coolant(true), steam(true)));
            } else {
                groups.add(pair(coolant(true), steam(true)));
                groups.add(pair(coolant(false), steam(false)));
            }
            for (List<TestTank>[] g : groups) {
                SteamBoil boil = SteamBoil.boil(residual, STEAM_PER_HEAT, 1_000,
                        PortPool.amountOf(g[0], WATER), PortPool.roomFor(g[1], STEAM));
                if (boil.heatTaken() <= 0) {
                    continue;
                }
                assertEquals(boil.waterUsed(), PortPool.drainFluid(g[0], WATER, boil.waterUsed(), false));
                assertEquals(boil.steam(), PortPool.fill(g[1], STEAM, boil.steam(), false));
                assertEquals(boil.waterUsed(), boil.steam(), "at full output one mB of water is one mB of steam");
                residual -= boil.heatTaken();
                this.boiled += boil.waterUsed();
            }

            // The turbine: inlet steam passes the rotor into the exhaust tank, 1:1.
            List<TestTank> in = List.of(this.inlet);
            List<TestTank> out = List.of(this.exhaust);
            TurbineMath.Plan plan = TurbineMath.plan(rotor, PortPool.amount(in), PortPool.roomFor(out, EXHAUST), false,
                    600, 8, Long.MAX_VALUE / 4, false, false);
            assertEquals(plan.steamPassed(), plan.condensed(), "no vent port: every mB passed is exhaust");
            assertEquals(plan.steamPassed(), PortPool.drain(in, plan.steamPassed(), false));
            assertEquals(plan.condensed(), PortPool.fill(out, EXHAUST, plan.condensed(), false));

            // The condenser: air-cooled at 200 mB/t, exhaust to water 1:1.
            TurbineMath.Condense c = TurbineMath.condense(this.condExhaust.amount(),
                    TANK - this.condWater.amount(), 200L, 0L, 0L, 1);
            if (c.condensed() > 0L) {
                long drained = this.condExhaust.slotDrain(c.condensed(), false);
                assertEquals(drained, this.condWater.slotFill(WATER, drained, false));
            }

            // Pipe-less exchanges, each block at its own phase.
            if (PortTransfer.due(time, PortTransfer.phase(11, INTERVAL), INTERVAL)) { // reactor port A
                push(steam(true), turbineInlet(), true);
                pullCoolant(coolant(true), turbineInlet());
            }
            if (PortTransfer.due(time, PortTransfer.phase(22, INTERVAL), INTERVAL)) { // reactor port B
                push(steam(false), condenser(), true);
                pullCoolant(coolant(false), condenser());
            }
            if (PortTransfer.due(time, PortTransfer.phase(33, INTERVAL), INTERVAL)) { // turbine exhaust port
                push(List.of(this.exhaust), condenser(), true);
            }
            if (PortTransfer.due(time, PortTransfer.phase(44, INTERVAL), INTERVAL)) { // condenser
                push(List.of(this.condWater), reactorPort(false), false);
                push(List.of(this.condWater), turbineExhaust(), false);
            }
            return residual;
        }

        @SuppressWarnings("unchecked")
        private static List<TestTank>[] pair(List<TestTank> a, List<TestTank> b) {
            return new List[] {a, b};
        }
    }

    private static TurbineMath.Rotor rotor() {
        // Three cindersteel vane slices pass 210 mB/t, more than the reactor raises.
        return TurbineMath.Rotor.of(new int[] {70, 70, 70}, new int[] {650, 650, 650}, 100_000L);
    }

    @Test
    void closedLoopRunsWithNoPipeAndConservesEveryMillibucket() {
        Plant plant = new Plant(true);
        long start = plant.total();
        assertEquals(TANK, start);
        TurbineMath.Rotor rotor = rotor();
        int stalledTicks = 0;
        for (long t = 0L; t < TICKS; t++) {
            int residual = plant.tick(t, rotor);
            if (residual > 0) {
                stalledTicks++;
            }
            assertEquals(start, plant.total(), "tick " + t + ": pooling and exchange create or void nothing");
        }
        assertEquals(0, stalledTicks, "the reactor never stalls over " + (TICKS / INTERVAL) + " intervals");
        // The loop really circulates: the condenser's water came back through port B many times over.
        assertEquals((long) HEAT * STEAM_PER_HEAT * TICKS, plant.boiled);
        assertTrue(plant.boiled > 10L * TANK, "boiled " + plant.boiled + " mB, more than ten times the plant's water");
    }

    @Test
    void theSameLayoutWithoutSharedTanksStalls() {
        Plant plant = new Plant(false);
        long start = plant.total();
        TurbineMath.Rotor rotor = rotor();
        int stalledTicks = 0;
        for (long t = 0L; t < TICKS; t++) {
            if (plant.tick(t, rotor) > 0) {
                stalledTicks++;
            }
            assertEquals(start, plant.total());
        }
        assertTrue(stalledTicks > TICKS / 2, "port A has no water and port B no outlet: the unpooled layout stalls");
    }
}

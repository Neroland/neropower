package za.co.neroland.neropower.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.neropower.beam.BeamMath;
import za.co.neroland.neropower.beam.SplitMath;
import za.co.neroland.neropower.energy.PortableEnergyMath;
import za.co.neroland.neropower.environmental.StirlingMath;
import za.co.neroland.neropower.generation.GenerationConfig;
import za.co.neroland.neropower.grid.LoadBankMath;
import za.co.neroland.neropower.storage.MomentumMath;
import za.co.neroland.neropower.storage.PoolMember;
import za.co.neroland.neropower.storage.PooledEnergyStorage;
import za.co.neroland.neropower.storage.SmesCore;
import za.co.neroland.neropower.turbine.TurbineConfig;
import za.co.neroland.neropower.turbine.TurbineMath;

/**
 * Energy accounting (Stage 3): no loop through NeroPower's machines gives back more NE than went in, using the
 * real rule classes, the config defaults and the config bounds. Each part is a closed loop or a store that is
 * filled and emptied: Load Bank to Stirling (the only path from NE back to NE through heat, at every preset,
 * Speed-module count and cold-face bonus), Momentum Wheel, Surge Bank pool, Storage Ring, power cells over FE,
 * Beam Splitter chains, the Phased Array, the Steam Condenser and the turbine.
 *
 * <p>Not loops by construction, so not simulated here: steam comes only from fuel. The core raises steam from
 * the heat its rods make this tick ({@code FissionMath.steamModeHeat}); heat stored in it or conducted into it
 * from a Load Bank never boils water, so a Load Bank cannot feed a turbine.
 */
class EnergyAccountingTest {

    static final long SEED = 0x5EED_0400L;

    // --- Load Bank -> heat -> Stirling ------------------------------------------------------------------

    /** NE a Stirling recovers from what a Load Bank burns, as a fraction, simulated over {@code ticks}. */
    static double loadBankToStirling(int loadBankNePerHeat, int stirlingNePerUnit, int bonusConfig,
            int loadBankPresetHeat, double stirlingSpeed, long rate, int ticks) {
        int divisor = LoadBankMath.nePerHeat(loadBankNePerHeat, stirlingNePerUnit, bonusConfig);
        // The coldest planet doubles the configured bonus (StirlingMath caps the planet scale at +100%).
        int bonus = StirlingMath.coldFaceBonusPermille(bonusConfig, -10_000, true);
        long burned = 0L;
        long recovered = 0L;
        long remainder = 0L;
        long stored = 0L;
        for (int t = 0; t < ticks; t++) {
            long[] heat = LoadBankMath.heat(rate, remainder, divisor);
            remainder = heat[1];
            burned += rate;
            int adjusted = LoadBankMath.presetAdjusted(heat[0], loadBankPresetHeat);
            if (adjusted > 0) {
                // NeroTech's addHeat: x preset heat permille, floor 1.
                stored += Math.max(1, (long) adjusted * loadBankPresetHeat / 1000L);
            }
            // The Stirling draws as fast as it may (it is the only sink; no loss to ambient is assumed).
            int draw = StirlingMath.drawFor((int) Math.min(Integer.MAX_VALUE, stored), 1000, 1000, stirlingSpeed);
            int drawn = (int) Math.min(stored, draw);
            stored -= drawn;
            recovered += StirlingMath.outputFor(drawn, stirlingNePerUnit, bonus);
        }
        // Whatever heat is left over is credited as if it were drawn too (the most a Stirling could ever get).
        recovered += StirlingMath.outputFor((int) Math.min(Integer.MAX_VALUE, stored), stirlingNePerUnit, bonus);
        return recovered / (double) burned;
    }

    @Test
    void aLoadBankStirlingLoopAlwaysLosesAtEveryBound() {
        int[] loadBank = {1, 100, 1_000_000};
        int[] stirling = {1, 15, 1_000};
        int[] bonuses = {0, 500, 1_000};
        int[] presetHeat = {500, 1_000, 2_000};
        double[] speeds = {0.75D, 1.0D, 1.5D, 8.0D * 1.5D};
        long[] rates = {1L, 37L, 20_000L, 1_000_000L};
        double worst = 0.0D;
        for (int lb : loadBank) {
            for (int st : stirling) {
                for (int bonus : bonuses) {
                    for (int heat : presetHeat) {
                        for (double speed : speeds) {
                            for (long rate : rates) {
                                double back = loadBankToStirling(lb, st, bonus, heat, speed, rate, 200);
                                worst = Math.max(worst, back);
                                assertTrue(back < 0.5D, "lb=" + lb + " stirling=" + st + " bonus=" + bonus
                                        + " preset=" + heat + " speed=" + speed + " rate=" + rate + ": " + back);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(worst > 0.0D, "the loop was never exercised");
    }

    @Test
    void speedDrawsHeatFasterButNeverMakesMoreNeFromIt() {
        // Before Stage 3 the Stirling multiplied its NE by preset x Speed modules (up to 12x): a Load Bank
        // loop at a profit. Speed now scales the draw only.
        int heat = 10_000;
        int perUnit = 15;
        for (double speed : new double[] {0.75D, 1.0D, 1.5D, 12.0D}) {
            int left = heat;
            long out = 0L;
            while (left > 0) {
                int drawn = Math.min(left, StirlingMath.drawFor(left, 100, 8, speed));
                left -= drawn;
                out += StirlingMath.outputFor(drawn, perUnit, 0);
            }
            assertEquals((long) heat * perUnit, out, "speed " + speed);
        }
        assertTrue(StirlingMath.drawFor(10_000, 100, 8, 12.0D) > StirlingMath.drawFor(10_000, 100, 8, 1.0D));
    }

    // --- stores -------------------------------------------------------------------------------------------

    @Test
    void theMomentumWheelOnlyEverLoses() {
        ConfigProbe.allValues();
        Random r = new Random(SEED);
        for (int loss : new int[] {0, 1, 2, 100}) {
            long capacity = 2_000_000L;
            long stored = 0L;
            long in = 0L;
            long out = 0L;
            for (int second = 0; second < 2_000; second++) {
                long add = Math.min(capacity - stored, r.nextInt(50_000));
                stored += add;
                in += add;
                stored -= MomentumMath.lossPerSecond(stored, loss);
                long take = Math.min(stored, r.nextInt(40_000));
                stored -= take;
                out += take;
                assertTrue(stored >= 0L && stored <= capacity);
            }
            out += stored;
            assertTrue(out <= in, "loss " + loss + ": out " + out + " > in " + in);
            if (loss == 0) {
                assertEquals(in, out, "a lossless wheel gives back exactly what it took");
            }
        }
    }

    /** A pool member on the plain JVM. */
    record Member(EnergyBuffer buffer, int io, boolean fast) implements PoolMember {
        static Member of(int capacity, int io, boolean fast, int stored) {
            EnergyBuffer b = new EnergyBuffer(capacity, io, io, () -> { });
            b.setRaw(stored);
            return new Member(b, io, fast);
        }

        @Override
        public NeroEnergyStorage poolBuffer() {
            return this.buffer;
        }

        @Override
        public long poolIo() {
            return this.io;
        }

        @Override
        public boolean fastTier() {
            return this.fast;
        }

        @Override
        public BlockPos getBlockPos() {
            return null;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }
    }

    @Test
    void aSurgeBankPoolConservesThroughRescansInsertsAndExtracts() {
        ConfigProbe.allValues();
        Random r = new Random(SEED);
        List<Member> all = List.of(Member.of(500_000, 1_000, false, 12_345), Member.of(2_000_000, 4_000, false, 0),
                Member.of(200_000, 50_000, true, 199_999), Member.of(200_000, 50_000, true, 7),
                Member.of(8_000_000, 16_000, false, 4_000_000));
        long start = all.stream().mapToLong(m -> m.buffer().getAmount()).sum();
        PooledEnergyStorage pool = new PooledEnergyStorage(() -> 64_000L, () -> 256_000L);
        long in = 0L;
        long out = 0L;
        for (int step = 0; step < 5_000; step++) {
            if (step % 7 == 0) {
                List<Member> subset = new ArrayList<>();
                for (Member m : all) {
                    if (r.nextBoolean()) {
                        subset.add(m);
                    }
                }
                pool.setMembers(subset);
            }
            in += pool.insert(r.nextInt(300_000), false);
            out += pool.extract(r.nextInt(300_000), false);
            long held = all.stream().mapToLong(m -> m.buffer().getAmount()).sum();
            assertEquals(start + in - out, held, "step " + step);
        }
    }

    @Test
    void aStorageRingNeverHoldsMoreThanWentIn() {
        Random r = new Random(SEED);
        SmesCore core = new SmesCore();
        core.applyStructure(true, 16, 50_000_000L);
        long in = 0L;
        long out = 0L;
        long lost = 0L;
        for (int step = 0; step < 5_000; step++) {
            core.beginTick(r.nextInt(400_000));
            in += core.insert(r.nextInt(400_000), false);
            out += core.extract(r.nextInt(300_000), false);
            core.tickLockout(1_000);
            switch (r.nextInt(40)) {
                case 0 -> lost += core.applyStructure(false, 0, 50_000_000L);
                case 1 -> lost += core.applyStructure(true, 4 + r.nextInt(53), 50_000_000L);
                case 2 -> lost += core.quench(500, 0);
                default -> {
                }
            }
            assertTrue(core.energy() >= 0L && core.energy() <= core.capacity() || !core.formed());
            assertEquals(in - out - lost, core.energy(), "step " + step + ": the ring's books do not balance");
        }
    }

    @Test
    void powerCellsNeverGainOverAnFeRoundTrip() {
        Random r = new Random(SEED);
        double[] ratios = {0.1D, 0.25D, 1.0D / 3.0D, 1.0D, 2.5D, 3.0D, 7.0D, 10.0D};
        for (double ratio : ratios) {
            long capacity = 4_000_000L;
            long io = 8_000L;
            long stored = 0L;
            long feIn = 0L;
            long feOut = 0L;
            for (int step = 0; step < 2_000; step++) {
                long[] charge = PortableEnergyMath.chargeFromFe(stored, capacity, io, 1 + r.nextInt(50_000), ratio);
                stored += charge[0];
                feIn += charge[1];
                // PortableEnergy.extractFe: what can leave, floored to FE, then its NE cost rounded up.
                long ne = PortableEnergyMath.givable(stored, io, Long.MAX_VALUE);
                long fe = Math.min(1 + r.nextInt(50_000), PortableEnergyMath.neToFeFloor(ne, ratio));
                if (fe > 0) {
                    long cost = PortableEnergyMath.feToNeCeil(fe, ratio);
                    if (cost <= ne) {
                        stored -= cost;
                        feOut += fe;
                    }
                }
                assertTrue(stored >= 0L && stored <= capacity);
            }
            feOut += PortableEnergyMath.neToFeFloor(stored, ratio);
            assertTrue(feOut <= feIn, "ratio " + ratio + ": " + feOut + " FE out of " + feIn + " in");
        }
    }

    // --- beams --------------------------------------------------------------------------------------------

    @Test
    void beamSplitterChainsAndLoopsOnlyLose() {
        Random r = new Random(SEED);
        for (int lens = 0; lens <= 50; lens += 10) {
            for (int perBlock : new int[] {0, 1, 3, 100}) {
                long circulating = 1_000_000L;
                long start = circulating;
                // A ring of splitters sending to each other: every hop is a beam shot plus a lens.
                for (int hop = 0; hop < 200 && circulating > 0; hop++) {
                    double factor = BeamMath.lossFactor(1 + r.nextInt(128), perBlock);
                    long delivered = BeamMath.delivered(circulating, factor);
                    long cost = BeamMath.sourceCost(delivered, factor, circulating);
                    assertTrue(cost >= delivered, "a shot never costs less than it lands");
                    long afterLens = delivered - SplitMath.lensLoss(delivered, lens);
                    int[] shares = {r.nextInt(1_001), r.nextInt(1_001), r.nextInt(1_001), r.nextInt(1_001)};
                    long sent = 0L;
                    for (long part : SplitMath.allocate(afterLens, shares)) {
                        sent += part;
                    }
                    assertTrue(sent <= afterLens, "a splitter hands on no more than it holds");
                    circulating = sent + (afterLens - sent); // what it keeps stays in its buffer
                    assertTrue(circulating <= start);
                    start = circulating;
                }
            }
        }
    }

    @Test
    void thePhasedArrayDeliversNoMoreThanItSends() {
        for (int perBlock = 0; perBlock <= 100; perBlock += 25) {
            for (int distance = 1; distance <= 1_024; distance *= 2) {
                double factor = BeamMath.lossFactor(distance, perBlock);
                long sent = 8_000L;
                long landed = BeamMath.delivered(sent, factor);
                assertTrue(landed <= sent);
                assertTrue(BeamMath.sourceCost(landed, factor, sent) >= landed);
            }
        }
    }

    // --- steam ---------------------------------------------------------------------------------------------

    @Test
    void theCondenserReturnsWaterOneForOneAndMakesNoEnergy() {
        Random r = new Random(SEED);
        for (int step = 0; step < 2_000; step++) {
            long exhaust = r.nextInt(20_000);
            TurbineMath.Condense c = TurbineMath.condense(exhaust, r.nextInt(20_000), r.nextInt(400),
                    r.nextInt(300), r.nextInt(1_000), 1 + r.nextInt(1_000));
            assertTrue(c.condensed() <= exhaust, "no more water than exhaust");
            assertTrue(c.condensed() >= 0L && c.cryoUsed() >= 0L);
        }
    }

    @Test
    void aTurbineNeverMakesMoreThanItsSteamCarries() {
        ConfigProbe.allValues();
        Random r = new Random(SEED);
        int nePerMb = TurbineConfig.nePerSteamMb();
        for (int step = 0; step < 20_000; step++) {
            TurbineMath.Rotor rotor = TurbineMath.Rotor.of(new int[] {1 + r.nextInt(500), r.nextInt(200)},
                    new int[] {1 + r.nextInt(1_000), r.nextInt(1_000)}, r.nextInt(5_000));
            long steam = r.nextInt(2_000);
            TurbineMath.Plan plan = TurbineMath.plan(rotor, steam, r.nextInt(2_000), r.nextBoolean(),
                    r.nextInt(1_001), nePerMb, r.nextInt(10_000), r.nextBoolean(), false);
            long output = TurbineMath.output(plan.extracted(), r.nextInt(2_401), 1000);
            assertTrue(plan.steamPassed() <= steam);
            assertTrue(output <= plan.steamUsed() * nePerMb, "output beyond the steam's NE");
            if (steam == 0L) {
                assertEquals(0L, output, "a coasting rotor with no steam makes nothing");
            }
        }
    }

    @Test
    void theFuelCellIsANetSinkAgainstNeroTechsElectrolyzer() {
        ConfigProbe.allValues();
        // NeroTech defaults: 40 NE/t x 200 t per operation for 200 mB of hydrogen (2 units) and 1 unit of oxygen.
        double electrolyzerPerUnit = 40.0D * 200.0D / 2.0D;
        assertTrue(GenerationConfig.fuelCellNePerUnit() < electrolyzerPerUnit,
                "a fuel cell must return less than electrolysis costs");
    }

    /**
     * NeroTech 0.8.0's Electrolyzer at its cheapest per hydrogen unit (a read-only fixture of its defaults and
     * Core's upgrade floors): 40 NE/t, Efficiency floor x0.25, Eco preset x0.5 energy, 8 Speed modules take the
     * 200 t operation to 33 t, and one operation makes 2 units. 40 x 0.25 x 0.5 x 33 / 2 = 82.5 NE per unit.
     */
    static final double ELECTROLYZER_CHEAPEST_NE_PER_UNIT = 40.0D * 0.25D * 0.5D * 33.0D / 2.0D;

    @Test
    void theFuelCellLosesEvenAgainstAFullyUpgradedElectrolyzer() {
        @SuppressWarnings("unchecked")
        za.co.neroland.nerolandcore.config.ConfigValue<Integer> yield =
                (za.co.neroland.nerolandcore.config.ConfigValue<Integer>) ConfigProbe.value("fuelCellNePerHydrogenUnit");
        int max = ConfigProbe.sanitize(yield, Integer.MAX_VALUE);
        assertEquals(60, (int) GenerationConfig.fuelCellNePerUnit(), "the default yield is 60 NE");
        assertEquals(70, max, "the yield is bounded at 70 NE");
        assertTrue(max < 0.85D * ELECTROLYZER_CHEAPEST_NE_PER_UNIT,
                "the bound must stay below 85 % of the cheapest electrolysis, so the loop loses energy");
        for (int ne = 1; ne <= max; ne++) {
            assertTrue(ne < ELECTROLYZER_CHEAPEST_NE_PER_UNIT, "a loop at " + ne + " NE per unit makes energy");
        }
        ConfigProbe.set("fuelCellNePerHydrogenUnit", "1000000");
        try {
            assertEquals(70, (int) GenerationConfig.fuelCellNePerUnit(), "a hand-edited huge yield clamps to 70");
        } finally {
            ConfigProbe.reset("fuelCellNePerHydrogenUnit");
        }
    }
}

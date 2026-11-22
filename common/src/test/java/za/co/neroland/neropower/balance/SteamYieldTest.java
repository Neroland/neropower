package za.co.neroland.neropower.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.fission.FissionConfig;
import za.co.neroland.neropower.fission.FissionMath;
import za.co.neroland.neropower.turbine.TurbineConfig;
import za.co.neroland.neropower.turbine.TurbineMath;
import za.co.neroland.neropower.turbine.VaneTier;

/**
 * Steam mode against direct mode, per rod, through the real rule classes and config defaults:
 * a rod's whole life is simulated tick by tick as the fission core runs it ({@code FissionMath} burn-up,
 * output, steam-mode heat and steam) and the steam goes through {@code TurbineMath.plan} / {@code output} at
 * rated speed, either condensed (exhaust ports to a condenser) or vented.
 */
class SteamYieldTest {

    /** {direct-or-steam NE over one rod's life}. */
    static long life(boolean steam, double speed, boolean economizer, boolean braided, VaneTier vanes,
            boolean condensed) {
        ConfigProbe.allValues();
        FissionMath.Curve curve = FissionMath.parseCurve(FissionConfig.burnupCurve());
        double scale = braided ? FissionConfig.braidedOutputPermille() / 1000.0D : 1.0D;
        double econ = economizer ? FissionConfig.economizerBurnupPermille() / 1000.0D : 1.0D;
        double burnRate = FissionConfig.burnupPerTick() * speed * econ
                * (braided ? FissionConfig.braidedBurnupPermille() / 1000.0D : 1.0D);
        long step = Math.max(1L, Math.round(burnRate));
        int outputPermille = economizer ? FissionConfig.economizerOutputPermille() : 1000;
        int eff = TurbineConfig.vaneEfficiency(vanes);
        TurbineMath.Rotor rotor = TurbineMath.Rotor.of(new int[] {1_000_000}, new int[] {eff}, Long.MAX_VALUE / 4);
        int nePerMb = TurbineConfig.nePerSteamMb();
        long total = 0L;
        long acc = 0L;
        int burnup = 0;
        while (burnup < FissionMath.MAX_BURNUP) {
            double rodSum = FissionMath.outputFactor(curve, burnup) / 1000.0D * scale;
            double heatSum = FissionMath.heatFactor(curve, burnup) / 1000.0D * scale;
            if (!steam) {
                long out = FissionMath.outputPerTick(FissionConfig.nePerRod(), rodSum, 1.0D, speed, 1000);
                total += out * outputPermille / 1000;
            } else {
                int heat = FissionMath.steamModeHeat(FissionConfig.heatPerRodPerTick(), heatSum, 1.0D, speed);
                long mb = FissionMath.steamFromHeat(heat, FissionConfig.steamPerHeat(), outputPermille);
                TurbineMath.Plan plan = TurbineMath.plan(rotor, mb, condensed ? mb : 0L, !condensed,
                        TurbineConfig.ventEfficiencyPermille(), nePerMb, Long.MAX_VALUE / 4, false, false);
                total += TurbineMath.output(plan.extracted(), TurbineMath.RATED_RPM, 1000);
            }
            acc += step;
            burnup += (int) (acc / 1000L);
            acc %= 1000L;
        }
        return total;
    }

    static double ratio(VaneTier vanes, boolean condensed, double speed, boolean economizer, boolean braided) {
        return life(true, speed, economizer, braided, vanes, condensed)
                / (double) life(false, speed, economizer, braided, vanes, condensed);
    }

    @Test
    void theRowsOfDecision005() {
        // Direct: 120 NE/t x 800 permille x 125 000 t = 12.0 M NE per rod.
        assertEquals(12_000_000L, life(false, 1.0D, false, false, VaneTier.STEEL, true), 12_000_000L * 0.005D);
        List<Object[]> rows = List.of(
                new Object[] {VaneTier.STEEL, 1.10D, 0.66D},
                new Object[] {VaneTier.CINDERSTEEL, 1.30D, 0.78D},
                new Object[] {VaneTier.SUPERCONDUCTING, 1.50D, 0.90D});
        for (Object[] row : rows) {
            VaneTier tier = (VaneTier) row[0];
            assertEquals((double) row[1], ratio(tier, true, 1.0D, false, false), 0.015D, tier + " condensed");
            assertEquals((double) row[2], ratio(tier, false, 1.0D, false, false), 0.015D, tier + " vented");
        }
    }

    @Test
    void steamWinsOnlyWithATurbineAndACondenser() {
        for (VaneTier tier : List.of(VaneTier.STEEL, VaneTier.CINDERSTEEL, VaneTier.SUPERCONDUCTING)) {
            assertTrue(ratio(tier, true, 1.0D, false, false) > 1.0D, tier + " condensed must beat direct");
            assertTrue(ratio(tier, false, 1.0D, false, false) < 1.0D, tier + " vented must lose to direct");
        }
        // No turbine: a core in steam mode makes no NE itself, and steam with nowhere to go converts nothing.
        assertEquals(TurbineMath.Plan.IDLE, TurbineMath.plan(TurbineMath.Rotor.NONE, 1_000L, 1_000L, true, 600, 8,
                1_000_000L, false, false));
        TurbineMath.Plan stalled = TurbineMath.plan(TurbineMath.Rotor.of(new int[] {110}, new int[] {750}, 800L),
                1_000L, 0L, false, 600, 8, 1_000_000L, false, false);
        assertEquals(0L, stalled.steamPassed(), "exhaust with no condenser and no vent stalls the inlet");
        assertEquals(0L, stalled.potential());
    }

    @Test
    void theRatioDoesNotMoveWithPresetSpeedModulesEconomizerOrBraidedRods() {
        // Burn-up follows the work rate, so a rod's steam must too (the Stage 3 steam-mode-heat fix: before
        // it, an Eco core raised a third more steam per rod).
        double base = ratio(VaneTier.SUPERCONDUCTING, true, 1.0D, false, false);
        for (double speed : new double[] {0.75D, 1.5D, 3.0D, 12.0D}) {
            assertEquals(base, ratio(VaneTier.SUPERCONDUCTING, true, speed, false, false), 0.01D, "speed " + speed);
        }
        assertEquals(base, ratio(VaneTier.SUPERCONDUCTING, true, 1.0D, true, false), 0.01D, "economizer");
        assertEquals(base, ratio(VaneTier.SUPERCONDUCTING, true, 1.0D, false, true), 0.01D, "braided rod");
        assertEquals(base, ratio(VaneTier.SUPERCONDUCTING, true, 0.75D, true, true), 0.01D, "all at once");
    }

    @Test
    void steamNeverYieldsMoreThanTwiceDirectAtAnyConfig() {
        // The steam bound: at the best vanes (1000 permille) and a condensed exhaust, the turbine's
        // effective NE per mB is capped so steam per rod stays at or under 2 x direct per rod.
        List<String> curves = List.of(FissionMath.DEFAULT_CURVE, "0=1000,1000=1000", "0=0,1000=0", "0=5000,1000=0",
                "0=0,999=1,1000=0");
        int[] nePerRod = {1, 120, 100_000};
        int[] heatPerRod = {0, 1, 6, 10_000};
        int[] steamPerHeat = {1, 4, 64};
        int[] configured = {1, 8, 100};
        for (String raw : curves) {
            FissionMath.Curve curve = FissionMath.parseCurve(raw);
            for (int ne : nePerRod) {
                for (int heat : heatPerRod) {
                    for (int sph : steamPerHeat) {
                        int cap = FissionMath.steamNePerMbCap(curve, ne, heat, sph);
                        for (int c : configured) {
                            int effective = Math.max(0, Math.min(c, cap));
                            long[] life = FissionMath.rodLife(curve, ne, heat);
                            long steamNe = TurbineMath.potential(life[1] * sph, effective, 1000, 1000);
                            assertTrue(steamNe <= 2L * life[0], raw + " ne=" + ne + " heat=" + heat + " sph=" + sph
                                    + " nePerMb=" + c + ": steam " + steamNe + " > 2 x direct " + life[0]);
                        }
                    }
                }
            }
        }
    }

    @Test
    void theDefaultsSitOnTheCapAndAnExtremeConfigIsClamped() {
        ConfigProbe.allValues();
        assertEquals(8, TurbineConfig.nePerSteamMb(), "the shipped 8 NE/mB is within the cap");
        try {
            ConfigProbe.set("turbineNePerSteamMb", "100");
            int capped = TurbineConfig.nePerSteamMb();
            assertEquals(100, TurbineConfig.configuredNePerSteamMb());
            assertEquals(8, capped, "100 NE/mB would let steam make 25x direct: clamped to the 2x cap");
            ConfigProbe.set("fissionNePerRod", "1");
            assertEquals(0, TurbineConfig.nePerSteamMb(), "a 1 NE/t direct rod leaves steam nothing under 2x");
        } finally {
            ConfigProbe.reset("turbineNePerSteamMb");
            ConfigProbe.reset("fissionNePerRod");
        }
        assertEquals(8, TurbineConfig.nePerSteamMb(), "the cap follows a config reload");
    }
}

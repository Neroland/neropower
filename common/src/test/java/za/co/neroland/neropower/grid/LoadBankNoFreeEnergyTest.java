package za.co.neroland.neropower.grid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.environmental.StirlingMath;

/**
 * Rule test for the Load Bank: it makes heat and nothing else, and a Stirling Generator on that heat gets
 * back less than half of what was burned under every config extreme (largest cold-face bonus, coldest
 * planet, Overdrive preset), so a Load Bank and a Stirling can never make free energy.
 */
class LoadBankNoFreeEnergyTest {

    @Test
    void stirlingReturnIsUnderHalfAtEveryExtreme() {
        int[] nePerHeatValues = {1, 100, 1_000_000};
        int[] stirlingValues = {1, 15, 1_000};
        int[] bonusValues = {0, 500, 1_000};
        int[] presets = {500, 1000, 2000};
        for (int lbPerHeat : nePerHeatValues) {
            for (int stirling : stirlingValues) {
                for (int bonus : bonusValues) {
                    int divisor = LoadBankMath.nePerHeat(lbPerHeat, stirling, bonus);
                    for (int preset : presets) {
                        long burned = 1_000_000L;
                        long heat = LoadBankMath.heat(burned, 0L, divisor)[0];
                        long added = (long) LoadBankMath.presetAdjusted(heat, preset) * preset / 1000L;
                        assertTrue(added <= heat, "preset adds no heat per NE");
                        int worstBonus = StirlingMath.coldFaceBonusPermille(bonus, -100_000, true);
                        long back = StirlingMath.outputFor((int) Math.min(Integer.MAX_VALUE, added), stirling, worstBonus);
                        assertTrue(back * 2 < burned, "returned " + back + " of " + burned + " at " + lbPerHeat
                                + "/" + stirling + "/" + bonus + "/" + preset);
                    }
                }
            }
        }
    }

    @Test
    void remainderCarriesSoLowRatesStillHeatAndNothingIsFree() {
        long carry = 0L;
        long heat = 0L;
        for (int i = 0; i < 100; i++) {
            long[] step = LoadBankMath.heat(30L, carry, 100);
            heat += step[0];
            carry = step[1];
        }
        assertEquals(30L, heat);
        assertEquals(0L, carry);
    }

    @Test
    void drawsTheSetRateAndStopsWhenHot() {
        assertEquals(5_000L, LoadBankMath.draw(5_000L, 40_000L, false));
        assertEquals(1_000L, LoadBankMath.draw(5_000L, 1_000L, false));
        assertEquals(0L, LoadBankMath.draw(5_000L, 40_000L, true));
        assertEquals(0L, LoadBankMath.draw(0L, 40_000L, false));
        assertEquals(4, LoadBankMath.heatStep(900, 800));
        assertEquals(0, LoadBankMath.heatStep(0, 800));
    }

    @Test
    void defaultsKeepTheConfiguredDivisor() {
        assertEquals(100, LoadBankMath.nePerHeat(100, 15, 500));
    }
}

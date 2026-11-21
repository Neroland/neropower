package za.co.neroland.neropower.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Rule test for the power cells' Induction Charger adapter ({@code PortableEnergy.chargeFe}, registered
 * through NeroTech's {@code api.WirelessCharging}): an FE offer becomes NE rounded down, bounded by the
 * cell's room and rate, and the FE reported is rounded up and never above the offer, so no ratio lets a
 * cell gain more than the charger paid for. Pure JVM over {@link PortableEnergyMath}.
 */
class PowerCellChargingTest {

    @Test
    void oneToOneRatioChargesAtTheCellRate() {
        long[] r = PortableEnergyMath.chargeFromFe(0, 250_000, 500, 10_000, 1.0D);
        assertEquals(500L, r[0], "capped by the cell's rate");
        assertEquals(500L, r[1]);
    }

    @Test
    void fullCellTakesNothing() {
        long[] r = PortableEnergyMath.chargeFromFe(250_000, 250_000, 500, 10_000, 1.0D);
        assertEquals(0L, r[0]);
        assertEquals(0L, r[1]);
    }

    @Test
    void nearlyFullCellTakesOnlyTheRoom() {
        long[] r = PortableEnergyMath.chargeFromFe(249_900, 250_000, 500, 10_000, 1.0D);
        assertEquals(100L, r[0]);
    }

    @Test
    void neverGainsMoreThanPaidAtAnyRatio() {
        double[] ratios = {0.3D, 1.0D, 2.5D, 4.0D, 7.0D, 10.0D};
        for (double ratio : ratios) {
            for (long offer = 1; offer < 200; offer += 7) {
                long[] r = PortableEnergyMath.chargeFromFe(0, 1_000_000, 8_000, offer, ratio);
                assertTrue(r[1] <= offer, "never reports more FE than offered");
                assertTrue(r[0] * ratio <= r[1] + 1e-6, "paid FE covers the NE gained (ratio " + ratio + ")");
            }
        }
    }

    @Test
    void drawingAndChargingRespectBounds() {
        assertEquals(500L, PortableEnergyMath.givable(10_000, 500, 1_000));
        assertEquals(300L, PortableEnergyMath.givable(300, 500, 1_000));
        assertEquals(0L, PortableEnergyMath.givable(0, 500, 1_000));
        assertEquals(0L, PortableEnergyMath.acceptable(10, 10, 500, 100));
        assertEquals(2L, PortableEnergyMath.feToNeCeil(5, 2.5D) - 0L);
    }
}

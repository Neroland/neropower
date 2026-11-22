package za.co.neroland.neropower.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.beam.BeamConfig;
import za.co.neroland.neropower.beam.SplitMath;
import za.co.neroland.neropower.client.hud.GeigerHud;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.failure.TerrainPolicy;
import za.co.neroland.neropower.fission.FissionConfig;
import za.co.neroland.neropower.fission.FissionMath;
import za.co.neroland.neropower.radiation.RadiationConfig;
import za.co.neroland.neropower.storage.StorageConfig;
import za.co.neroland.neropower.turbine.TurbineConfig;
import za.co.neroland.neropower.turbine.TurbineMath;

/**
 * Every tunable is in config with a bound (Stage 3): walks NeroPower's whole {@code neropower} schema
 * reflectively. Every number has a finite min and max with its default inside; every string has a parser that
 * turns garbage into a safe value; the balance keys all
 * exist.
 */
class ConfigBoundsTest {

    /** Keys the balance decisions name (005 turbine and steam, 006 fuel and waste, 008 storage and grid). */
    static final List<String> DECISION_KEYS = List.of(
            // 005
            "fissionSteamPerHeat", "fissionCryoHeatPerMb", "fissionCryoSafeBandPermille", "reactorSteamPortTankMb",
            "turbineNePerSteamMb", "turbineVaneFlowSteel", "turbineVaneFlowCindersteel",
            "turbineVaneFlowSuperconducting", "turbineVaneEffSteel", "turbineVaneEffCindersteel",
            "turbineVaneEffSuperconducting", "turbineStatorCeilingTier1", "turbineStatorCeilingTier2",
            "turbineStatorCeilingTier3", "turbineSpinUpRpmPerTick", "turbineCoastDownRpmPerTick", "turbineTripRpm",
            "turbineFailureAction", "turbineVentEfficiencyPermille", "turbineSteamPortTankMb", "condenserMbPerTick",
            "condenserAtmosphereFactors", "condenserRadiatorMbPerTick", "condenserSteamPerCryoMb", "condenserTankMb",
            // 006
            "breederTicksPerPellet", "breederMaxBlankets", "breederReactivityCostPermille",
            "fissionModeratorCapPermille", "fissionModeratorPermillePerContact", "fissionEuropiumControlRodPermille",
            "fissionBraidedOutputPermille", "fissionBraidedBurnupPermille", "fissionBraidedHeavyWaterContacts",
            "fissionEconomizerBurnupPermille", "fissionEconomizerOutputPermille", "poolRodHeatPerTick",
            "poolCoolingPerContact", "poolCoolTicks", "vitrifierNePerTick", "vitrifierTicksPerOp",
            "caskHeatIntervalTicks", "caskSlotsPerHeat", "betavoltaicNePerTick", "betavoltaicCapsuleTicks",
            // 008
            "surgeBankCapacity", "surgeBankIo", "bankFastTierIoCap", "bankMaxIoCap", "momentumWheelCapacity",
            "momentumWheelIo", "momentumWheelLossPermillePerSecond", "smesCapacityPerCoil", "smesIoPerCoil",
            "smesMaxIo", "smesCryoTankMb", "smesCryoPerCoilPer100Ticks", "smesWarmingPerTick", "smesCoolingPerTick",
            "smesQuenchMode", "smesQuenchLossPermille", "smesQuenchLockoutTicks", "powerCellCapacityBasic",
            "powerCellCapacityAdvanced", "powerCellCapacityElite", "powerCellIoBasic", "powerCellIoAdvanced",
            "powerCellIoElite", "breakerMinDwellTicks", "loadBankMaxRate", "loadBankNePerHeat",
            "beamSplitterMaxLinks", "beamSplitterLensLoss", "phasedArrayRange", "phasedArrayLossPermillePerBlock",
            "phasedArrayTransferPerPass", "phasedArrayMaxTargets", "fuelCellTicksPerUnit",
            "fuelCellNePerHydrogenUnit");

    /** A garbage-in check per string key: the value the mod reads must be safe whatever the file says. */
    static final Map<String, Consumer<String>> STRING_KEYS = Map.of(
            "terrainDamageMode", raw -> assertEquals("auto", NeroPowerConfig.terrainDamageMode()),
            "radiationMode", raw -> assertFalse(TerrainPolicy.resolve(RadiationConfig.radiationMode(), true),
                    "garbage radiationMode must read as auto (off on a dedicated server)"),
            "geigerHudCorner", raw -> assertEquals(GeigerHud.Corner.BOTTOM_LEFT,
                    GeigerHud.Corner.parse(NeroPowerConfig.geigerHudCorner())),
            "fissionBurnupCurve", raw -> assertEquals(FissionMath.outputFactor(FissionMath.DEFAULT, 300),
                    FissionMath.outputFactor(FissionMath.parseCurve(FissionConfig.burnupCurve()), 300)),
            "smesQuenchMode", raw -> assertFalse(StorageConfig.smesQuenchBlast(), "garbage must read as drain"),
            "turbineFailureAction", raw -> assertEquals(TurbineConfig.FailureMode.TRIP, TurbineConfig.failureAction()),
            "condenserAtmosphereFactors", raw -> assertEquals(TurbineMath.UNKNOWN_ATMOSPHERE_FACTOR,
                    TurbineMath.atmosphereFactor(TurbineConfig.condenserAtmosphereFactors(), "EARTHLIKE")),
            "beamSplitterLensLoss", raw -> {
                int[] loss = BeamConfig.beamSplitterLensLoss();
                assertEquals(50, loss[SplitMath.Lens.QUARTZ.ordinal()]);
                assertEquals(10, loss[SplitMath.Lens.VOID.ordinal()]);
            });

    @Test
    void everyNumberHasABoundAndItsDefaultInside() {
        List<String> bad = new ArrayList<>();
        for (ConfigValue<?> value : ConfigProbe.allValues()) {
            Object def = value.defaultValue();
            String key = value.key();
            if (def instanceof Integer d) {
                @SuppressWarnings("unchecked") ConfigValue<Integer> v = (ConfigValue<Integer>) value;
                int min = ConfigProbe.sanitize(v, Integer.MIN_VALUE);
                int max = ConfigProbe.sanitize(v, Integer.MAX_VALUE);
                if (min == Integer.MIN_VALUE || max == Integer.MAX_VALUE || min >= max || d < min || d > max) {
                    bad.add(key + " [" + min + ", " + max + "] default " + d);
                }
            } else if (def instanceof Long d) {
                @SuppressWarnings("unchecked") ConfigValue<Long> v = (ConfigValue<Long>) value;
                long min = ConfigProbe.sanitize(v, Long.MIN_VALUE);
                long max = ConfigProbe.sanitize(v, Long.MAX_VALUE);
                if (min == Long.MIN_VALUE || max == Long.MAX_VALUE || min >= max || d < min || d > max) {
                    bad.add(key + " [" + min + ", " + max + "] default " + d);
                }
            } else if (def instanceof Double d) {
                @SuppressWarnings("unchecked") ConfigValue<Double> v = (ConfigValue<Double>) value;
                double min = ConfigProbe.sanitize(v, Double.NEGATIVE_INFINITY);
                double max = ConfigProbe.sanitize(v, Double.POSITIVE_INFINITY);
                if (!Double.isFinite(min) || !Double.isFinite(max) || min >= max || d < min || d > max) {
                    bad.add(key + " [" + min + ", " + max + "] default " + d);
                }
            } else if (!(def instanceof Boolean) && !(def instanceof String)) {
                bad.add(key + " has an unchecked type " + def.getClass().getSimpleName());
            }
        }
        assertTrue(bad.isEmpty(), "unbounded or out-of-range keys: " + bad);
    }

    @Test
    void numbersClampAndGarbageFallsBackToTheDefault() {
        for (ConfigValue<?> value : ConfigProbe.allValues()) {
            Object def = value.defaultValue();
            if (!(def instanceof Number)) {
                continue;
            }
            String key = value.key();
            try {
                ConfigProbe.set(key, "not a number");
                assertEquals(def, value.get(), key + ": garbage must read as the default");
                ConfigProbe.set(key, def instanceof Double ? "1e300"
                        : def instanceof Long ? Long.toString(Long.MAX_VALUE) : Integer.toString(Integer.MAX_VALUE));
                Object high = value.get();
                ConfigProbe.set(key, def instanceof Double ? "-1e300"
                        : def instanceof Long ? Long.toString(Long.MIN_VALUE) : Integer.toString(Integer.MIN_VALUE));
                Object low = value.get();
                assertTrue(((Number) low).doubleValue() <= ((Number) def).doubleValue()
                        && ((Number) def).doubleValue() <= ((Number) high).doubleValue(), key + " clamps oddly");
            } finally {
                ConfigProbe.reset(key);
            }
        }
    }

    @Test
    void everyStringKeyHasASafeParser() {
        Set<String> unknown = new TreeSet<>();
        for (ConfigValue<?> value : ConfigProbe.allValues()) {
            if (!(value.defaultValue() instanceof String)) {
                continue;
            }
            String key = value.key();
            Consumer<String> check = STRING_KEYS.get(key);
            if (check == null) {
                unknown.add(key);
                continue;
            }
            try {
                ConfigProbe.set(key, "garbage, =,=12,x=");
                check.accept("garbage, =,=12,x=");
            } finally {
                ConfigProbe.reset(key);
            }
        }
        assertTrue(unknown.isEmpty(), "add a garbage-in check to ConfigBoundsTest.STRING_KEYS for " + unknown);
    }

    @Test
    void tableStringsClampTheirEntries() {
        assertEquals(TurbineMath.MAX_ATMOSPHERE_FACTOR, TurbineMath.atmosphereFactor("VACUUM=99999", "VACUUM"));
        assertEquals(0, TurbineMath.atmosphereFactor("VACUUM=-5", "VACUUM"));
        int[] loss = SplitMath.parseLensLoss("quartz=-4,void=5000");
        assertEquals(0, loss[SplitMath.Lens.QUARTZ.ordinal()]);
        assertEquals(SplitMath.PERMILLE, loss[SplitMath.Lens.VOID.ordinal()]);
    }

    @Test
    void aNotANumberBeamDamageReadsAsTheDefault() {
        try {
            ConfigProbe.set("beamDamage", "NaN");
            assertTrue(Double.isFinite(BeamConfig.beamDamage()), "Core's clamp reads NaN as the default");
            assertEquals(0.5D, BeamConfig.beamDamage());
        } finally {
            ConfigProbe.reset("beamDamage");
        }
    }

    @Test
    void theDecisionsTunablesAreAllInConfig() {
        ConfigProbe.allValues();
        List<String> missing = new ArrayList<>();
        for (String key : DECISION_KEYS) {
            if (NeroPowerConfig.schema().value(key) == null) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "decision tunables missing from config: " + missing);
    }

    @Test
    void theFailureLadderDefaultsAreOrdered() {
        ConfigProbe.allValues();
        assertTrue(NeroPowerConfig.failureWarningPermille() < NeroPowerConfig.failureUnstablePermille());
        assertTrue(NeroPowerConfig.failureUnstablePermille() < NeroPowerConfig.failureFailurePermille());
        assertTrue(FissionConfig.economizerOutputPermille() <= 1_000 && FissionConfig.economizerBurnupPermille() <= 1_000);
    }
}

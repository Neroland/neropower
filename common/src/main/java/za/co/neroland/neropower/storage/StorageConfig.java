package za.co.neroland.neropower.storage;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * Stage 5 storage-tier balance, declared on NeroPower's shared {@code neropower} schema (see
 * {@link NeroPowerConfig#schema()}). Class-loaded by {@code NeroPowerCommon.init()} via {@link #init()}
 * BEFORE {@code NeroPowerConfig.load()}, so these keys are part of the schema Core registers.
 *
 * <p>All keys are server-authoritative: capacities and I/O limits are gameplay balance, and the
 * bank scan radius / I/O cap bound the controller's work. Nothing here is player data.
 */
public final class StorageConfig {

    // --- battery cells: capacity (NE) and external I/O limit (NE/t) per tier --------------------
    private static final ConfigValue<Integer> CELL_CAPACITY_BASIC = NeroPowerConfig.schema().intRange(
            "batteryCellCapacityBasic", 500_000, 1_000, 100_000_000, true,
            "Basic Battery Cell capacity (NE)");
    private static final ConfigValue<Integer> CELL_CAPACITY_ADVANCED = NeroPowerConfig.schema().intRange(
            "batteryCellCapacityAdvanced", 2_000_000, 1_000, 100_000_000, true,
            "Advanced Battery Cell capacity (NE)");
    private static final ConfigValue<Integer> CELL_CAPACITY_ELITE = NeroPowerConfig.schema().intRange(
            "batteryCellCapacityElite", 8_000_000, 1_000, 100_000_000, true,
            "Elite Battery Cell capacity (NE)");
    private static final ConfigValue<Integer> CELL_IO_BASIC = NeroPowerConfig.schema().intRange(
            "batteryCellIoBasic", 1_000, 1, 1_000_000, true,
            "Basic Battery Cell maximum insert / extract per tick (NE/t)");
    private static final ConfigValue<Integer> CELL_IO_ADVANCED = NeroPowerConfig.schema().intRange(
            "batteryCellIoAdvanced", 4_000, 1, 1_000_000, true,
            "Advanced Battery Cell maximum insert / extract per tick (NE/t)");
    private static final ConfigValue<Integer> CELL_IO_ELITE = NeroPowerConfig.schema().intRange(
            "batteryCellIoElite", 16_000, 1, 1_000_000, true,
            "Elite Battery Cell maximum insert / extract per tick (NE/t)");

    // --- bank controller ------------------------------------------------------------------------
    private static final ConfigValue<Integer> BANK_SCAN_RADIUS = NeroPowerConfig.schema().intRange(
            "bankScanRadius", 2, 1, 4, true,
            "how far (blocks, each axis) a Battery Bank Controller looks for connected cells: 1 = a 3x3x3 "
            + "region around it, 2 = 5x5x5, ... 4 = 9x9x9; only cells reachable face-to-face from a cell "
            + "touching the controller join the bank");
    private static final ConfigValue<Integer> BANK_MAX_IO_CAP = NeroPowerConfig.schema().intRange(
            "bankMaxIoCap", 64_000, 1, 10_000_000, true,
            "hard cap on a bank's pooled insert / extract per tick (NE/t); the pool's I/O is otherwise "
            + "min(member cell I/O) x member count");
    private static final ConfigValue<Integer> BANK_PRIORITY_THRESHOLD = NeroPowerConfig.schema().intRange(
            "bankPriorityThresholdPermille", 300, 0, 1_000, true,
            "PRIORITY_SOURCE mode: a bank only pushes into a neighbour whose own buffer is below this fill "
            + "(permille of its capacity); BUFFER mode ignores it");
    private static final ConfigValue<Long> BANK_CAPACITY_MILESTONE = NeroPowerConfig.schema().longRange(
            "bankCapacityMilestone", 32_000_000L, 1_000L, 1_000_000_000_000L, true,
            "pooled capacity (NE) at which a Battery Bank opens its owner's neropower:bank_capacity milestone "
            + "record gate (a quest signal; it gates nothing)");

    // --- 0.4.0: Surge Bank (fast tier of the bank pool; decision 008, ids per decision 011) ----------
    private static final ConfigValue<Integer> SURGE_BANK_CAPACITY = NeroPowerConfig.schema().intRange(
            "surgeBankCapacity", 200_000, 1_000, 100_000_000, true,
            "Surge Bank capacity (NE): small, but it moves energy fast");
    private static final ConfigValue<Integer> SURGE_BANK_IO = NeroPowerConfig.schema().intRange(
            "surgeBankIo", 50_000, 1, 10_000_000, true,
            "Surge Bank maximum insert / extract per tick (NE/t)");
    private static final ConfigValue<Integer> BANK_FAST_TIER_IO_CAP = NeroPowerConfig.schema().intRange(
            "bankFastTierIoCap", 256_000, 1, 100_000_000, true,
            "hard cap on the summed I/O of the Surge Banks in one bank (NE/t); added on top of the cells' "
            + "pooled I/O, which bankMaxIoCap still caps");

    // --- 0.4.0: Momentum Wheel ---------------------------------------------------------------------
    private static final ConfigValue<Integer> MOMENTUM_WHEEL_CAPACITY = NeroPowerConfig.schema().intRange(
            "momentumWheelCapacity", 2_000_000, 1_000, 1_000_000_000, true,
            "Momentum Wheel capacity (NE) at full speed");
    private static final ConfigValue<Integer> MOMENTUM_WHEEL_IO = NeroPowerConfig.schema().intRange(
            "momentumWheelIo", 2_000, 1, 1_000_000, true,
            "Momentum Wheel maximum insert / extract per tick (NE/t)");
    private static final ConfigValue<Integer> MOMENTUM_WHEEL_LOSS = NeroPowerConfig.schema().intRange(
            "momentumWheelLossPermillePerSecond", 2, 0, 100, true,
            "share of the stored energy (permille) a Momentum Wheel loses to bearing friction every second; "
            + "never part of a bank pool, so the loss is always visible");

    // --- 0.4.0: Storage Ring (SMES) -----------------------------------------------------------------
    private static final ConfigValue<Long> SMES_CAPACITY_PER_COIL = NeroPowerConfig.schema().longRange(
            "smesCapacityPerCoil", 50_000_000L, 1_000_000L, 1_000_000_000L, true,
            "Storage Ring capacity per coil in the ring (NE)");
    private static final ConfigValue<Integer> SMES_IO_PER_COIL = NeroPowerConfig.schema().intRange(
            "smesIoPerCoil", 2_000, 1, 1_000_000, true,
            "Storage Ring insert / extract per coil per tick (NE/t), capped by smesMaxIo");
    private static final ConfigValue<Integer> SMES_MAX_IO = NeroPowerConfig.schema().intRange(
            "smesMaxIo", 200_000, 1, 100_000_000, true,
            "hard cap on a Storage Ring's insert / extract per tick (NE/t)");
    private static final ConfigValue<Integer> SMES_CRYO_TANK_MB = NeroPowerConfig.schema().intRange(
            "smesCryoTankMb", 8_000, 1_000, 256_000, true,
            "Storage Ring Controller cryo coolant tank size (mB of #nerotech:cryo_coolant)");
    private static final ConfigValue<Integer> SMES_CRYO_PER_COIL = NeroPowerConfig.schema().intRange(
            "smesCryoPerCoilPer100Ticks", 1, 0, 100, true,
            "cryo coolant a formed Storage Ring uses per coil every 100 ticks (mB); 0 = never starves");
    private static final ConfigValue<Integer> SMES_WARMING = NeroPowerConfig.schema().intRange(
            "smesWarmingPerTick", 2, 0, 1_000, true,
            "how fast a starved Storage Ring warms (permille of quench warmth per tick)");
    private static final ConfigValue<Integer> SMES_COOLING = NeroPowerConfig.schema().intRange(
            "smesCoolingPerTick", 5, 0, 1_000, true,
            "how fast a cooled Storage Ring sheds warmth (permille per tick)");
    private static final ConfigValue<String> SMES_QUENCH_MODE = NeroPowerConfig.schema().string(
            "smesQuenchMode", "drain", true,
            "what a Storage Ring quench does: drain (default) = lose smesQuenchLossPermille of the charge and go "
            + "offline for smesQuenchLockoutTicks, no blast, no terrain change; blast = an explosion through the "
            + "shared failure rules (failureRadiusCap, terrainDamageMode, spawn protection)");
    private static final ConfigValue<Integer> SMES_QUENCH_LOSS = NeroPowerConfig.schema().intRange(
            "smesQuenchLossPermille", 500, 0, 1_000, true,
            "share of the stored energy (permille) a drain-mode quench loses");
    private static final ConfigValue<Integer> SMES_QUENCH_LOCKOUT = NeroPowerConfig.schema().intRange(
            "smesQuenchLockoutTicks", 1_200, 0, 72_000, true,
            "ticks a quenched Storage Ring stays offline; it re-arms only once this has passed and its warmth "
            + "is back below the warning threshold minus the hysteresis");

    // --- 0.4.0: portable power cells ------------------------------------------------------------------
    private static final ConfigValue<Integer> POWER_CELL_CAPACITY_BASIC = NeroPowerConfig.schema().intRange(
            "powerCellCapacityBasic", 250_000, 1_000, 1_000_000_000, true, "Basic Power Cell capacity (NE)");
    private static final ConfigValue<Integer> POWER_CELL_CAPACITY_ADVANCED = NeroPowerConfig.schema().intRange(
            "powerCellCapacityAdvanced", 1_000_000, 1_000, 1_000_000_000, true, "Advanced Power Cell capacity (NE)");
    private static final ConfigValue<Integer> POWER_CELL_CAPACITY_ELITE = NeroPowerConfig.schema().intRange(
            "powerCellCapacityElite", 4_000_000, 1_000, 1_000_000_000, true, "Elite Power Cell capacity (NE)");
    private static final ConfigValue<Integer> POWER_CELL_IO_BASIC = NeroPowerConfig.schema().intRange(
            "powerCellIoBasic", 500, 1, 1_000_000, true, "Basic Power Cell charge / draw rate (NE/t)");
    private static final ConfigValue<Integer> POWER_CELL_IO_ADVANCED = NeroPowerConfig.schema().intRange(
            "powerCellIoAdvanced", 2_000, 1, 1_000_000, true, "Advanced Power Cell charge / draw rate (NE/t)");
    private static final ConfigValue<Integer> POWER_CELL_IO_ELITE = NeroPowerConfig.schema().intRange(
            "powerCellIoElite", 8_000, 1, 1_000_000, true, "Elite Power Cell charge / draw rate (NE/t)");

    private StorageConfig() {
    }

    public static int surgeBankCapacity() {
        return SURGE_BANK_CAPACITY.get();
    }

    public static int surgeBankIo() {
        return SURGE_BANK_IO.get();
    }

    /** Cap on the summed Surge Bank I/O in one bank (NE/t). */
    public static int bankFastTierIoCap() {
        return BANK_FAST_TIER_IO_CAP.get();
    }

    public static int momentumWheelCapacity() {
        return MOMENTUM_WHEEL_CAPACITY.get();
    }

    public static int momentumWheelIo() {
        return MOMENTUM_WHEEL_IO.get();
    }

    public static int momentumWheelLossPermillePerSecond() {
        return MOMENTUM_WHEEL_LOSS.get();
    }

    public static long smesCapacityPerCoil() {
        return SMES_CAPACITY_PER_COIL.get();
    }

    public static int smesIoPerCoil() {
        return SMES_IO_PER_COIL.get();
    }

    public static int smesMaxIo() {
        return SMES_MAX_IO.get();
    }

    public static int smesCryoTankMb() {
        return SMES_CRYO_TANK_MB.get();
    }

    public static int smesCryoPerCoilPer100Ticks() {
        return SMES_CRYO_PER_COIL.get();
    }

    public static int smesWarmingPerTick() {
        return SMES_WARMING.get();
    }

    public static int smesCoolingPerTick() {
        return SMES_COOLING.get();
    }

    /** Whether a quench is the blast variant ({@code smesQuenchMode=blast}); anything else is drain. */
    public static boolean smesQuenchBlast() {
        return "blast".equalsIgnoreCase(SMES_QUENCH_MODE.get().trim());
    }

    public static int smesQuenchLossPermille() {
        return SMES_QUENCH_LOSS.get();
    }

    public static int smesQuenchLockoutTicks() {
        return SMES_QUENCH_LOCKOUT.get();
    }

    public static int powerCellCapacityBasic() {
        return POWER_CELL_CAPACITY_BASIC.get();
    }

    public static int powerCellCapacityAdvanced() {
        return POWER_CELL_CAPACITY_ADVANCED.get();
    }

    public static int powerCellCapacityElite() {
        return POWER_CELL_CAPACITY_ELITE.get();
    }

    public static int powerCellIoBasic() {
        return POWER_CELL_IO_BASIC.get();
    }

    public static int powerCellIoAdvanced() {
        return POWER_CELL_IO_ADVANCED.get();
    }

    public static int powerCellIoElite() {
        return POWER_CELL_IO_ELITE.get();
    }

    /** Class-load trigger: called from {@code NeroPowerCommon.init()} before {@code NeroPowerConfig.load()}. */
    public static void init() {
    }

    public static int batteryCellCapacityBasic() {
        return CELL_CAPACITY_BASIC.get();
    }

    public static int batteryCellCapacityAdvanced() {
        return CELL_CAPACITY_ADVANCED.get();
    }

    public static int batteryCellCapacityElite() {
        return CELL_CAPACITY_ELITE.get();
    }

    public static int batteryCellIoBasic() {
        return CELL_IO_BASIC.get();
    }

    public static int batteryCellIoAdvanced() {
        return CELL_IO_ADVANCED.get();
    }

    public static int batteryCellIoElite() {
        return CELL_IO_ELITE.get();
    }

    /** Bank scan radius in blocks per axis (1..4). */
    public static int bankScanRadius() {
        return BANK_SCAN_RADIUS.get();
    }

    /** Hard cap on a bank's pooled I/O per tick (NE/t). */
    public static int bankMaxIoCap() {
        return BANK_MAX_IO_CAP.get();
    }

    /** PRIORITY_SOURCE fill threshold (permille of the neighbour's capacity). */
    public static int bankPriorityThresholdPermille() {
        return BANK_PRIORITY_THRESHOLD.get();
    }

    /** Pooled capacity (NE) that opens the owner's {@code neropower:bank_capacity} milestone record. */
    public static long bankCapacityMilestone() {
        return BANK_CAPACITY_MILESTONE.get();
    }
}

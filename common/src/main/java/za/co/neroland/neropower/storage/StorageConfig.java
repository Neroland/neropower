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

    private StorageConfig() {
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
}

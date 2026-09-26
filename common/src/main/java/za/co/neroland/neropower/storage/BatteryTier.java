package za.co.neroland.neropower.storage;

/**
 * The three Battery Cell tiers. Capacity and external I/O come from {@link StorageConfig} per tier
 * (read at construction / on use — never cached across a config reload by the enum itself).
 */
public enum BatteryTier {

    BASIC("basic"),
    ADVANCED("advanced"),
    ELITE("elite");

    private final String key;

    BatteryTier(String key) {
        this.key = key;
    }

    /** The tier's id fragment: {@code battery_cell_<key>}. */
    public String key() {
        return this.key;
    }

    /** Buffer capacity (NE) from config. */
    public int capacity() {
        return switch (this) {
            case BASIC -> StorageConfig.batteryCellCapacityBasic();
            case ADVANCED -> StorageConfig.batteryCellCapacityAdvanced();
            case ELITE -> StorageConfig.batteryCellCapacityElite();
        };
    }

    /** External insert / extract limit (NE/t) from config. */
    public int maxIo() {
        return switch (this) {
            case BASIC -> StorageConfig.batteryCellIoBasic();
            case ADVANCED -> StorageConfig.batteryCellIoAdvanced();
            case ELITE -> StorageConfig.batteryCellIoElite();
        };
    }
}

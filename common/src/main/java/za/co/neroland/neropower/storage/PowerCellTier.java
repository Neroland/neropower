package za.co.neroland.neropower.storage;

/**
 * The three portable Power Cell tiers. Capacity and rate come from {@link StorageConfig}
 * per tier, read on use, so a config reload applies at once.
 */
public enum PowerCellTier {

    BASIC("basic"),
    ADVANCED("advanced"),
    ELITE("elite");

    private final String key;

    PowerCellTier(String key) {
        this.key = key;
    }

    /** The tier's id fragment: {@code power_cell_<key>}. */
    public String key() {
        return this.key;
    }

    /** Capacity (NE). */
    public long capacity() {
        return switch (this) {
            case BASIC -> StorageConfig.powerCellCapacityBasic();
            case ADVANCED -> StorageConfig.powerCellCapacityAdvanced();
            case ELITE -> StorageConfig.powerCellCapacityElite();
        };
    }

    /** Charge / draw rate (NE/t). */
    public long io() {
        return switch (this) {
            case BASIC -> StorageConfig.powerCellIoBasic();
            case ADVANCED -> StorageConfig.powerCellIoAdvanced();
            case ELITE -> StorageConfig.powerCellIoElite();
        };
    }
}

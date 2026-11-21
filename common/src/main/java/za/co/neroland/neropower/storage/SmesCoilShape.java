package za.co.neroland.neropower.storage;

import net.minecraft.util.StringRepresentable;

/**
 * The Storage Ring Coil's {@code shape} block-state values (contract row 21): a straight run along x or
 * z, or one of the four corners, named by the two neighbours it joins. Chosen from the neighbouring ring
 * members on placement and on every horizontal neighbour change ({@link #of}).
 */
public enum SmesCoilShape implements StringRepresentable {

    STRAIGHT_X("straight_x"),
    STRAIGHT_Z("straight_z"),
    CORNER_NE("corner_ne"),
    CORNER_NW("corner_nw"),
    CORNER_SE("corner_se"),
    CORNER_SW("corner_sw");

    private final String name;

    SmesCoilShape(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * The shape for a coil whose north / south / east / west neighbours are (or are not) ring members:
     * a straight run wins over a corner (both ends of an axis), then a corner, then any single neighbour's
     * axis, and a lone coil reads {@link #STRAIGHT_X}. Pure, so it is unit-testable.
     */
    public static SmesCoilShape of(boolean north, boolean south, boolean east, boolean west) {
        if (east && west) {
            return STRAIGHT_X;
        }
        if (north && south) {
            return STRAIGHT_Z;
        }
        if (north && east) {
            return CORNER_NE;
        }
        if (north && west) {
            return CORNER_NW;
        }
        if (south && east) {
            return CORNER_SE;
        }
        if (south && west) {
            return CORNER_SW;
        }
        if (north || south) {
            return STRAIGHT_Z;
        }
        return STRAIGHT_X;
    }
}

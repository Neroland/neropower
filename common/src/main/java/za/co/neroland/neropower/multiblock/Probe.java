package za.co.neroland.neropower.multiblock;

/** Reads cells for a shape. Implementations never load a chunk. */
@FunctionalInterface
public interface Probe {

    /** The cell at a position (or {@link Cell#UNLOADED}). */
    Cell at(int x, int y, int z);

    /** Whether every position in {@code bounds} is loaded. Default: true (pure tests). */
    default boolean loaded(Bounds bounds) {
        return true;
    }
}

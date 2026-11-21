package za.co.neroland.neropower.multiblock;

/** A multiblock controller block entity: told when a member changes, it revalidates on its next tick. */
public interface MultiblockHost {

    /** Schedule a revalidation (sets a flag only; safe from any block callback). */
    void markStructureDirty();
}

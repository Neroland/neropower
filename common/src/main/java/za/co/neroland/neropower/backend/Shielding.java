package za.co.neroland.neropower.backend;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import za.co.neroland.neropower.registry.ModTags;

/** Counts the faces of a waste block left open by {@code #neropower:radiation_barrier} blocks. */
public final class Shielding {

    private Shielding() {
    }

    /** Faces of {@code pos} whose neighbour is not a radiation barrier (0..6). */
    public static int unshieldedFaces(Level level, BlockPos pos) {
        int open = 0;
        for (Direction side : Direction.values()) {
            if (!level.getBlockState(pos.relative(side)).is(ModTags.RADIATION_BARRIER)) {
                open++;
            }
        }
        return open;
    }
}

package za.co.neroland.neropower.machine;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

/**
 * Gallery exhibits: machines placed by {@code /neropower gallery}. The flag is NeroTech's machine-base
 * {@code GalleryExhibit} (saved with the block entity, machine state only, no player data); marking a
 * machine drops its owner and its operator, so it credits no gate, trophy or quest and sends no owner alert.
 * NeroPower adds, through this class: no radiation from an exhibit, no NeroLink event, and no destructive
 * failure action (the ladder still telegraphs; the action is skipped).
 *
 * <p>Only the gallery command calls {@link #mark}. Every check is a cheap instance test.
 */
public final class Exhibits {

    private Exhibits() {
    }

    /** Whether {@code be} is a gallery exhibit. */
    public static boolean is(@Nullable Object be) {
        return be instanceof NeroTechMachineBlockEntity machine && machine.isGalleryExhibit();
    }

    /** Whether the block entity at {@code pos} is a gallery exhibit; {@code false} when the chunk is not loaded. */
    public static boolean at(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return is(be);
    }

    /** Mark {@code be} as an exhibit (gallery command only). Returns whether it is a machine that can carry the flag. */
    public static boolean mark(@Nullable BlockEntity be) {
        if (be instanceof NeroTechMachineBlockEntity machine) {
            machine.markGalleryExhibit();
            machine.setChanged();
            return true;
        }
        return false;
    }
}

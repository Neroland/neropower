package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

/**
 * Fission Reactor multiblock validation, modelled on NeroTech's {@code FusionStructure}: a hollow
 * cubic shell of {@code fission_casing} in one of two sizes — {@value #MIN_SIZE}³ or
 * {@value #MAX_SIZE}³ — with the controller ({@code fission_core}) sitting at the <b>centre of one
 * vertical wall face, facing outward</b>. The interior holds air or {@code control_rod_assembly}
 * blocks (interior only — an assembly in the wall is not casing); the core counts them.
 *
 * <p>Validation is a bounded scan (at most 5³ = 125 positions) run by the controller every
 * {@value #RECHECK_TICKS} ticks and again whenever a neighbour changes. Chunks are never loaded by
 * validation: an unloaded corner simply reports "unformed" until the area is back.
 */
public final class FissionStructure {

    /** Smallest and largest shell edge. */
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 5;

    /** Structure re-validation cadence (ticks), formed or not. */
    public static final int RECHECK_TICKS = 40;

    /** Candidate shell sizes, largest first so a 5³ shell is never mis-detected as its 3³ core. */
    private static final int[] SIZES = {MAX_SIZE, MIN_SIZE};

    private FissionStructure() {
    }

    /**
     * A validation result. {@code valid == false} carries {@code shellSize == 0} and no bounds
     * beyond the controller's own position.
     *
     * @param valid     whether a shell is formed
     * @param shellSize the shell edge (3 or 5), 0 when unformed
     * @param rodCount  Control Rod Assemblies found inside the shell
     * @param min       the shell's minimum corner (inclusive); the controller when unformed
     * @param max       the shell's maximum corner (inclusive); the controller when unformed
     * @param center    the interior centre (the failure epicentre); the controller when unformed
     */
    public record Result(boolean valid, int shellSize, int rodCount, BlockPos min, BlockPos max, BlockPos center) {

        /** The "no shell" result for a controller at {@code core}. */
        public static Result unformed(BlockPos core) {
            BlockPos fixed = core.immutable();
            return new Result(false, 0, 0, fixed, fixed, fixed);
        }

        /** Interior blocks of this shell (0 when unformed). */
        public int interiorVolume() {
            return FissionMath.interiorVolume(this.shellSize);
        }

        /** Rod slots this shell can use ({@link FissionMath#usableRodSlots}). */
        public int usableRodSlots() {
            return FissionMath.usableRodSlots(this.shellSize);
        }
    }

    /**
     * Validate the largest formed shell for a controller at {@code corePos} facing {@code facing}
     * (the shell extends behind the controller, away from its front face).
     */
    public static Result check(Level level, BlockPos corePos, Direction facing) {
        for (int size : SIZES) {
            Result result = check(level, corePos, facing, size);
            if (result != null) {
                return result;
            }
        }
        return Result.unformed(corePos);
    }

    @Nullable
    private static Result check(Level level, BlockPos controller, Direction facing, int size) {
        int half = (size - 1) / 2;
        BlockPos center = controller.relative(facing.getOpposite(), half);
        BlockPos min = center.offset(-half, -half, -half);
        BlockPos max = center.offset(half, half, half);
        if (!level.hasChunksAt(min, max)) {
            return null; // never force-load chunks for validation
        }
        int rods = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean surface = pos.getX() == min.getX() || pos.getX() == max.getX()
                    || pos.getY() == min.getY() || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            if (pos.equals(controller)) {
                continue; // the controller occupies its wall-centre slot
            }
            BlockState state = level.getBlockState(pos);
            if (surface) {
                if (!state.is(FissionContent.FISSION_CASING.get())) {
                    return null;
                }
            } else if (state.is(FissionContent.CONTROL_ROD_ASSEMBLY.get())) {
                rods++;
            } else if (!state.isAir()) {
                return null;
            }
        }
        return new Result(true, size, rods, min.immutable(), max.immutable(), center.immutable());
    }
}

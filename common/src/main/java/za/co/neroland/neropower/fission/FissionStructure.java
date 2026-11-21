package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neropower.multiblock.Bounds;
import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.LevelProbe;
import za.co.neroland.neropower.multiblock.ReactorShape;
import za.co.neroland.neropower.multiblock.ShapeResult;

/**
 * Fission Reactor multiblock validation, now a thin adapter over the shared
 * {@link za.co.neroland.neropower.multiblock.ReactorShape} (the shared multiblock base): a hollow
 * cubic shell of {@code fission_casing} with the controller ({@code fission_core}) at the <b>centre of
 * one wall, facing outward</b>; the interior holds air or reactor interior blocks. Sizes 3 and 5 form as
 * in 0.3.x; the 7 size needs a Reactor Control Computer in the wall.
 *
 * <p>Validation is a bounded scan that never loads a chunk, run on change: when any member block is
 * placed or removed ({@link za.co.neroland.neropower.multiblock.MultiblockIndex}), on the core's own
 * neighbour change, on load and when its menu opens. There is no periodic recheck any more.
 */
public final class FissionStructure {

    /** Smallest and largest shell edge that can form with this release's blocks. */
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 7;

    /**
     * The 0.3.x re-validation cadence. Unused since validation runs on change; kept so nothing that
     * referenced it breaks.
     */
    @Deprecated
    public static final int RECHECK_TICKS = 40;

    private FissionStructure() {
    }

    /**
     * A validation result. {@code valid == false} carries {@code shellSize == 0} and no bounds
     * beyond the controller's own position.
     *
     * @param valid     whether a shell is formed
     * @param shellSize the shell edge (3, 5 or 7), 0 when unformed
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
        ShapeResult shape = ReactorShape.evaluate(new LevelProbe(level, FissionStructure::cellOf),
                corePos.getX(), corePos.getY(), corePos.getZ(),
                facing.getStepX(), facing.getStepY(), facing.getStepZ());
        return fromShape(shape, corePos);
    }

    /** The 0.3.x result view of a shared shape result. */
    public static Result fromShape(ShapeResult shape, BlockPos corePos) {
        if (!shape.valid()) {
            return Result.unformed(corePos);
        }
        Bounds b = shape.bounds();
        return new Result(true, shape.size(), ReactorShape.rods(shape),
                new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.maxY(), b.maxZ()),
                new BlockPos(shape.centreX(), shape.centreY(), shape.centreZ()));
    }

    /** How the reactor shape sees a block state. */
    public static Cell cellOf(BlockState state) {
        if (state.is(FissionContent.FISSION_CASING.get())) {
            return Cell.FISSION_CASING;
        }
        if (state.is(FissionContent.CONTROL_ROD_ASSEMBLY.get())) {
            return Cell.CONTROL_ROD;
        }
        if (state.is(FissionContent.FISSION_CORE.get())) {
            return Cell.FISSION_CORE;
        }
        if (state.isAir()) {
            return Cell.AIR;
        }
        return cellOf040(state);
    }

    /** The 0.4.0 interior and wall blocks. */
    private static Cell cellOf040(BlockState state) {
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.GRAPHITE_MODERATOR.get())) {
            return Cell.GRAPHITE_MODERATOR;
        }
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.MODERATOR_TANK.get())) {
            return Cell.MODERATOR_TANK;
        }
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.BREEDER_BLANKET.get())) {
            return Cell.BREEDER_BLANKET;
        }
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.EUROPIUM_CONTROL_ROD_ASSEMBLY.get())) {
            return Cell.EUROPIUM_CONTROL_ROD;
        }
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.REACTOR_STEAM_PORT.get())) {
            return Cell.REACTOR_STEAM_PORT;
        }
        if (state.is(za.co.neroland.neropower.registry.content.FissionContent.REACTOR_CONTROL_COMPUTER.get())) {
            return Cell.REACTOR_CONTROL_COMPUTER;
        }
        return Cell.OTHER;
    }
}

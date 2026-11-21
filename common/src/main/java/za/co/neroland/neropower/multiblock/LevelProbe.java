package za.co.neroland.neropower.multiblock;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A {@link Probe} over a live level: reads block states only where loaded, never loads a chunk. */
public final class LevelProbe implements Probe {

    private final Level level;
    private final Function<BlockState, Cell> mapper;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public LevelProbe(Level level, Function<BlockState, Cell> mapper) {
        this.level = level;
        this.mapper = mapper;
    }

    @Override
    public Cell at(int x, int y, int z) {
        this.cursor.set(x, y, z);
        if (!this.level.isLoaded(this.cursor)) {
            return Cell.UNLOADED;
        }
        return this.mapper.apply(this.level.getBlockState(this.cursor));
    }

    @Override
    public boolean loaded(Bounds bounds) {
        return this.level.hasChunksAt(new BlockPos(bounds.minX(), bounds.minY(), bounds.minZ()),
                new BlockPos(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
    }
}

package za.co.neroland.neropower.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * The in-world half of bank formation: runs {@link BankFloodFill} over the level around a
 * controller and resolves the packed member positions to live {@link BatteryCellBlockEntity}s.
 * Unloaded chunk columns read as "not a cell" ({@code hasChunkAt} guard — a controller on a chunk
 * border must never force loads), so a bank straddling an unloaded border simply shrinks until it
 * comes back.
 */
public final class BankNetwork {

    private BankNetwork() {
    }

    /**
     * Every cell connected to the controller at {@code pos} within {@code radius}, in discovery
     * order.
     */
    public static List<BatteryCellBlockEntity> scan(Level level, BlockPos pos, int radius) {
        List<Long> packed = BankFloodFill.collect(
                BankFloodFill.pack(pos.getX(), pos.getY(), pos.getZ()), radius,
                candidate -> cellAt(level, candidate) != null);
        List<BatteryCellBlockEntity> cells = new ArrayList<>(packed.size());
        for (long member : packed) {
            BatteryCellBlockEntity cell = cellAt(level, member);
            if (cell != null) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static BatteryCellBlockEntity cellAt(Level level, long packed) {
        int x = BankFloodFill.unpackX(packed);
        int y = BankFloodFill.unpackY(packed);
        int z = BankFloodFill.unpackZ(packed);
        if (!level.hasChunkAt(x, z)) {
            return null;
        }
        return level.getBlockEntity(new BlockPos(x, y, z)) instanceof BatteryCellBlockEntity cell
                && !cell.isRemoved() ? cell : null;
    }
}

package za.co.neroland.neropower.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

/**
 * Where a transmitter or relay beams to: a block position plus the dimension id it was linked in,
 * so a stored target can never silently match a different block at the same coordinates in another
 * world. World/block data only — no player identity (POPIA/GDPR; the linking player's UUID lives
 * beside this in the block entity's own {@code LinkOwner} field, documented for Stage 8 erasure).
 */
public record BeamTarget(BlockPos pos, String dimension) {

    /** The target at {@code pos} in {@code level}'s dimension. */
    public static BeamTarget of(BlockPos pos, Level level) {
        return new BeamTarget(pos.immutable(), dimensionId(level));
    }

    /** The target at {@code pos} in the dimension with id {@code dimension}. */
    public static BeamTarget of(BlockPos pos, String dimension) {
        return new BeamTarget(pos.immutable(), dimension);
    }

    /** The string dimension id NeroTech's wireless node also persists ({@code minecraft:overworld}). */
    public static String dimensionId(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Whether this target lies in {@code level}'s dimension. */
    public boolean sameDimension(Level level) {
        return this.dimension.equals(dimensionId(level));
    }

    // --- persistence (mirrors WirelessNodeBlockEntity's PartnerX/Y/Z/Dim layout) -----------------

    /** Write {@code target} (or its absence) under {@code Target*} keys. */
    public static void save(ValueOutput output, @Nullable BeamTarget target) {
        output.putBoolean("Linked", target != null);
        if (target != null) {
            output.putInt("TargetX", target.pos.getX());
            output.putInt("TargetY", target.pos.getY());
            output.putInt("TargetZ", target.pos.getZ());
            output.putString("TargetDim", target.dimension);
        }
    }

    /** Read back what {@link #save} wrote; {@code null} when unlinked. */
    @Nullable
    public static BeamTarget load(ValueInput input) {
        if (!input.getBooleanOr("Linked", false)) {
            return null;
        }
        return new BeamTarget(new BlockPos(input.getIntOr("TargetX", 0), input.getIntOr("TargetY", 0),
                input.getIntOr("TargetZ", 0)), input.getStringOr("TargetDim", ""));
    }
}

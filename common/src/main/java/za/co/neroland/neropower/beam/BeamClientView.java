package za.co.neroland.neropower.beam;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

/**
 * What a client may know of a beam's target. The Beam Transmitter / Relay
 * update tag writes this instead of the saved target; the server NBT is unchanged. Pure, so it is tested
 * without starting Minecraft.
 */
public final class BeamClientView {

    private BeamClientView() {
    }

    /** Within this many blocks (Euclidean) a same-dimension target reaches the client unchanged. */
    public static final int CLIENT_EXACT_REACH = 64;

    /**
     * What the client may know of {@code target}, seen from {@code from} in dimension {@code ownDim}: the
     * target itself when it is in {@code ownDim} and within {@link #CLIENT_EXACT_REACH} blocks; a point
     * {@code max(farLength, 64)} blocks along the same direction when it is farther; this block with an
     * empty dimension when it is in another dimension. {@code null} when unlinked. Pure.
     */
    @Nullable
    public static BeamTarget clientTarget(BlockPos from, @Nullable BeamTarget target, String ownDim, int farLength) {
        if (target == null) {
            return null;
        }
        if (ownDim.isEmpty() || !ownDim.equals(target.dimension())) {
            return BeamTarget.of(from, "");
        }
        long dx = (long) target.pos().getX() - from.getX();
        long dy = (long) target.pos().getY() - from.getY();
        long dz = (long) target.pos().getZ() - from.getZ();
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len <= CLIENT_EXACT_REACH) {
            return target;
        }
        double scale = Math.max(farLength, CLIENT_EXACT_REACH) / len;
        return BeamTarget.of(from.offset((int) Math.round(dx * scale), (int) Math.round(dy * scale),
                (int) Math.round(dz * scale)), ownDim);
    }
}

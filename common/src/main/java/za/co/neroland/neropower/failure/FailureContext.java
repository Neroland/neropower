package za.co.neroland.neropower.failure;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/**
 * What a {@link FailureAction} knows about the machine that failed: the inclusive bounds of its
 * multiblock (a single-block machine passes its own position twice), the owner if one was recorded,
 * and the stable machine id used on NeroTech's failure channel.
 *
 * <p>Privacy (POPIA/GDPR): {@code ownerId} is personal data — an action may use it to alert that
 * player (through Core's link alerts) and for nothing else: never logged, never sent to a client,
 * never encoded into an event scope.
 *
 * @param min       the multiblock's minimum corner (inclusive)
 * @param max       the multiblock's maximum corner (inclusive)
 * @param ownerId   the placing player's UUID, when recorded
 * @param machineId the block-entity type id, e.g. {@code "neropower:fission_core"}
 */
public record FailureContext(BlockPos min, BlockPos max, Optional<UUID> ownerId, String machineId) {

    /** A single-block machine at {@code pos}. */
    public static FailureContext single(BlockPos pos, Optional<UUID> ownerId, String machineId) {
        return new FailureContext(pos, pos, ownerId, machineId);
    }

    /** The bounds' centre block (rounded down on each axis). */
    public BlockPos center() {
        return new BlockPos(
                Math.floorDiv(this.min.getX() + this.max.getX(), 2),
                Math.floorDiv(this.min.getY() + this.max.getY(), 2),
                Math.floorDiv(this.min.getZ() + this.max.getZ(), 2));
    }

    /**
     * The blast radius that stays inside the multiblock: half the largest edge, rounded up, never
     * below 1 — a 3x3x3 reads 1, a 5x5x5 reads 2.
     */
    public int boundsRadius() {
        int extent = Math.max(this.max.getX() - this.min.getX(),
                Math.max(this.max.getY() - this.min.getY(), this.max.getZ() - this.min.getZ()));
        return Math.max(1, (extent + 1) / 2);
    }
}

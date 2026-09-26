package za.co.neroland.neropower.failure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What happens when a machine reaches {@link FailureStage#FAILURE}. Runs once, on the server, from
 * the owning block entity's tick; the action is expected to remove the controller block (the block
 * entity does not survive the call). Implementations: {@link RemoveOnly} (soft fail) and
 * {@link Explode} (blast, honouring the terrain-damage and radius-cap config and the
 * {@link za.co.neroland.neropower.protection.ProtectionCheck}).
 */
@FunctionalInterface
public interface FailureAction {

    /**
     * @param level the machine's level
     * @param pos   the controller block's position
     * @param ctx   multiblock bounds, owner and machine id
     */
    void apply(ServerLevel level, BlockPos pos, FailureContext ctx);
}

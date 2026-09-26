package za.co.neroland.neropower.failure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The soft failure: the controller block simply vanishes — no blast, no drops (the machine is lost,
 * its contents with it), nothing else in the world touched. The default {@link FailureAction} of
 * every NeroPower machine until it says otherwise.
 */
public final class RemoveOnly implements FailureAction {

    public static final RemoveOnly INSTANCE = new RemoveOnly();

    private RemoveOnly() {
    }

    @Override
    public void apply(ServerLevel level, BlockPos pos, FailureContext ctx) {
        level.removeBlock(pos, false);
    }
}

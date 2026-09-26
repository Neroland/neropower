package za.co.neroland.neropower.fission;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import za.co.neroland.neropower.failure.FailureAction;
import za.co.neroland.neropower.failure.FailureContext;

/**
 * The fission core's failure: NeroPower's {@code Explode} blast (radius shell + 2, capped and
 * terrain-damage-aware) followed, when {@code fissionScorchEnabled}, by a {@link ScorchZones scorch
 * zone} of {@code fissionScorchRadius} around the multiblock's centre that {@link ScorchTicker}
 * keeps hurting living entities in for {@code fissionScorchDays}. Composed here so the block entity's
 * {@code failureAction()} stays a one-liner and the blast runs first — the zone records a place only,
 * never who was there.
 */
public final class ScorchZoneAction implements FailureAction {

    private final FailureAction blast;

    /** @param blast the action to run first (the explosion); this action records the scorch zone after it */
    public ScorchZoneAction(FailureAction blast) {
        this.blast = blast;
    }

    @Override
    public void apply(ServerLevel level, BlockPos pos, FailureContext ctx) {
        this.blast.apply(level, pos, ctx);
        if (!FissionConfig.scorchEnabled()) {
            return;
        }
        ScorchZones.get(level.getServer()).add(level.dimension().identifier().toString(), ctx.center(),
                FissionConfig.scorchRadius(), FissionConfig.scorchDays());
    }
}

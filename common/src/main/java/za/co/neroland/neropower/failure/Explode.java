package za.co.neroland.neropower.failure;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.protection.ProtectionCheck;

/**
 * The destructive failure: a blast centred on the multiblock, then the controller block removed.
 * Its radius is the machine's request clamped to {@code failureRadiusCap}
 * ({@link #effectiveRadius}); whether it breaks blocks at all follows {@code terrainDamageMode}
 * (auto = never on a dedicated server), exactly like NeroTech's fusion meltdown.
 *
 * <p>Multiplayer safety: when terrain damage is on, every block the blast could reach is checked
 * against the {@link ProtectionCheck} (spawn protection) and for unbreakability
 * ({@code destroySpeed < 0}, bedrock and friends). If anything inside the radius is protected the
 * blast shrinks to the multiblock's own footprint ({@link FailureContext#boundsRadius()}); if even
 * that footprint holds a protected block, the blast keeps its damage and knockback but breaks
 * nothing ({@code ExplosionInteraction.NONE}). A reactor built against spawn cannot be used to
 * crater it.
 */
public final class Explode implements FailureAction {

    private final int requestedRadius;

    /** @param requestedRadius the radius the machine asks for (blocks); capped by config at run time */
    public Explode(int requestedRadius) {
        this.requestedRadius = requestedRadius;
    }

    /** The radius the machine asks for, before the cap. */
    public int requestedRadius() {
        return this.requestedRadius;
    }

    /**
     * The blast radius actually used: {@code requested} clamped to {@code cap}
     * ({@code failureRadiusCap}), never below 1. Pure, so the balance is unit-testable.
     */
    public static int effectiveRadius(int requested, int cap) {
        return Math.max(1, Math.min(requested, cap));
    }

    @Override
    public void apply(ServerLevel level, BlockPos pos, FailureContext ctx) {
        int radius = effectiveRadius(this.requestedRadius, NeroPowerConfig.failureRadiusCap());
        // auto → no terrain damage on a dedicated server (a shared world), full damage otherwise.
        boolean terrainDamage = NeroPowerConfig.terrainDamage(level.getServer().isDedicatedServer());
        BlockPos center = ctx.center();
        if (terrainDamage && !clearOfProtection(level, center, radius)) {
            radius = Math.min(radius, ctx.boundsRadius());
            if (!clearOfProtection(level, center, radius)) {
                terrainDamage = false;
            }
        }
        level.explode(null, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D, radius,
                terrainDamage ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        level.removeBlock(pos, false);
    }

    /**
     * Whether no block within {@code radius} of {@code center} (a cube — the blast's bounding box) is
     * spawn-protected or unbreakable. At most a 33x33x33 scan at the config cap, once per failure.
     */
    static boolean clearOfProtection(ServerLevel level, BlockPos center, int radius) {
        ProtectionCheck protection = Protection.get();
        for (BlockPos scan : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (protection.isProtected(level, scan)) {
                return false;
            }
            BlockState state = level.getBlockState(scan);
            if (!state.isAir() && state.getDestroySpeed(level, scan) < 0.0F) {
                return false;
            }
        }
        return true;
    }
}

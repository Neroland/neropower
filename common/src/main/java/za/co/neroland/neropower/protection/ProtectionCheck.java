package za.co.neroland.neropower.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The seam every NeroPower authorisation and world-damage decision goes through: may this player
 * touch this block, and is this block off limits to a failure blast? The default,
 * {@link VanillaProtection}, knows only what vanilla knows (spawn protection, world border, adventure
 * rules). If Core ever ships a claim / region API, a richer implementation is installed through
 * {@link Protection#set} and every caller picks it up — no NeroPower code changes.
 *
 * <p>Never authorise by proximity ({@code getNearestPlayer}) — the acting player is always passed in.
 */
public interface ProtectionCheck {

    /** Whether {@code player} may interact with the block at {@code pos} (linking, configuring, unlinking). */
    boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos);

    /** Whether the block at {@code pos} must not be broken by a failure blast (no player involved). */
    boolean isProtected(ServerLevel level, BlockPos pos);
}

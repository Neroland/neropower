package za.co.neroland.neropower.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The default {@link ProtectionCheck}: vanilla rules only.
 * <ul>
 *   <li>{@link #mayInteract} is {@code Player.mayInteract} — spawn protection (operators exempt),
 *       world border and adventure-mode rules; the same call NeroTech's Configurator uses before it
 *       pairs Wireless Nodes.</li>
 *   <li>{@link #isProtected} is the server's spawn protection, evaluated without a player (a blast
 *       has no operator to exempt): overworld only, {@code spawn-protection} radius from
 *       {@code server.properties} measured as vanilla does — Chebyshev distance on x/z from the
 *       world's respawn point, any height. A radius of 0 (the singleplayer default) protects nothing.</li>
 * </ul>
 */
public final class VanillaProtection implements ProtectionCheck {

    public static final VanillaProtection INSTANCE = new VanillaProtection();

    private VanillaProtection() {
    }

    @Override
    public boolean mayInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return player.mayInteract(level, pos);
    }

    @Override
    public boolean isProtected(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        MinecraftServer server = level.getServer();
        // Only a dedicated server has a spawn-protection radius (server.properties); the integrated
        // server never protects spawn, exactly as vanilla's own spawn-protection check behaves.
        int radius = server instanceof DedicatedServer dedicated ? dedicated.getProperties().spawnProtection.get() : 0;
        if (radius <= 0) {
            return false;
        }
        BlockPos spawn = level.getRespawnData().pos();
        int dx = Math.abs(pos.getX() - spawn.getX());
        int dz = Math.abs(pos.getZ() - spawn.getZ());
        return Math.max(dx, dz) <= radius;
    }
}

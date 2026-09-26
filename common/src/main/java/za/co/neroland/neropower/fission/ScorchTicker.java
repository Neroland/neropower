package za.co.neroland.neropower.fission;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import za.co.neroland.neropower.link.NeroPowerLinkModule;

/**
 * The server tick hook that makes {@link ScorchZones} bite: every {@value #INTERVAL} ticks it
 * walks the (small) zone list, drops expired zones, and deals {@value #DAMAGE} magic damage to every
 * living entity inside a zone whose centre chunk is loaded. Unloaded zones are skipped, never
 * force-loaded. With {@code fissionScorchEnabled} off there are no zones and the pass is one
 * empty-list check.
 *
 * <p><b>Wiring (loader entry points):</b> call {@link #tick(MinecraftServer)} once per server tick
 * from the loader's end-of-server-tick event — Fabric {@code ServerTickEvents.END_SERVER_TICK},
 * NeoForge {@code ServerTickEvent.Post}, Forge {@code TickEvent.ServerTickEvent} (phase END). It is
 * safe to call on every tick; the interval throttle lives here.
 */
public final class ScorchTicker {

    /** Ticks between damage passes (1 s). */
    public static final int INTERVAL = 20;

    /** Damage per pass (half a heart). */
    public static final float DAMAGE = 1.0F;

    /** Vertical reach of a zone below and above its centre, in addition to its radius. */
    private static final int VERTICAL_PAD = 2;

    private ScorchTicker() {
    }

    /** Run one server tick; does the work only every {@value #INTERVAL} ticks. */
    public static void tick(MinecraftServer server) {
        // This per-loader server tick is also NeroPower's single reliable handle on the running
        // server, so the NeroLink module can resolve the requesting player when the companion bridge
        // queries it (NeroTech does the same from PollutionManager.tick). Cheap volatile write.
        NeroPowerLinkModule.rememberServer(server);
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        ScorchZones zones = ScorchZones.get(server);
        List<ScorchZones.Zone> live = zones.zones();
        if (live.isEmpty()) {
            return;
        }
        long today = ScorchZones.todayEpochDay();
        if (zones.expire(today) > 0) {
            live = zones.zones();
        }
        for (ScorchZones.Zone zone : live) {
            ServerLevel level = resolve(server, zone.dimension());
            if (level == null || !level.hasChunkAt(zone.center())) {
                continue; // unloaded (or a dimension that no longer exists) — skip, never force-load
            }
            int r = zone.radius();
            AABB box = new AABB(
                    zone.center().getX() - r, zone.center().getY() - r - VERTICAL_PAD, zone.center().getZ() - r,
                    zone.center().getX() + r + 1, zone.center().getY() + r + VERTICAL_PAD + 1, zone.center().getZ() + r + 1);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                entity.hurtServer(level, level.damageSources().magic(), DAMAGE);
            }
        }
    }

    private static ServerLevel resolve(MinecraftServer server, String dimension) {
        Identifier id;
        try {
            id = Identifier.parse(dimension);
        } catch (RuntimeException invalid) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}

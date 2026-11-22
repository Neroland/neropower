package za.co.neroland.neropower.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.content.RadiationContent;

/**
 * Radiation's server tick, after the store's decay ({@link ContaminationTicker}): the Geiger Counter
 * readings (also while off, so the HUD can say so), and while radiation runs the dosimeters, the
 * budgeted effect pass, the per-minute source emission and the dropped-rod pass.
 *
 * <p>Effects are applied in the moment from the chunk level and the worn shielding; nothing is
 * accumulated or kept per player, and no block is ever read for damage or changed, so claims are never
 * touched. Nothing here logs, and nothing names anyone.
 */
public final class RadiationTicker {

    /** The radiation damage type ({@code data/neropower/damage_type/radiation.json}). */
    public static final ResourceKey<DamageType> RADIATION_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "radiation"));

    private static final Identifier HOT_ROD = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "hot_spent_fuel_rod");
    private static final Identifier SPENT_ROD = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "spent_fuel_rod");
    private static final Identifier RESIDUE = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "fission_residue");

    /** Per-dimension cursor of the round-robin effect pass (a chunk key; world data). */
    private static final Map<ResourceKey<Level>, Long> EFFECT_CURSOR = new ConcurrentHashMap<>();

    /** Set once the radiation content is registered ({@code RadiationContent.init()}); off until then. */
    private static volatile boolean active;

    private RadiationTicker() {
    }

    /** Called from {@code RadiationContent.init()}: the items and components this tick uses now exist. */
    public static void activate() {
        active = true;
    }

    public static void tick(MinecraftServer server) {
        if (!active) {
            return;
        }
        boolean on = RadiationConfig.enabled(server.isDedicatedServer());
        int t = server.getTickCount();
        if (t % RadiationMath.GEIGER_INTERVAL == 0) {
            geigers(server, on);
        }
        if (!on) {
            return;
        }
        if (t % RadiationMath.DOSIMETER_INTERVAL == 0) {
            dosimeters(server);
        }
        boolean effects = t % Math.max(1, RadiationConfig.effectIntervalTicks()) == 0;
        boolean emit = t % ContaminationMap.DECAY_PERIOD_TICKS == 0;
        boolean dropped = t % RadiationMath.DROPPED_INTERVAL == 0;
        if (!effects && !emit && !dropped) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (effects) {
                effects(level);
            }
            if (emit) {
                RadiationSources.INSTANCE.emit(level, RadiationConfig.caskPerFace());
            }
            if (dropped) {
                dropped(level);
            }
        }
    }

    // --- Geiger ----------------------------------------------------------------------------

    private static void geigers(MinecraftServer server, boolean on) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            boolean mainGeiger = main.is(RadiationContent.GEIGER_COUNTER.get());
            boolean offGeiger = off.is(RadiationContent.GEIGER_COUNTER.get());
            if ((mainGeiger || offGeiger) && !player.isSpectator()) {
                GeigerReading reading = on ? read((ServerLevel) player.level(), player.blockPosition()) : GeigerReading.OFF;
                if (mainGeiger) {
                    write(main, reading);
                }
                if (offGeiger) {
                    write(off, reading);
                }
            }
            // A counter that left the hand keeps nothing.
            Container inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack != main && stack != off && stack.has(RadiationContent.GEIGER_READING.get())) {
                    stack.remove(RadiationContent.GEIGER_READING.get());
                }
            }
        }
    }

    private static void write(ItemStack stack, GeigerReading reading) {
        if (!reading.equals(stack.get(RadiationContent.GEIGER_READING.get()))) {
            stack.set(RadiationContent.GEIGER_READING.get(), reading);
        }
    }

    /** The reading at {@code pos}: the 3 x 3 chunk levels (rows north to south) and sources near. */
    static GeigerReading read(ServerLevel level, BlockPos pos) {
        ContaminationMap map = ContaminationState.get(level).map();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        List<Integer> levels = new ArrayList<>(9);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                levels.add(map.level(RadiationMath.chunkKey(cx + dx, cz + dz)));
            }
        }
        int sources = RadiationSources.INSTANCE.countNear(level, pos, RadiationMath.GEIGER_SOURCE_RANGE,
                RadiationMath.GEIGER_SOURCE_CAP);
        return new GeigerReading(levels, sources, true);
    }

    // --- Dosimeter -------------------------------------------------------------------------

    private static void dosimeters(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            BlockPos pos = player.blockPosition();
            int level = ContaminationState.get((ServerLevel) player.level()).map()
                    .level(RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ()));
            if (level <= 0) {
                continue;
            }
            Container inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.is(RadiationContent.DOSIMETER_BADGE.get())) {
                    DosimeterBadgeItem.expose(stack, level);
                }
            }
        }
    }

    // --- Effects ---------------------------------------------------------------------------

    private static void effects(ServerLevel level) {
        ContaminationMap map = ContaminationState.get(level).map();
        if (map.size() == 0) {
            return;
        }
        int limit = RadiationConfig.effectChunksPerPass();
        List<Long> chunks = map.nextAtLeast(EFFECT_CURSOR.get(level.dimension()), RadiationMath.WEAKNESS_AT, limit);
        if (chunks.isEmpty()) {
            EFFECT_CURSOR.remove(level.dimension());
            return;
        }
        EFFECT_CURSOR.put(level.dimension(), chunks.get(chunks.size() - 1));
        int minY = level.dimensionType().minY();
        int maxY = minY + level.dimensionType().height();
        DamageSource damage = null;
        for (long chunk : chunks) {
            int cx = RadiationMath.chunkX(chunk);
            int cz = RadiationMath.chunkZ(chunk);
            if (!level.hasChunk(cx, cz) || !level.isPositionEntityTicking(new BlockPos(cx << 4, minY, cz << 4))) {
                continue; // unloaded or lazy: skipped, never force-loaded
            }
            int chunkLevel = map.level(chunk);
            AABB box = new AABB(cx << 4, minY, cz << 4, (cx << 4) + 16, maxY, (cz << 4) + 16);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
                if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
                    continue;
                }
                if (za.co.neroland.neropower.protection.Protection.get().isProtected(level, entity.blockPosition())) {
                    continue; // nothing living on protected ground is harmed
                }
                int tier = RadiationMath.effectTier(true, chunkLevel, ShieldingArmour.pieces(entity));
                if (tier >= 1) {
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, RadiationMath.EFFECT_TICKS, 0));
                }
                if (tier >= 2) {
                    entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, RadiationMath.EFFECT_TICKS, 0));
                }
                if (tier >= 3) {
                    if (damage == null) {
                        damage = new DamageSource(damageType(level));
                    }
                    //? if >=1.21.2 {
                    entity.hurtServer(level, damage, RadiationMath.DAMAGE);
                    //?} else {
                    /*entity.hurt(damage, RadiationMath.DAMAGE);
                    *///?}
                }
            }
        }
    }

    private static Holder<DamageType> damageType(ServerLevel level) {
        //? if >=1.21.2 {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(RADIATION_DAMAGE);
        //?} else {
        /*return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(RADIATION_DAMAGE);
        *///?}
    }

    // --- Dropped rods and residue -------------------------------------------------------------

    private static void dropped(ServerLevel level) {
        int hot = RadiationConfig.droppedHotRod();
        int spent = RadiationConfig.droppedSpent();
        if (hot <= 0 && spent <= 0) {
            return;
        }
        Map<Long, Integer> perChunk = new TreeMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity item) || !entity.isAlive()) {
                continue;
            }
            int amount = droppedAmount(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()), hot, spent);
            if (amount > 0) {
                BlockPos pos = item.blockPosition();
                if (amount >= hot) {
                    // Hot rods on the ground glow with motes.
                    level.sendParticles(za.co.neroland.neropower.registry.ModParticles.RAD_MOTE.get(),
                            item.getX(), item.getY() + 0.3D, item.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
                }
                perChunk.merge(RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ()), amount, Integer::sum);
            }
        }
        perChunk.forEach((chunk, amount) -> RadiationSources.contaminate(level, chunk, RadiationMath.droppedCap(amount)));
    }

    /** Per item entity: a hot rod {@code hot}, a cooled rod or residue {@code spent}, anything else 0. */
    static int droppedAmount(Identifier id, int hot, int spent) {
        if (HOT_ROD.equals(id)) {
            return hot;
        }
        return SPENT_ROD.equals(id) || RESIDUE.equals(id) ? spent : 0;
    }
}

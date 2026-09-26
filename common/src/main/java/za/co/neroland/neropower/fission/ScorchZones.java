package za.co.neroland.neropower.fission;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Server-authoritative, persistent store of fission <b>scorch zones</b>: the ground a failed
 * reactor leaves poisoned for a few real days ({@code fissionScorchDays}). Each entry is a place —
 * dimension id, centre, half-edge radius — plus the UTC epoch day it expires on. Nothing here is
 * player data (POPIA/GDPR): no owner, no victims, no names.
 *
 * <p>Loaded through Core's crash-safe {@link SavedDataRecovery} on the overworld, exactly like
 * NeroTech's pollution store, so a corrupt file degrades to the last backup or a fresh store instead
 * of crashing every server tick that touches it.
 */
public final class ScorchZones extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "scorch_zones");

    public static final SavedDataType<ScorchZones> TYPE =
            new SavedDataType<>(ID, ScorchZones::new, codec(), null);

    /**
     * One scorch zone: a cube of half-edge {@code radius} around {@code center} in {@code dimension},
     * gone once the UTC calendar day passes {@code expiryEpochDay}.
     */
    public record Zone(String dimension, BlockPos center, int radius, long expiryEpochDay) {

        static final Codec<Zone> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("dimension").forGetter(Zone::dimension),
                Codec.LONG.fieldOf("center").forGetter(zone -> zone.center().asLong()),
                Codec.INT.fieldOf("radius").forGetter(Zone::radius),
                Codec.LONG.fieldOf("expiry").forGetter(Zone::expiryEpochDay)
        ).apply(inst, (dimension, center, radius, expiry) -> new Zone(dimension, BlockPos.of(center), radius, expiry)));

        /** Whether this zone has passed its expiry day. */
        public boolean expired(long todayEpochDay) {
            return todayEpochDay > this.expiryEpochDay;
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    public ScorchZones() {
    }

    /** The store for this server (overworld saved data) through Core's crash-safe accessor. */
    public static ScorchZones get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, ScorchZones::new, ID.toString());
    }

    /** Today's UTC epoch day — the clock every expiry is measured against. */
    public static long todayEpochDay() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** Add a zone expiring {@code days} calendar days from today. */
    public void add(String dimension, BlockPos center, int radius, int days) {
        this.zones.add(new Zone(dimension, center.immutable(), Math.max(1, radius),
                todayEpochDay() + Math.max(1, days)));
        setDirty();
    }

    /** A read-only snapshot of the live zones. */
    public List<Zone> zones() {
        return List.copyOf(this.zones);
    }

    /** Drop every zone whose expiry day has passed; returns how many were removed. */
    public int expire(long todayEpochDay) {
        int before = this.zones.size();
        this.zones.removeIf(zone -> zone.expired(todayEpochDay));
        int removed = before - this.zones.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** Remove every zone (admin reset). */
    public void clear() {
        if (!this.zones.isEmpty()) {
            this.zones.clear();
            setDirty();
        }
    }

    // --- persistence ------------------------------------------------------------------

    private static Codec<ScorchZones> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Zone.CODEC.listOf().optionalFieldOf("zones", List.of()).forGetter(ScorchZones::zones)
        ).apply(inst, ScorchZones::fromData));
    }

    private static ScorchZones fromData(List<Zone> zones) {
        ScorchZones state = new ScorchZones();
        state.zones.addAll(zones);
        return state;
    }
}

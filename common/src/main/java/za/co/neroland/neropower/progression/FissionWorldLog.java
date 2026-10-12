package za.co.neroland.neropower.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;
import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * The world's fission log: which dimensions have ever had a Fission Reactor burn fuel. The first
 * burn in a dimension publishes one rising crossing on the {@link #FISSION_OUTPUT
 * neropower:fission_output} channel (Core's {@link ThresholdEvents}, attributed to NeroPower) with
 * the <b>dimension id</b> as scope, so a server-scope "world log" quest or another mod can hear
 * "fission has started here" — once per dimension, ever.
 *
 * <p>Nothing here is player data (POPIA/GDPR): the store holds dimension ids only, and the crossing
 * names a dimension, never a player, an owner or a block position. Loaded through Core's crash-safe
 * {@link SavedDataRecovery} on the overworld, like {@code ScorchZones}.
 */
public final class FissionWorldLog extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "fission_log");

    /** The threshold channel the first burn in a dimension is published on. */
    public static final Identifier FISSION_OUTPUT =
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "fission_output");

    public static final SavedDataType<FissionWorldLog> TYPE =
            new SavedDataType<>(ID, FissionWorldLog::new, codec(), null);

    private final Set<String> burned = new TreeSet<>();

    public FissionWorldLog() {
    }

    /** The store for this server (overworld saved data) through Core's crash-safe accessor. */
    public static FissionWorldLog get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, FissionWorldLog::new, ID.toString());
    }

    /**
     * Record that a reactor in {@code level} burned fuel; on the first burn in that dimension fires
     * the {@link #FISSION_OUTPUT} crossing (scope = dimension id, value = threshold = 1, rising).
     *
     * @return {@code true} if this was the dimension's first burn
     */
    public static boolean recordBurn(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        String dimension = level.dimension().identifier().toString();
        FissionWorldLog log = get(server);
        if (!log.burned.add(dimension)) {
            return false;
        }
        log.setDirty();
        ThresholdEvents.fire(NeroPowerCommon.MOD_ID,
                new ThresholdEvents.ThresholdCrossing(FISSION_OUTPUT, dimension, 1L, 1L, true));
        return true;
    }

    /** Whether any reactor has burned fuel in {@code dimension} (an id string). */
    public boolean hasBurned(String dimension) {
        return this.burned.contains(dimension);
    }

    /** A read-only, sorted snapshot of the dimensions that have seen fission. */
    public List<String> burnedDimensions() {
        return List.copyOf(this.burned);
    }

    // --- persistence ------------------------------------------------------------------

    private static Codec<FissionWorldLog> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.listOf().optionalFieldOf("burned", List.of()).forGetter(FissionWorldLog::burnedDimensions)
        ).apply(inst, FissionWorldLog::fromData));
    }

    private static FissionWorldLog fromData(List<String> burned) {
        FissionWorldLog state = new FissionWorldLog();
        state.burned.addAll(new ArrayList<>(burned));
        return state;
    }
}

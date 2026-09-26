package za.co.neroland.neropower.data;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * The <b>pending link-owner erasure</b> set (POPIA/GDPR, Stage 8). A beam transmitter / relay keeps
 * the linking player's UUID in its own block-entity NBT ({@code LinkOwner}), and an erase request
 * cannot reach a block entity whose chunk is not loaded. So {@link NeroPowerDataErasure} records the
 * erased UUID here with the UTC epoch day of the request, and every transmitter / relay checks
 * {@link #isPending(UUID)} on its first server tick after (re)load — and again whenever
 * {@link #erasureEpoch()} moves, i.e. an erase request landed while it was loaded — and drops its
 * link owner if listed. Exactly NeroTech's mechanism for machine owners
 * ({@code PollutionAttributionPrefs}); NeroPower keeps its own store so it never writes into
 * NeroTech's file.
 *
 * <p><b>Retention: {@value #ERASURE_RETENTION_DAYS} days.</b> Rows expire that many days after the
 * request (purged on store load and on each new mark), which bounds how long an erased UUID lingers
 * here. A transmitter unloaded for longer than that keeps a UUID nobody can be matched to any more
 * — the player's data in every other Neroland store is gone by then — and drops it the next time
 * its link is changed or the block is broken.
 *
 * <p>Loaded through Core's crash-safe {@link SavedDataRecovery} on the overworld, exactly like
 * {@code ScorchZones}. The Minecraft-free parts ({@link #mark}, {@link #isPending},
 * {@link #purgeExpired}) are unit-tested on a bare instance.
 */
public final class NeroPowerErasureState extends SavedData {

    /** How long a pending erasure row is kept before it expires (days). */
    public static final int ERASURE_RETENTION_DAYS = 30;

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "erasure_state");

    public static final SavedDataType<NeroPowerErasureState> TYPE =
            new SavedDataType<>(ID, NeroPowerErasureState::new, codec(), null);

    /** Erased players whose link owners still have to be cleared → epoch day of the request. */
    private final Map<UUID, Long> pending = new HashMap<>();

    /**
     * Bumped on every {@link #mark}; loaded transmitters compare it per tick (one static read) and
     * re-check their link owner only when it moved. Process-local, never persisted: a fresh process
     * starts every block entity at "unchecked" anyway.
     */
    private static volatile int erasureEpoch;

    public NeroPowerErasureState() {
    }

    /** The store for this server (overworld saved data) through Core's crash-safe accessor. */
    public static NeroPowerErasureState get(MinecraftServer server) {
        return SavedDataRecovery.get(server.overworld(), TYPE, NeroPowerErasureState::new, ID.toString());
    }

    /** Whether {@code owner} is pending erasure on this server — the block entities' one-call check. */
    public static boolean isPending(MinecraftServer server, UUID owner) {
        return owner != null && server != null && get(server).isPending(owner);
    }

    /** Today's UTC epoch day — the clock every expiry is measured against. */
    public static long todayEpochDay() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** The current erasure epoch — bumped per erase request; wraps harmlessly. */
    public static int erasureEpoch() {
        return erasureEpoch;
    }

    /**
     * Record that {@code player} was erased on {@code epochDay}: every transmitter / relay they
     * linked drops its link owner on its next tick (loaded) or next load (unloaded). Also purges rows
     * older than {@value #ERASURE_RETENTION_DAYS} days so the set never grows without bound.
     */
    public void mark(UUID player, long epochDay) {
        purgeExpired(epochDay);
        this.pending.put(player, epochDay);
        setDirty();
        erasureEpoch++;
    }

    /** Whether {@code owner} was erased recently enough that block entities must still drop them. */
    public boolean isPending(UUID owner) {
        return owner != null && this.pending.containsKey(owner);
    }

    /** Number of pending rows (diagnostics and tests). */
    public int size() {
        return this.pending.size();
    }

    /** Drop rows older than {@value #ERASURE_RETENTION_DAYS} days as of {@code today}; returns how many. */
    public int purgeExpired(long today) {
        Iterator<Map.Entry<UUID, Long>> it = this.pending.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (today - entry.getValue() > ERASURE_RETENTION_DAYS) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    // --- persistence ------------------------------------------------------------------

    /** One persisted row: the erased UUID (as text) and the request's epoch day. */
    private record Row(String uuid, long epochDay) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("uuid").forGetter(Row::uuid),
                Codec.LONG.fieldOf("day").forGetter(Row::epochDay)
        ).apply(inst, Row::new));
    }

    private static Codec<NeroPowerErasureState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Row.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(NeroPowerErasureState::rows)
        ).apply(inst, NeroPowerErasureState::fromRows));
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        this.pending.forEach((uuid, day) -> out.add(new Row(uuid.toString(), day)));
        return out;
    }

    private static NeroPowerErasureState fromRows(List<Row> rows) {
        NeroPowerErasureState state = new NeroPowerErasureState();
        for (Row row : rows) {
            try {
                state.pending.put(UUID.fromString(row.uuid()), row.epochDay());
            } catch (IllegalArgumentException ignored) {
                // skip malformed UUID rows
            }
        }
        // Expired rows die on load, so a long-idle world does not carry them forward.
        state.purgeExpired(todayEpochDay());
        return state;
    }
}

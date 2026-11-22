package za.co.neroland.neropower.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * The beam link owner <b>erasure tombstones</b> (POPIA/GDPR). A beam transmitter / relay keeps the linking
 * player's UUID in its own block-entity NBT ({@code LinkOwnerMost} / {@code LinkOwnerLeast}), and an erase
 * request cannot reach a block entity whose chunk is not loaded. So {@link NeroPowerDataErasure} records
 * the erased player here, and every transmitter / relay checks {@link #shouldDropLinkOwner} on its first
 * server tick after (re)load, and again whenever {@link #erasureEpoch()} moves (an erase request landed
 * while it was loaded). It then drops its link owner if that link was made on or before the erasure day.
 *
 * <p><b>Pseudonymised, and kept until the job is done.</b> A tombstone is never the UUID: it is a 128-bit
 * truncation of SHA-256 over a random per-world salt (saved once in this store) and the UUID, with the
 * UTC epoch day of the request. A tombstone does <b>not expire</b>. NeroPower keeps no index of where
 * transmitters stand, so it cannot tell when the last one has been scrubbed. Any chunk may hold one and
 * stay unloaded for years, and an expired tombstone would let it load with the erased UUID intact
 *. The cost is 16 bytes and a day per erased player, capped at
 * {@value #MAX_TOMBSTONES} (the oldest goes first, logged with a count).
 *
 * <p><b>Returning players.</b> A link made <i>after</i> the erasure day carries a {@code LinkDay} newer
 * than the tombstone, so it survives: the tombstone only ever removes links the erasure was about. A link
 * made on the erasure day itself is dropped (the more private choice when the order is unknown).
 *
 * <p>Older saves stored plain UUID rows ({@code pending}); they are hashed on load and the plain rows
 * are never written again. Loaded through Core's crash-safe {@link SavedDataRecovery} on the overworld.
 * The Minecraft-free parts are unit-tested on a bare instance.
 */
public final class NeroPowerErasureState extends SavedData {

    /** Most tombstones kept; reaching it drops the oldest. */
    public static final int MAX_TOMBSTONES = 65_536;

    /** {@code LinkDay} of a link saved before 0.4.0 (no day recorded): older than any erasure. */
    public static final long UNKNOWN_LINK_DAY = Long.MIN_VALUE;

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "erasure_state");

    public static final SavedDataType<NeroPowerErasureState> TYPE =
            SavedDataCompat.type(ID, NeroPowerErasureState::new, codec());

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Per-world salt, hex; empty until the first tombstone. Never logged or sent. */
    private String salt = "";

    /** Hashed erased player → epoch day of the (latest) request, oldest first. */
    private final Map<String, Long> tombstones = new LinkedHashMap<>();

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

    /** Whether {@code owner} has an erasure tombstone on this server. */
    public static boolean isPending(MinecraftServer server, UUID owner) {
        return owner != null && server != null && get(server).isPending(owner);
    }

    /**
     * The block entities' one-call check: whether a link owner recorded on {@code linkDay} must be dropped
     * because that player was erased on or after that day.
     */
    public static boolean shouldDropLinkOwner(MinecraftServer server, UUID owner, long linkDay) {
        return owner != null && server != null && get(server).erasedOnOrAfter(owner, linkDay);
    }

    /** Today's UTC epoch day: the clock every link day and tombstone is stamped with. */
    public static long todayEpochDay() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** The current erasure epoch — bumped per erase request; wraps harmlessly. */
    public static int erasureEpoch() {
        return erasureEpoch;
    }

    /**
     * Record that {@code player} was erased on {@code epochDay}: every transmitter / relay they linked on or
     * before that day drops its link owner on its next tick (loaded) or next load (unloaded), however long
     * from now that is.
     */
    public void mark(UUID player, long epochDay) {
        if (player == null) {
            return;
        }
        String key = hash(player);
        Long previous = this.tombstones.remove(key);
        this.tombstones.put(key, previous == null ? epochDay : Math.max(previous, epochDay));
        enforceCap();
        setDirty();
        erasureEpoch++;
    }

    /** Whether {@code owner} has a tombstone (was erased on this server). */
    public boolean isPending(UUID owner) {
        return owner != null && !this.salt.isEmpty() && this.tombstones.containsKey(hash(owner));
    }

    /** Whether {@code owner} was erased on or after {@code linkDay} (so a link made then must lose its owner). */
    public boolean erasedOnOrAfter(UUID owner, long linkDay) {
        if (owner == null || this.salt.isEmpty()) {
            return false;
        }
        Long erasedOn = this.tombstones.get(hash(owner));
        return erasedOn != null && linkDay <= erasedOn;
    }

    /** Number of tombstones (diagnostics and tests). */
    public int size() {
        return this.tombstones.size();
    }

    /** Whether any stored key equals {@code text} (tests: proves no plain UUID is kept). */
    boolean storesPlain(String text) {
        return this.tombstones.containsKey(text) || this.salt.equals(text);
    }

    private void enforceCap() {
        int dropped = 0;
        Iterator<String> it = this.tombstones.keySet().iterator();
        while (this.tombstones.size() > MAX_TOMBSTONES && it.hasNext()) {
            it.next();
            it.remove();
            dropped++;
        }
        if (dropped > 0) {
            NeroPowerCommon.LOGGER.warn("[NeroPower] erasure tombstone cap reached; dropped {} oldest", dropped);
        }
    }

    /** Salted SHA-256 of the UUID, first 16 bytes, hex. One-way; never logged or sent. */
    private String hash(UUID player) {
        if (this.salt.isEmpty()) {
            byte[] bytes = new byte[16];
            RANDOM.nextBytes(bytes);
            this.salt = HexFormat.of().formatHex(bytes);
            setDirty();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(this.salt.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) ':');
            digest.update(player.toString().getBytes(StandardCharsets.UTF_8));
            byte[] full = digest.digest();
            return HexFormat.of().formatHex(full, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    // --- persistence ------------------------------------------------------------------

    /** One tombstone: the salted hash and the request's epoch day. */
    record Tomb(String hash, long epochDay) {
        static final Codec<Tomb> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("h").forGetter(Tomb::hash),
                Codec.LONG.fieldOf("day").forGetter(Tomb::epochDay)
        ).apply(inst, Tomb::new));
    }

    /** A pre-0.4.0 row: the erased UUID as text. Read only, hashed on load, never written. */
    record LegacyRow(String uuid, long epochDay) {
        static final Codec<LegacyRow> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("uuid").forGetter(LegacyRow::uuid),
                Codec.LONG.fieldOf("day").forGetter(LegacyRow::epochDay)
        ).apply(inst, LegacyRow::new));
    }

    private static Codec<NeroPowerErasureState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.optionalFieldOf("salt", "").forGetter(s -> s.salt),
                Tomb.CODEC.listOf().optionalFieldOf("tombstones", List.of()).forGetter(NeroPowerErasureState::tombs),
                LegacyRow.CODEC.listOf().optionalFieldOf("pending", List.of()).forGetter(s -> List.of())
        ).apply(inst, NeroPowerErasureState::fromSaved));
    }

    private List<Tomb> tombs() {
        List<Tomb> out = new ArrayList<>(this.tombstones.size());
        this.tombstones.forEach((hash, day) -> out.add(new Tomb(hash, day)));
        return out;
    }

    /** Rebuild from a save; plain legacy rows are hashed (with a salt made now if the save had none). */
    static NeroPowerErasureState fromSaved(String salt, List<Tomb> tombs, List<LegacyRow> legacy) {
        NeroPowerErasureState state = new NeroPowerErasureState();
        state.salt = salt == null ? "" : salt;
        for (Tomb tomb : tombs) {
            state.tombstones.put(tomb.hash(), tomb.epochDay());
        }
        for (LegacyRow row : legacy) {
            try {
                UUID uuid = UUID.fromString(row.uuid());
                String key = state.hash(uuid);
                Long previous = state.tombstones.get(key);
                state.tombstones.put(key, previous == null ? row.epochDay() : Math.max(previous, row.epochDay()));
            } catch (IllegalArgumentException ignored) {
                // skip malformed rows
            }
        }
        if (!legacy.isEmpty()) {
            state.setDirty(); // rewrite without the plain rows
        }
        state.enforceCap();
        return state;
    }

    /** Test seam: load a pre-0.4.0 plain row set, as an old save would. */
    static NeroPowerErasureState fromLegacyRows(Map<UUID, Long> rows) {
        List<LegacyRow> legacy = new ArrayList<>();
        rows.forEach((uuid, day) -> legacy.add(new LegacyRow(uuid.toString(), day)));
        return fromSaved("", List.of(), legacy);
    }
}

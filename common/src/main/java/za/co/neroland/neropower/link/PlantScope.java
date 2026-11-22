package za.co.neroland.neropower.link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

/**
 * The pure half of link schema 2's plant sections: who sees which machine, in what order, and what an
 * entry may carry. Nothing here touches the world, so every rule is unit-tested exactly as
 * {@link NeroPowerLinkModule} applies it.
 *
 * <p><b>Reach.</b> An entry is listed only for an <i>online</i> requester, in the machine's dimension,
 * within {@value LinkScope#PROXIMITY_CHUNKS} chunks on the chunk grid ({@link LinkScope#withinProximity});
 * the entry's {@code band} ({@code near} / {@code in_range}) is its only distance information; an owned
 * machine only for its owner ({@link LinkScope#visibleTo}). The module only ever offers machines in
 * loaded chunks. There is no server-wide roster: an offline or unresolved requester gets an empty list.
 *
 * <p><b>Order.</b> The in-reach set is shuffled before it is capped at {@link #MAX_ENTRIES}, so list
 * order and handle indices carry no direction or chunk order.
 *
 * <p><b>Shape.</b> An entry never carries a coordinate, a dimension, a distance, a direction, an owner
 * UUID or a name ({@link #FORBIDDEN_KEYS}); the module strips them before handles are attached, so a
 * field added by mistake later cannot leak.
 */
final class PlantScope {

    /** Most entries in one section response. */
    static final int MAX_ENTRIES = 64;

    /** Keys no plant entry may carry (schema 1 had {@code x/y/z/dim} and beam {@code distance}). */
    static final Set<String> FORBIDDEN_KEYS = Set.of("x", "y", "z", "dim", "dimension", "pos", "position",
            "distance", "direction", "owner", "ownerId", "ownerName", "player", "playerName", "uuid", "name");

    /** A UUID in any string value is a leak, whatever the key. */
    static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private PlantScope() {
    }

    /** The asking player as the module resolved them: online, in {@code dim}, at a block position. */
    record Viewer(UUID id, String dim, int x, int y, int z) {
    }

    /**
     * One machine the module found in a loaded chunk near the viewer.
     *
     * @param target the server-side handle target (never serialised)
     * @param owner  the recorded owner used for scoping (compared, never emitted)
     * @param entry  the entry's non-location fields
     */
    record Candidate<T>(T target, String dim, int x, int y, int z, Optional<UUID> owner, JsonObject entry) {
    }

    /** What a section lists: the entries (already stripped, with {@code ownedByYou}) and their targets. */
    record Selection<T>(List<JsonObject> entries, List<T> targets, boolean truncated) {
    }

    /** Whether {@code viewer} may see a machine at {@code (dim, x, y, z)} with {@code owner}. */
    static boolean inScope(@Nullable Viewer viewer, String dim, int x, int y, int z, Optional<UUID> owner) {
        if (viewer == null || viewer.id() == null || dim == null || !dim.equals(viewer.dim())) {
            return false;
        }
        return LinkScope.withinProximity(viewer.x(), viewer.y(), viewer.z(), x, y, z)
                && LinkScope.visibleTo(owner, viewer.id());
    }

    /**
     * Filter {@code found} to what {@code viewer} may see, shuffle, cap at {@link #MAX_ENTRIES}, strip the
     * forbidden keys and add {@code owned} / {@code ownedByYou} (a boolean about the viewer only).
     */
    static <T> Selection<T> select(@Nullable Viewer viewer, List<Candidate<T>> found, Random random) {
        List<Candidate<T>> visible = new ArrayList<>();
        for (Candidate<T> c : found) {
            if (inScope(viewer, c.dim(), c.x(), c.y(), c.z(), c.owner())) {
                visible.add(c);
            }
        }
        Collections.shuffle(visible, random);
        boolean truncated = visible.size() > MAX_ENTRIES;
        if (truncated) {
            visible.subList(MAX_ENTRIES, visible.size()).clear();
        }
        List<JsonObject> entries = new ArrayList<>(visible.size());
        List<T> targets = new ArrayList<>(visible.size());
        for (Candidate<T> c : visible) {
            JsonObject entry = strip(c.entry());
            boolean mine = viewer != null && LinkScope.isOwner(c.owner(), viewer.id());
            // `owned` is schema 1's name; `ownedByYou` is the schema-2 name NeroTech uses. Both mean
            // "you are the recorded owner" and say nothing about anyone else.
            entry.addProperty("owned", mine);
            entry.addProperty("ownedByYou", mine);
            // The only distance information: a coarse band on the chunk grid ("near" or "in_range").
            if (viewer != null) {
                entry.addProperty("band", LinkScope.band(viewer.x(), viewer.z(), c.x(), c.z()));
            }
            entries.add(entry);
            targets.add(c.target());
        }
        return new Selection<>(entries, targets, truncated);
    }

    /** A copy of {@code entry} without any {@link #FORBIDDEN_KEYS} and without UUID-shaped strings. */
    static JsonObject strip(JsonObject entry) {
        JsonObject out = new JsonObject();
        if (entry == null) {
            return out;
        }
        for (var e : entry.entrySet()) {
            if (FORBIDDEN_KEYS.contains(e.getKey()) || e.getKey().startsWith("_")) {
                continue;
            }
            JsonElement value = e.getValue();
            if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && UUID_PATTERN.matcher(value.getAsString()).find()) {
                continue;
            }
            out.add(e.getKey(), value == null ? null : value.deepCopy());
        }
        return out;
    }

    /** The section envelope: {@code section}, {@code schema}, {@code asOf}, {@code scope}, {@code radius}, {@code machines}. */
    static JsonObject envelope(String section, int schema, long asOf) {
        JsonObject out = new JsonObject();
        out.addProperty("section", section);
        out.addProperty("schema", schema);
        out.addProperty("asOf", asOf);
        out.addProperty("scope", "proximity");
        out.addProperty("radius", LinkScope.PROXIMITY_BLOCKS);
        out.add("machines", new JsonArray());
        return out;
    }

    /** Attach {@code entries} with their handles to the envelope's {@code machines}. */
    static void fill(JsonObject envelope, List<JsonObject> entries, List<String> handles, boolean truncated) {
        JsonArray machines = envelope.getAsJsonArray("machines");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i);
            entry.addProperty("h", handles.get(i));
            machines.add(entry);
        }
        envelope.addProperty("truncated", truncated);
    }

    /** The readout word the gauge kit uses for codes 0..5 (shared by every NeroPower family). */
    static String readoutWord(int code) {
        return switch (code) {
            case 1 -> "working";
            case 2 -> "starved";
            case 3 -> "hot";
            case 4 -> "tripped";
            case 5 -> "failing";
            default -> "idle";
        };
    }

    /** {@code amount / capacity} as permille 0..1000; 0 when there is no capacity. */
    static int permille(long amount, long capacity) {
        if (capacity <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(1000L, amount * 1000L / capacity));
    }

    /**
     * Whether {@code json} (recursively) carries a forbidden key or a UUID-shaped string. Used by tests
     * and as a last-line check in the module before a section leaves the server.
     */
    static boolean leaks(JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return false;
        }
        if (json.isJsonPrimitive()) {
            return json.getAsJsonPrimitive().isString() && UUID_PATTERN.matcher(json.getAsString()).find();
        }
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                if (leaks(e)) {
                    return true;
                }
            }
            return false;
        }
        for (var e : json.getAsJsonObject().entrySet()) {
            if (FORBIDDEN_KEYS.contains(e.getKey()) || leaks(e.getValue())) {
                return true;
            }
        }
        return false;
    }
}

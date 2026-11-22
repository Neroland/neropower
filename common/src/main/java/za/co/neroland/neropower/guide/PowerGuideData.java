package za.co.neroland.neropower.guide;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.jetbrains.annotations.Nullable;

/**
 * The Power Guide's pages, read from {@code data/neropower/guide/index.json} and one file per chapter
 * (chapter, then ordered steps: icon, title key, text key, the item that completes it). Pure data with
 * no Minecraft types, so the client viewer and the tests share it. Tolerant: a missing or broken file
 * drops that chapter (or yields an empty guide) instead of throwing.
 *
 * <p>No player data: the guide keeps no state of its own and records nothing.
 */
public final class PowerGuideData {

    /** Classpath root of the guide files. */
    public static final String ROOT = "data/neropower/guide/";

    /** One step of a chapter. */
    public record Step(String id, String icon, String titleKey, String textKey, @Nullable String completedBy) {
    }

    /** One chapter: its id, title key and ordered steps. */
    public record Chapter(String id, String titleKey, List<Step> steps) {

        /** Whether a step of this chapter shows or is completed by {@code itemId} ({@code neropower:x}). */
        public boolean mentions(String itemId) {
            for (Step step : this.steps) {
                if (itemId.equals(step.icon()) || itemId.equals(step.completedBy())) {
                    return true;
                }
            }
            return false;
        }

        /** The index of the first step showing or completed by {@code itemId}, or 0. */
        public int stepFor(String itemId) {
            for (int i = 0; i < this.steps.size(); i++) {
                Step step = this.steps.get(i);
                if (itemId.equals(step.icon()) || itemId.equals(step.completedBy())) {
                    return i;
                }
            }
            return 0;
        }
    }

    private final String titleKey;
    private final List<Chapter> chapters;

    private PowerGuideData(String titleKey, List<Chapter> chapters) {
        this.titleKey = titleKey;
        this.chapters = List.copyOf(chapters);
    }

    /** The guide's title key. */
    public String titleKey() {
        return this.titleKey;
    }

    /** The chapters in index order (possibly empty). */
    public List<Chapter> chapters() {
        return this.chapters;
    }

    /** The index of the first chapter mentioning {@code itemId}, or -1. */
    public int chapterFor(String itemId) {
        for (int i = 0; i < this.chapters.size(); i++) {
            if (this.chapters.get(i).mentions(itemId)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Read the guide through {@code open} (a classpath path such as {@code data/neropower/guide/index.json}
     * to a stream, or null when missing).
     */
    public static PowerGuideData load(Function<String, InputStream> open) {
        JsonObject index = read(open, ROOT + "index.json");
        if (index == null) {
            return new PowerGuideData("gui.neropower.power_guide.title", List.of());
        }
        String title = string(index, "title", "gui.neropower.power_guide.title");
        List<Chapter> chapters = new ArrayList<>();
        JsonArray ids = index.has("chapters") && index.get("chapters").isJsonArray()
                ? index.getAsJsonArray("chapters") : new JsonArray();
        for (JsonElement idElement : ids) {
            if (!idElement.isJsonPrimitive()) {
                continue;
            }
            String id = idElement.getAsString();
            if (!id.matches("[a-z0-9_]+")) {
                continue;
            }
            Chapter chapter = chapter(id, read(open, ROOT + id + ".json"));
            if (chapter != null) {
                chapters.add(chapter);
            }
        }
        return new PowerGuideData(title, chapters);
    }

    /** Read the guide from this class's own classpath (the mod jar, or the test resources). */
    public static PowerGuideData fromClasspath() {
        ClassLoader loader = PowerGuideData.class.getClassLoader();
        return load(path -> {
            InputStream in = PowerGuideData.class.getResourceAsStream("/" + path);
            return in != null || loader == null ? in : loader.getResourceAsStream(path);
        });
    }

    @Nullable
    private static Chapter chapter(String id, @Nullable JsonObject json) {
        if (json == null || !json.has("steps") || !json.get("steps").isJsonArray()) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("steps")) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String stepId = string(o, "id", null);
            String titleKey = string(o, "title", null);
            String textKey = string(o, "text", null);
            if (stepId == null || titleKey == null || textKey == null) {
                continue;
            }
            steps.add(new Step(stepId, string(o, "icon", ""), titleKey, textKey, string(o, "completed_by_item", null)));
        }
        if (steps.isEmpty()) {
            return null;
        }
        return new Chapter(id, string(json, "title", "gui.neropower.power_guide.chapter." + id), steps);
    }

    @Nullable
    private static String string(JsonObject o, String key, @Nullable String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    @Nullable
    private static JsonObject read(Function<String, InputStream> open, String path) {
        try (InputStream in = open.apply(path)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                return json.isJsonObject() ? json.getAsJsonObject() : null;
            }
        } catch (IOException | RuntimeException e) {
            return null; // tolerant: a broken page drops out, the guide still opens
        }
    }
}

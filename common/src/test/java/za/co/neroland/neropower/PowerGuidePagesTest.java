package za.co.neroland.neropower;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.guide.PowerGuideData;

/**
 * Keeps the Power Guide pages honest: every chapter in the index loads, every icon and completing item is a
 * registered NeroPower item (the registration lists {@link ContentCompletenessTest} keeps), every title and
 * text key exists in {@code en_us.json}, step ids are unique, and a chapter fits a 16-bit completion mask.
 * Also: the loader tolerates missing and broken files, and the Fuel Cell page carries the configured 60 NE.
 */
class PowerGuidePagesTest {

    private static Set<String> registered() {
        Set<String> ids = new HashSet<>();
        ContentCompletenessTest.BLOCKS.forEach(b -> ids.add("neropower:" + b));
        ContentCompletenessTest.ITEMS.forEach(i -> ids.add("neropower:" + i));
        return ids;
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = PowerGuidePagesTest.class.getClassLoader()
                .getResourceAsStream("assets/neropower/lang/en_us.json")) {
            assertTrue(in != null, "en_us.json on the test classpath");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyPageResolves() throws IOException {
        PowerGuideData guide = PowerGuideData.fromClasspath();
        JsonObject lang = lang();
        Set<String> ids = registered();
        List<String> problems = new ArrayList<>();
        assertEquals(8, guide.chapters().size(), "every chapter in index.json loads");
        if (!lang.has(guide.titleKey())) {
            problems.add("no lang for " + guide.titleKey());
        }
        Set<String> stepIds = new HashSet<>();
        for (PowerGuideData.Chapter c : guide.chapters()) {
            if (!lang.has(c.titleKey())) {
                problems.add(c.id() + ": no lang for " + c.titleKey());
            }
            if (c.steps().size() > 16) {
                problems.add(c.id() + ": more than 16 steps");
            }
            for (PowerGuideData.Step s : c.steps()) {
                if (!stepIds.add(s.id())) {
                    problems.add(s.id() + ": step id used twice");
                }
                if (!ids.contains(s.icon())) {
                    problems.add(s.id() + ": icon " + s.icon() + " is not a registered NeroPower item");
                }
                if (s.completedBy() != null && !ids.contains(s.completedBy())) {
                    problems.add(s.id() + ": completed_by_item " + s.completedBy() + " is not registered");
                }
                for (String key : List.of(s.titleKey(), s.textKey())) {
                    if (!lang.has(key)) {
                        problems.add(s.id() + ": no lang for " + key);
                    } else if (key.equals(s.textKey()) && !lang.get(key).getAsString().contains("Next:")) {
                        problems.add(s.id() + ": text does not name the next step");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void machineScreensFindTheirChapter() {
        PowerGuideData guide = PowerGuideData.fromClasspath();
        for (String block : List.of("turbine_helm", "fuel_cell", "smes_controller", "vitrifier", "circuit_breaker")) {
            assertTrue(guide.chapterFor("neropower:" + block) >= 0, block + " opens a chapter");
        }
        int generation = guide.chapterFor("neropower:fuel_cell");
        assertEquals("generation", guide.chapters().get(generation).id());
        assertEquals(-1, guide.chapterFor("minecraft:stone"));
    }

    @Test
    void fuelCellPageUsesTheConfiguredYield() throws IOException {
        String text = lang().get("gui.neropower.power_guide.step.fuel_cell.text").getAsString();
        assertTrue(text.contains("60 NE") && !text.contains("200"), "60 NE per hydrogen unit");
    }

    @Test
    void loaderIsTolerant() {
        assertTrue(PowerGuideData.load(path -> null).chapters().isEmpty(), "no index: an empty guide");
        Map<String, String> files = Map.of(
                PowerGuideData.ROOT + "index.json", "{\"title\":\"t\",\"chapters\":[\"a\",\"b\",\"../x\",\"missing\"]}",
                PowerGuideData.ROOT + "a.json", "{\"steps\":[{\"id\":\"s\",\"title\":\"k\",\"text\":\"k.text\"},{\"id\":1}]}",
                PowerGuideData.ROOT + "b.json", "{ not json");
        PowerGuideData guide = PowerGuideData.load(path -> files.containsKey(path)
                ? new ByteArrayInputStream(files.get(path).getBytes(StandardCharsets.UTF_8)) : null);
        assertEquals(1, guide.chapters().size(), "broken, missing and unsafe chapters drop out");
        assertEquals(1, guide.chapters().get(0).steps().size());
        assertFalse(guide.chapters().get(0).mentions("neropower:x"));
    }
}

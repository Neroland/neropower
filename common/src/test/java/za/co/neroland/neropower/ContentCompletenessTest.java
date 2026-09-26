package za.co.neroland.neropower;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Asserts that every block and item NeroPower registers has its English name in
 * {@code assets/neropower/lang/en_us.json} — for a block both {@code block.neropower.<id>} (the
 * placed block) and {@code item.neropower.<id>} (its plain {@code BlockItem}, which is what the
 * creative tab and tooltips read; NeroTech ships the same pair), with the same text — and that
 * every block has a blockstate, an item definition, a loot table and every block model its blockstate
 * names on the classpath, and that every block and item (bar machine by-products) is the result of
 * at least one recipe in {@link #RECIPES}. The id lists are deliberately hardcoded here
 * (not read from the registries) so that the test runs on the plain JVM in every Stonecutter cell
 * without bootstrapping Minecraft — when a block or item is added, add it to the matching list.
 *
 * <p>The full resource audit (models, textures, recipes, tags, GUI lang keys) lives in
 * {@code tools/check_content.py}; this test is the cheap per-cell subset of it.
 */
class ContentCompletenessTest {

    /** Every block id registered through {@code ModBlocks} (all have block items of the same id). */
    static final List<String> BLOCKS = List.of(
            "fission_core",
            "fission_casing",
            "control_rod_assembly",
            "battery_cell_basic",
            "battery_cell_advanced",
            "battery_cell_elite",
            "battery_bank_controller",
            "beam_transmitter",
            "beam_receiver",
            "beam_relay",
            "orbital_receiver",
            "radioisotope_generator",
            "stirling_generator");

    /** Every plain (non-block) item id registered through {@code ModItems}. */
    static final List<String> ITEMS = List.of(
            "control_rod",
            "uranium_pellet",
            "fuel_rod",
            "spent_fuel_rod",
            "reprocessed_pellet",
            "isotope_pellet",
            "spent_isotope_pellet");

    /** Items only ever produced by a machine, never crafted (as {@code ALLOW_NO_RECIPE} in {@code tools/check_content.py}). */
    static final Set<String> NO_RECIPE = Set.of("spent_fuel_rod", "spent_isotope_pellet");

    /**
     * Every recipe file under {@code data/neropower/recipe/}. Hardcoded so the check works from a jar
     * too; when the directory is a plain folder on the test classpath the test also checks this
     * list against it, so a new recipe cannot be forgotten here.
     */
    static final List<String> RECIPES = List.of(
            "battery_bank_controller",
            "battery_cell_advanced",
            "battery_cell_basic",
            "battery_cell_elite",
            "beam_receiver",
            "beam_relay",
            "beam_transmitter",
            "chemical_processing_fuel_rod",
            "chemical_processing_spent_fuel_rod",
            "control_rod",
            "control_rod_assembly",
            "fission_casing",
            "fission_core",
            "fuel_rod",
            "isotope_pellet",
            "orbital_receiver",
            "radioisotope_generator",
            "stirling_generator",
            "uranium_pellet",
            "uranium_pellet_from_ore",
            "uranium_pellet_from_reprocessed");

    private static final String RECIPE_DIR = "data/neropower/recipe/";

    /** {@code "result": {"id": "..."}} or {@code "result": "..."} — the first result id in a recipe. */
    private static final Pattern RESULT = Pattern.compile(
            "\"result\"\\s*:\\s*(?:\\{[^}]*?\"id\"\\s*:\\s*)?\"([^\"]+)\"");

    /** A block model reference in a blockstate (variants or multipart). */
    private static final Pattern BLOCK_MODEL = Pattern.compile("\"model\"\\s*:\\s*\"neropower:block/([^\"]+)\"");

    private static final Pattern KEY = Pattern.compile("\"([^\"\\\\]+)\"\\s*:");
    private static final Pattern ENTRY = Pattern.compile("\"([^\"\\\\]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void everyBlockAndItemHasAnEnglishName() throws IOException {
        Map<String, String> lang = langEntries();
        List<String> missing = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        for (String block : BLOCKS) {
            String blockKey = "block.neropower." + block;
            String itemKey = "item.neropower." + block;
            if (!lang.containsKey(blockKey)) {
                missing.add(blockKey);
            }
            if (!lang.containsKey(itemKey)) {
                missing.add(itemKey);
            } else if (lang.containsKey(blockKey) && !lang.get(blockKey).equals(lang.get(itemKey))) {
                mismatched.add(itemKey + "=" + lang.get(itemKey) + " vs " + blockKey + "=" + lang.get(blockKey));
            }
        }
        for (String item : ITEMS) {
            String key = "item.neropower." + item;
            if (!lang.containsKey(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "en_us.json is missing " + missing);
        assertTrue(mismatched.isEmpty(), "en_us.json block/item names disagree: " + mismatched);
    }

    @Test
    void everyBlockHasStateItemDefinitionAndLootTable() {
        List<String> missing = new ArrayList<>();
        for (String block : BLOCKS) {
            requireResource(missing, "assets/neropower/blockstates/" + block + ".json");
            requireResource(missing, "assets/neropower/items/" + block + ".json");
            requireResource(missing, "data/neropower/loot_table/blocks/" + block + ".json");
        }
        for (String item : ITEMS) {
            requireResource(missing, "assets/neropower/items/" + item + ".json");
            requireResource(missing, "assets/neropower/models/item/" + item + ".json");
        }
        assertTrue(missing.isEmpty(), "classpath is missing " + missing);
    }

    @Test
    void everyBlockHasItsBlockModels() throws IOException {
        List<String> missing = new ArrayList<>();
        for (String block : BLOCKS) {
            String state = "assets/neropower/blockstates/" + block + ".json";
            if (ContentCompletenessTest.class.getClassLoader().getResource(state) == null) {
                continue; // reported by everyBlockHasStateItemDefinitionAndLootTable
            }
            Matcher matcher = BLOCK_MODEL.matcher(resourceText(state));
            Set<String> models = new TreeSet<>();
            while (matcher.find()) {
                models.add(matcher.group(1));
            }
            if (models.isEmpty()) {
                missing.add(block + " (blockstate names no neropower:block/ model)");
            }
            for (String model : models) {
                requireResource(missing, "assets/neropower/models/block/" + model + ".json");
            }
        }
        assertTrue(missing.isEmpty(), "block models missing: " + missing);
    }

    @Test
    void everyBlockAndItemIsARecipeResult() throws IOException {
        Set<String> results = new HashSet<>();
        List<String> unreadable = new ArrayList<>();
        for (String recipe : RECIPES) {
            String path = RECIPE_DIR + recipe + ".json";
            if (ContentCompletenessTest.class.getClassLoader().getResource(path) == null) {
                unreadable.add(path);
                continue;
            }
            Matcher matcher = RESULT.matcher(resourceText(path));
            if (matcher.find()) {
                results.add(matcher.group(1));
            } else {
                unreadable.add(path + " (no result id)");
            }
        }
        assertTrue(unreadable.isEmpty(), "recipes missing or without a result: " + unreadable);
        List<String> uncrafted = new ArrayList<>();
        for (String id : BLOCKS) {
            if (!results.contains("neropower:" + id)) {
                uncrafted.add(id);
            }
        }
        for (String id : ITEMS) {
            if (!NO_RECIPE.contains(id) && !results.contains("neropower:" + id)) {
                uncrafted.add(id);
            }
        }
        assertTrue(uncrafted.isEmpty(), "no recipe produces " + uncrafted);
    }

    @Test
    void recipeListMatchesTheRecipeFolder() throws URISyntaxException {
        URL dir = ContentCompletenessTest.class.getClassLoader().getResource(RECIPE_DIR);
        if (dir == null || !"file".equals(dir.getProtocol())) {
            return; // inside a jar: the hardcoded list alone is checked above
        }
        File[] files = new File(dir.toURI()).listFiles((d, name) -> name.endsWith(".json"));
        assertNotNull(files, RECIPE_DIR + " is not listable");
        Set<String> onDisk = new TreeSet<>();
        for (File file : files) {
            onDisk.add(file.getName().substring(0, file.getName().length() - ".json".length()));
        }
        Set<String> listed = new TreeSet<>(RECIPES);
        Set<String> unlisted = new TreeSet<>(onDisk);
        unlisted.removeAll(listed);
        Set<String> absent = new TreeSet<>(listed);
        absent.removeAll(onDisk);
        assertTrue(unlisted.isEmpty(), "add to ContentCompletenessTest.RECIPES: " + unlisted);
        assertTrue(absent.isEmpty(), "listed but not on disk: " + absent);
    }

    @Test
    void creativeTabFailureStagesAndCommandsAreNamed() throws IOException {
        Set<String> keys = langKeys();
        List<String> missing = new ArrayList<>();
        for (String key : List.of(
                "itemGroup.neropower",
                "neropower.failure.stage.stable",
                "neropower.failure.stage.warning",
                "neropower.failure.stage.unstable",
                "neropower.failure.stage.failure",
                "command.neropower.gallery.failed",
                "command.neropower.gallery.player_only",
                "command.neropower.gallery.creative_only",
                "command.neropower.gallery.built",
                "command.neropower.gallery.cleared")) {
            if (!keys.contains(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "en_us.json is missing " + missing);
    }

    private static void requireResource(List<String> missing, String path) {
        if (ContentCompletenessTest.class.getClassLoader().getResource(path) == null) {
            missing.add(path);
        }
    }

    /**
     * Reads the top-level keys of the flat lang file with a regex rather than a JSON library, so
     * the test has no dependency beyond the JDK: the file is a single object of string values and
     * every {@code "key":} pair is a key.
     */
    private static Set<String> langKeys() throws IOException {
        String text = langText();
        Set<String> keys = new HashSet<>();
        Matcher matcher = KEY.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** Key → raw value text (escapes left as written; the block/item pairs compare equal either way). */
    private static Map<String, String> langEntries() throws IOException {
        String text = langText();
        Map<String, String> entries = new HashMap<>();
        Matcher matcher = ENTRY.matcher(text);
        while (matcher.find()) {
            entries.put(matcher.group(1), matcher.group(2));
        }
        return entries;
    }

    private static String langText() throws IOException {
        return resourceText("assets/neropower/lang/en_us.json");
    }

    private static String resourceText(String path) throws IOException {
        try (InputStream in = ContentCompletenessTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package za.co.neroland.neropower.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The waste back end's rules, read from the shipped data (no JSON library, as the other data tests):
 * <ul>
 *   <li>hot rods must cool before reprocessing — no recipe takes a hot rod or the
 *       {@code #neropower:spent_fuel} tag that holds it; reprocessing takes only the cooled rod;</li>
 *   <li>vitrified waste is inert — no recipe takes it, and it is not vitrifiable again;</li>
 *   <li>racks and casks cannot duplicate rods on break — their loot tables drop the block alone,
 *       with no function that copies contents (NeroTech's container drop spills the slots once);</li>
 *   <li>nothing is voided for free — vitrifying costs energy, and residue centrifuging consumes more
 *       residue than it returns.</li>
 * </ul>
 */
class BackEndRulesTest {

    private static final String RECIPE_DIR = "data/neropower/recipe/";

    /** Every recipe this package adds, plus the existing reprocessing pair (used when the folder is in a jar). */
    private static final List<String> KNOWN_RECIPES = List.of(
            "spent_fuel_pool", "vitrifier", "dry_cask",
            "centrifuging_spent_fuel_rod", "centrifuging_beta_capsule",
            "chemical_processing_spent_fuel_rod", "chemical_processing_fuel_rod");

    private static final Pattern RESULT_BLOCK = Pattern.compile(
            "\"(?:result|results)\"\\s*:\\s*(\\{[^}]*\\}|\\[[^\\]]*\\])", Pattern.DOTALL);
    private static final Pattern INGREDIENT = Pattern.compile("\"ingredient\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern COUNT = Pattern.compile("\"count\"\\s*:\\s*(\\d+)");

    @Test
    void noRecipeTakesAHotRod() throws Exception {
        for (var recipe : recipes().entrySet()) {
            String inputs = inputsOf(recipe.getValue());
            assertFalse(inputs.contains("neropower:hot_spent_fuel_rod"),
                    recipe.getKey() + " takes a hot rod; it must cool in a Spent Fuel Rack first");
            assertFalse(inputs.contains("#neropower:spent_fuel"),
                    recipe.getKey() + " takes #neropower:spent_fuel, which holds hot rods");
        }
    }

    @Test
    void reprocessingTakesOnlyTheCooledRod() throws IOException {
        for (String id : List.of("chemical_processing_spent_fuel_rod", "centrifuging_spent_fuel_rod")) {
            Matcher m = INGREDIENT.matcher(read(RECIPE_DIR + id + ".json"));
            assertTrue(m.find(), id);
            assertEquals("neropower:spent_fuel_rod", m.group(1), id);
        }
    }

    @Test
    void hotRodsAreNotStoredOrVitrified() throws IOException {
        assertFalse(read("data/neropower/tags/item/cask_storable.json").contains("hot_spent_fuel_rod"));
        assertFalse(read("data/neropower/tags/item/vitrifiable.json").contains("hot_spent_fuel_rod"));
    }

    @Test
    void vitrifiedWasteIsInert() throws Exception {
        for (var recipe : recipes().entrySet()) {
            String text = recipe.getValue();
            assertFalse(text.contains("neropower:vitrified_waste"),
                    recipe.getKey() + " mentions vitrified waste; nothing may use or make it but the Vitrifier");
        }
        assertFalse(read("data/neropower/tags/item/vitrifiable.json").contains("vitrified_waste"),
                "vitrified waste must not be vitrifiable again");
        assertTrue(read("data/neropower/tags/item/cask_storable.json").contains("neropower:vitrified_waste"));
    }

    @Test
    void vitrifyingIsNeverFree() {
        assertEquals(8_000L, CaskMath.vitrifyCost(40, 200), "decision default: 40 NE/t for 200 ticks");
        assertTrue(CaskMath.vitrifyCost(1, 1) > 0L, "the config floors keep a positive cost");
        assertFalse(CaskMath.canVitrify(1, 1, 0, 64), "two sand per operation");
        assertFalse(CaskMath.canVitrify(0, 2, 0, 64), "no waste, no work");
        assertFalse(CaskMath.canVitrify(1, 2, 64, 64), "a full output stalls rather than voids");
        assertTrue(CaskMath.canVitrify(1, 2, 63, 64));
    }

    @Test
    void residueCentrifugingConsumesResidue() throws IOException {
        String json = read(RECIPE_DIR + "centrifuging_beta_capsule.json");
        int in = firstCount(inputsOf(json));
        int back = 0;
        Matcher results = RESULT_BLOCK.matcher(json);
        assertTrue(results.find());
        String block = results.group(1);
        Matcher entry = Pattern.compile("\\{[^}]*\"item\"\\s*:\\s*\"neropower:fission_residue\"[^}]*\\}").matcher(block);
        while (entry.find()) {
            back += firstCount(entry.group());
        }
        assertTrue(back < in, "centrifuging must use up residue (" + in + " in, " + back + " back)");
    }

    @Test
    void racksAndCasksDropOnlyThemselves() throws IOException {
        for (String block : List.of("spent_fuel_pool", "dry_cask", "vitrifier")) {
            String loot = read("data/neropower/loot_table/blocks/" + block + ".json");
            String lower = loot.toLowerCase(Locale.ROOT);
            assertTrue(loot.contains("\"neropower:" + block + "\""), block);
            assertEquals(1, occurrences(loot, "\"minecraft:item\""), block + " drops exactly one entry");
            for (String copy : List.of("copy_components", "copy_nbt", "set_contents", "minecraft:contents",
                    "minecraft:dynamic", "copy_custom_data", "container")) {
                assertFalse(lower.contains(copy), block + " loot copies contents (" + copy + ")");
            }
        }
    }

    @Test
    void traceHeatCountsOnlyRadioactiveSlots() {
        assertEquals(0, CaskMath.traceHeat(8, 9));
        assertEquals(1, CaskMath.traceHeat(9, 9));
        assertEquals(3, CaskMath.traceHeat(CaskMath.CASK_SLOTS, 9));
        assertFalse(CaskMath.emitting(0), "a cask of vitrified waste is inert");
        assertTrue(CaskMath.emitting(1));
    }

    // --- helpers ------------------------------------------------------------------------------------

    /** Everything a recipe consumes: the JSON with its result / results block cut out. */
    private static String inputsOf(String json) {
        return RESULT_BLOCK.matcher(json).replaceAll("");
    }

    private static int firstCount(String text) {
        Matcher m = COUNT.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    private static int occurrences(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    /** Every recipe JSON on the classpath folder (or the known list when the folder is inside a jar). */
    private static TreeMap<String, String> recipes() throws IOException, URISyntaxException {
        TreeMap<String, String> out = new TreeMap<>();
        URL dir = BackEndRulesTest.class.getClassLoader().getResource(RECIPE_DIR);
        List<String> names = new ArrayList<>();
        if (dir != null && "file".equals(dir.getProtocol())) {
            File[] files = new File(dir.toURI()).listFiles((d, name) -> name.endsWith(".json"));
            assertNotNull(files);
            for (File f : files) {
                names.add(f.getName().substring(0, f.getName().length() - ".json".length()));
            }
        } else {
            names.addAll(KNOWN_RECIPES);
        }
        for (String name : names) {
            out.put(name, read(RECIPE_DIR + name + ".json"));
        }
        assertTrue(out.keySet().containsAll(KNOWN_RECIPES), "recipes missing: " + KNOWN_RECIPES);
        return out;
    }

    private static String read(String path) throws IOException {
        try (InputStream in = BackEndRulesTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

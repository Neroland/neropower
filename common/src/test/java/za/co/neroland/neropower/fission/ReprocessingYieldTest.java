package za.co.neroland.neropower.fission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Locks the fuel-cycle economics from the shipped recipe JSONs (read from the test classpath the
 * same way {@code ContentCompletenessTest} reads the lang file, no JSON library): pulling a Fuel
 * Rod early and reprocessing it yields more Reprocessed Pellets than a spent rod, but still less
 * uranium than a fresh rod cost, so reprocessing can never be a uranium loop. Pure JVM.
 */
class ReprocessingYieldTest {

    private static final String RECIPES = "data/neropower/recipe/";

    private static final Pattern TYPE = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern INGREDIENT = Pattern.compile("\"ingredient\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RESULT_ID = Pattern.compile(
            "\"result\"\\s*:\\s*\\{[^}]*?\"id\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern RESULT_COUNT = Pattern.compile(
            "\"result\"\\s*:\\s*\\{[^}]*?\"count\"\\s*:\\s*(\\d+)");
    private static final Pattern PATTERN_ROWS = Pattern.compile("\"pattern\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern ROW = Pattern.compile("\"([^\"]*)\"");

    @Test
    void reprocessingRecipesAreChemicalProcessingIntoReprocessedPellets() throws IOException {
        for (String[] recipe : new String[][] {
                {"chemical_processing_fuel_rod", "neropower:fuel_rod"},
                {"chemical_processing_spent_fuel_rod", "neropower:spent_fuel_rod"}}) {
            String json = read(recipe[0]);
            assertEquals("nerotech:chemical_processing", first(TYPE, json), recipe[0]);
            assertEquals(recipe[1], first(INGREDIENT, json), recipe[0]);
            assertEquals("neropower:reprocessed_pellet", first(RESULT_ID, json), recipe[0]);
        }
    }

    @Test
    void anEarlyPulledRodYieldsMoreThanASpentOne() throws IOException {
        int early = resultCount("chemical_processing_fuel_rod");
        int spent = resultCount("chemical_processing_spent_fuel_rod");
        assertEquals(2, early);
        assertEquals(1, spent);
        assertTrue(early > spent, "a partially-burnt rod must be worth reprocessing early");
    }

    @Test
    void reprocessingAFreshRodNeverReturnsItsUranium() throws IOException {
        int uraniumPerRod = uraniumPelletsPerFuelRod();
        int reprocessedPerUranium = reprocessedPelletsPerUraniumPellet();
        assertEquals(3, uraniumPerRod, "a Fuel Rod costs three Uranium Pellets");
        assertEquals(2, reprocessedPerUranium, "reprocessed → uranium is 2:1");
        int early = resultCount("chemical_processing_fuel_rod");
        // In uranium terms: early / reprocessedPerUranium uranium back, uraniumPerRod in.
        assertTrue(early < uraniumPerRod * reprocessedPerUranium,
                early + " reprocessed pellets must be worth less than the " + uraniumPerRod
                        + " uranium pellets the rod cost");
    }

    /** The result count of a recipe (1 when the result has no {@code count}). */
    private static int resultCount(String recipe) throws IOException {
        String count = firstOrNull(RESULT_COUNT, read(recipe));
        return count == null ? 1 : Integer.parseInt(count);
    }

    /** Occurrences of the key mapped to {@code neropower:uranium_pellet} across {@code fuel_rod}'s pattern. */
    private static int uraniumPelletsPerFuelRod() throws IOException {
        String json = read("fuel_rod");
        Matcher key = Pattern.compile("\"(.)\"\\s*:\\s*\"neropower:uranium_pellet\"").matcher(json);
        assertTrue(key.find(), "fuel_rod.json has no uranium_pellet key");
        char symbol = key.group(1).charAt(0);
        String rows = first(PATTERN_ROWS, json);
        int count = 0;
        Matcher row = ROW.matcher(rows);
        while (row.find()) {
            for (char c : row.group(1).toCharArray()) {
                if (c == symbol) {
                    count++;
                }
            }
        }
        assertEquals(1, resultCount("fuel_rod"), "one rod per craft");
        return count;
    }

    /** Reprocessed pellets in the shapeless {@code uranium_pellet_from_reprocessed} per pellet out. */
    private static int reprocessedPelletsPerUraniumPellet() throws IOException {
        String json = read("uranium_pellet_from_reprocessed");
        Matcher m = Pattern.compile("\"neropower:reprocessed_pellet\"").matcher(json);
        int inputs = 0;
        while (m.find()) {
            inputs++;
        }
        return inputs / resultCount("uranium_pellet_from_reprocessed");
    }

    private static String first(Pattern pattern, String text) {
        String found = firstOrNull(pattern, text);
        assertNotNull(found, "pattern " + pattern + " not found");
        return found;
    }

    private static String firstOrNull(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String read(String recipe) throws IOException {
        String path = RECIPES + recipe + ".json";
        try (InputStream in = ReprocessingYieldTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

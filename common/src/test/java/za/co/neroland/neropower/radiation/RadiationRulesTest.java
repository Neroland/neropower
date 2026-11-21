package za.co.neroland.neropower.radiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Radiation's rules (decision 007): shielding stops emission, decay is deterministic, nothing is kept
 * per player, the dosimeter stores no identity, and with radiation off nothing happens.
 */
class RadiationRulesTest {

    private static final String PKG = "za/co/neroland/neropower/radiation";

    // --- shielding blocks emission ---------------------------------------------------------

    @Test
    void anEnclosedSourceEmitsNothing() {
        assertEquals(0, RadiationMath.emission(true, 0, 1), "all six faces shielded");
        assertEquals(0, RadiationMath.emission(true, 0, 100));
        assertEquals(6, RadiationMath.emission(true, 6, 1), "an open cask: one per face");
        assertEquals(3, RadiationMath.emission(true, 3, 1), "every covered face removes its share");
        assertEquals(0, RadiationMath.emission(false, 6, 1), "an empty cask or calm rack is no source");
        assertEquals(6, RadiationMath.emission(true, 9, 1), "faces are clamped to six");
    }

    @Test
    void bothShieldingBlocksAreBarriers() throws IOException {
        String tag = read("data/neropower/tags/block/radiation_barrier.json");
        assertTrue(tag.contains("\"neropower:shielding_block\""));
        assertTrue(tag.contains("\"neropower:shielding_glass\""));
    }

    @Test
    void shieldingArmourRemovesAQuarterPerPiece() {
        assertEquals(800, RadiationMath.effectiveLevel(800, 1));
        assertEquals(400, RadiationMath.effectiveLevel(800, 2));
        assertEquals(0, RadiationMath.effectiveLevel(1000, 4), "four pieces = immune");
        assertEquals(3, RadiationMath.effectTier(true, 1000, 0));
        assertEquals(2, RadiationMath.effectTier(true, 1000, 1), "750 felt: weakness and hunger");
        assertEquals(0, RadiationMath.effectTier(true, 1000, 4));
    }

    @Test
    void nerospaceSuitEntriesAreOptional() throws IOException {
        for (String tag : List.of("radiation_shielding", "radiation_shielding_lined")) {
            String json = read("data/neropower/tags/item/" + tag + ".json");
            int ids = count(json, "\"nerospace:");
            assertEquals(12, ids, tag + ": the 12 T2/heat/cold suit pieces");
            assertEquals(ids, count(json, "\"required\": false"), tag + ": every Nerospace entry is optional");
        }
    }

    // --- decay determinism -----------------------------------------------------------------

    @Test
    void decayIsDeterministic() {
        ContaminationMap a = seeded();
        ContaminationMap b = seeded();
        for (long t = 0; t < ContaminationMap.DECAY_PERIOD_TICKS * 30L; t++) {
            a.decayTick(t, 5, 16);
            b.decayTick(t, 5, 16);
        }
        assertEquals(a.snapshot(), b.snapshot(), "same history, same levels");
        assertTrue(a.level(RadiationMath.chunkKey(0, 0)) < 1000, "it decayed");
    }

    @Test
    void effectPassIsRoundRobin() {
        ContaminationMap map = seeded();
        List<Long> first = map.nextAtLeast(null, RadiationMath.WEAKNESS_AT, 2);
        List<Long> second = map.nextAtLeast(first.get(first.size() - 1), RadiationMath.WEAKNESS_AT, 2);
        assertEquals(2, first.size());
        assertFalse(first.contains(second.get(0)), "the cursor moves on");
        for (long key : map.nextAtLeast(null, RadiationMath.WEAKNESS_AT, 64)) {
            assertTrue(map.level(key) >= RadiationMath.WEAKNESS_AT, "only chunks that can have an effect");
        }
    }

    @Test
    void chunkKeysMatchVanillaPacking() {
        long key = RadiationMath.chunkKey(-3, 7);
        assertEquals(-3, RadiationMath.chunkX(key));
        assertEquals(7, RadiationMath.chunkZ(key));
        assertEquals((-3L & 0xFFFFFFFFL) | (7L << 32), key, "ChunkPos.asLong packing");
        assertEquals(RadiationMath.chunkKey(-1, -1), RadiationMath.chunkKeyOfBlock(-1, -16));
    }

    // --- no per-player state ---------------------------------------------------------------

    @Test
    void radiationKeepsNothingPerPlayer() throws IOException {
        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String name = file.getFileName().toString();
            for (String banned : List.of("UUID", "getUUID", "getGameProfile", "getStringUUID", "getScoreboardName")) {
                assertFalse(text.contains(banned), name + " touches player identity (" + banned + ")");
            }
            assertFalse(text.matches("(?s).*Map<[^>]*Player.*"), name + " keys a map by player");
            assertFalse(text.contains("LOGGER"), name + " logs");
        }
    }

    @Test
    void dosimeterStoresOnlyANumber() throws IOException {
        Path content = locate("za/co/neroland/neropower/registry/content/RadiationContent.java");
        Assumptions.assumeTrue(content != null, "source tree not reachable from the test's working directory");
        String text = Files.readString(content, StandardCharsets.UTF_8);
        assertTrue(text.contains("DataComponentType<Integer>> DOSIMETER_READING"), "the reading is one Integer");
        assertEquals(0, RadiationMath.dosimeterAdd(0, 9), "level / 10, floored");
        assertEquals(34, RadiationMath.dosimeterAdd(0, 340));
        assertEquals(RadiationMath.DOSIMETER_CAP, RadiationMath.dosimeterAdd(RadiationMath.DOSIMETER_CAP, 1000));
        assertEquals(4, RadiationMath.dosimeterStep(RadiationMath.DOSIMETER_CAP));
        assertEquals(0, RadiationMath.dosimeterStep(999));
    }

    @Test
    void dosimeterResetsByCraftingAlone() throws IOException {
        String json = read("data/neropower/recipe/dosimeter_badge_reset.json");
        assertTrue(json.contains("crafting_shapeless"));
        assertEquals(2, count(json, "neropower:dosimeter_badge"), "one badge in, one fresh badge out");
        assertFalse(json.toLowerCase(Locale.ROOT).contains("components"), "the result carries no reading");
    }

    // --- config off => no effects ----------------------------------------------------------

    @Test
    void radiationOffMeansNoEffects() {
        for (int level = 0; level <= ContaminationMap.MAX_LEVEL; level += 50) {
            for (int pieces = 0; pieces <= 4; pieces++) {
                assertEquals(0, RadiationMath.effectTier(false, level, pieces), "off at " + level);
            }
        }
        assertEquals(1, RadiationMath.effectTier(true, 200, 0));
        assertEquals(0, RadiationMath.effectTier(true, 199, 0));
    }

    @Test
    void bandsAndClicksFollowTheHudSpec() {
        assertEquals(0, RadiationMath.band(9));
        assertEquals(1, RadiationMath.band(10));
        assertEquals(2, RadiationMath.band(200));
        assertEquals(3, RadiationMath.band(500));
        assertEquals(4, RadiationMath.band(800));
        assertEquals(0.2D, RadiationMath.clickRate(0), 1e-9);
        assertEquals(2.0D, RadiationMath.clickRate(500), 1e-9);
        assertEquals(20.0D, RadiationMath.clickRate(1000), 1e-9);
        assertEquals(200, RadiationMath.neighbourBurst(600), "600 at the epicentre, 200 to each neighbour");
    }

    // --- helpers ---------------------------------------------------------------------------

    private static ContaminationMap seeded() {
        ContaminationMap map = new ContaminationMap();
        map.add(RadiationMath.chunkKey(0, 0), 1000);
        map.add(RadiationMath.chunkKey(1, 0), 600);
        map.add(RadiationMath.chunkKey(-1, 2), 250);
        map.add(RadiationMath.chunkKey(5, 5), 40);
        return map;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = RadiationRulesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The radiation package's main sources (skips when the tree is not reachable, e.g. from a jar). */
    private static List<Path> sources() throws IOException {
        Path dir = locate(PKG);
        Assumptions.assumeTrue(dir != null, "source tree not reachable from the test's working directory");
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(out::add);
        }
        assertFalse(out.isEmpty());
        return out;
    }

    private static Path locate(String rel) {
        Path at = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && at != null; i++, at = at.getParent()) {
            Path candidate = at.resolve("common/src/main/java").resolve(rel);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}

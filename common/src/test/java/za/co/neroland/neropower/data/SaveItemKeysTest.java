package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.fission.FissionCoreSave;
import za.co.neroland.neropower.fission.FissionMath;

/**
 * A 0.3.2 reactor saved in the real item-key layout ({@code Item0}, {@code Item1}, ... as NeroTech's
 * machine base writes them) keeps its rods: every saved index is a rod slot that still exists at the same
 * index, the rods keep their burn-up, the spent rod stays spent, and the slots appended in 0.4.0 (blanket in
 * and out) are absent and so start empty. Pure JVM.
 */
class SaveItemKeysTest {

    private static final String FIXTURE = "common/src/test/resources/fixtures/neropower_0_3_2_item_keys.snbt";

    private static Path fixturePath() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(FIXTURE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("fixture not found above " + Paths.get("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> core() throws IOException {
        Map<String, Object> all = (Map<String, Object>) MiniSnbt.parse(
                Files.readString(fixturePath(), StandardCharsets.UTF_8));
        Map<String, Object> core = (Map<String, Object>) all.get("fission_core");
        assertNotNull(core);
        return core;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Map<String, Object>> items(Map<String, Object> core) {
        Map<Integer, Map<String, Object>> items = new TreeMap<>();
        for (Map.Entry<String, Object> e : core.entrySet()) {
            if (e.getKey().matches("Item\\d+")) {
                items.put(Integer.parseInt(e.getKey().substring(4)), (Map<String, Object>) e.getValue());
            }
        }
        return items;
    }

    @Test
    void everySavedIndexIsStillARodSlot() throws IOException {
        Map<Integer, Map<String, Object>> items = items(core());
        assertEquals(FissionMath.ROD_SLOTS, items.size(), "0.3.2 saved one entry per rod slot");
        for (int index : items.keySet()) {
            assertTrue(index >= 0 && index < FissionMath.ROD_SLOTS, "Item" + index + " is a rod slot");
        }
        assertTrue(!items.containsKey(FissionMath.ROD_SLOTS) && !items.containsKey(FissionMath.ROD_SLOTS + 1),
                "the blanket slots appended in 0.4.0 are absent, so they load empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rodsKeepTheirBurnupAndTheSpentRodStaysSpent() throws IOException {
        Map<String, Object> core = core();
        Map<Integer, Map<String, Object>> items = items(core);
        assertEquals("neropower:fuel_rod", items.get(0).get("id"));
        assertEquals(420, ((Map<String, Object>) items.get(0).get("components")).get("neropower:burnup"));
        assertEquals("neropower:fuel_rod", items.get(1).get("id"));
        assertEquals(415, ((Map<String, Object>) items.get(1).get("components")).get("neropower:burnup"));
        assertTrue(items.get(2).isEmpty(), "an empty slot");
        assertEquals("neropower:spent_fuel_rod", items.get(3).get("id"));
        long rods = items.values().stream().filter(m -> "neropower:fuel_rod".equals(m.get("id"))).count();
        FissionCoreSave save = FissionCoreSave.read(SaveKeys.of(core));
        assertEquals(rods, save.rodCount(), "the saved rod count matches the rods in the slots");
        assertEquals(731_552L, save.burnupAccumulator());
    }
}

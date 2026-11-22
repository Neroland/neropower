package za.co.neroland.neropower.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.generation.BetavoltaicCore;
import za.co.neroland.neropower.storage.SmesCore;

/**
 * The Stage 3 adversarial pass, where it can be a test: duplication through breaking and rebuilding a
 * multiblock (vane sets and stator windings come back exactly once, the Storage Ring never returns more than
 * it held), loot tables that never copy a container into the dropped block (Spent Fuel Rack, Dry Cask and
 * every other machine drop their slots once, through the block entity), power cells, spent fuel and the
 * cask, and the Beta Capsule clock.
 */
class AdversarialTest {

    static final String LOOT_DIR = "data/neropower/loot_table/blocks/";

    @BeforeAll
    static void load() throws IOException, URISyntaxException {
        RecipeGraphTest.load();
    }

    static JsonObject loot(String block) throws IOException {
        return RecipeGraphTest.readJson(LOOT_DIR + block + ".json").getAsJsonObject();
    }

    /** Every state property a pool's conditions require, in either loot format (pre-26.3 or 26.3). */
    static Map<String, String> requiredState(JsonObject pool) {
        Map<String, String> state = new HashMap<>();
        List<JsonElement> conditions = new ArrayList<>();
        if (pool.has("conditions")) {
            pool.getAsJsonArray("conditions").forEach(conditions::add);
        }
        if (pool.has("condition")) {
            conditions.add(pool.get("condition"));
        }
        for (int i = 0; i < conditions.size(); i++) {
            JsonObject c = conditions.get(i).getAsJsonObject();
            if (c.has("terms")) {
                c.getAsJsonArray("terms").forEach(conditions::add);
            }
            for (String key : List.of("properties", "state")) {
                if (c.has(key) && c.get(key).isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : c.getAsJsonObject(key).entrySet()) {
                        state.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        }
        return state;
    }

    static List<String> drops(JsonObject table, String property, String value) {
        List<String> out = new ArrayList<>();
        for (JsonElement p : table.getAsJsonArray("pools")) {
            JsonObject pool = p.getAsJsonObject();
            Map<String, String> need = requiredState(pool);
            String wanted = need.get(property);
            if (wanted != null && !wanted.equals(value)) {
                continue;
            }
            assertTrue(need.keySet().stream().allMatch(property::equals), "unexpected state condition " + need);
            for (JsonElement e : pool.getAsJsonArray("entries")) {
                out.add(e.getAsJsonObject().get("name").getAsString());
            }
        }
        return out;
    }

    @Test
    void aVanedSpindleDropsItselfAndItsOneVaneSet() throws IOException {
        JsonObject table = loot("turbine_spindle");
        Map<String, String> vane = Map.of("none", "", "steel", "neropower:steel_vane_set",
                "cindersteel", "neropower:cindersteel_vane_set", "superconducting", "neropower:superconducting_vane_set");
        for (Map.Entry<String, String> e : vane.entrySet()) {
            List<String> drops = drops(table, "vanes", e.getKey());
            List<String> expected = new ArrayList<>(List.of("neropower:turbine_spindle"));
            if (!e.getValue().isEmpty()) {
                expected.add(e.getValue());
            }
            assertEquals(expected.size(), drops.size(), "vanes=" + e.getKey() + " drops " + drops);
            assertTrue(drops.containsAll(expected), "vanes=" + e.getKey() + " drops " + drops);
        }
    }

    @Test
    void aWoundStatorDropsItselfAndOneCoil() throws IOException {
        JsonObject table = loot("turbine_stator");
        Map<String, String> coil = Map.of("none", "", "tier1", "nerotech:nero_coil",
                "tier2", "nerotech:cindersteel_coil", "tier3", "nerotech:superconductor_coil");
        for (Map.Entry<String, String> e : coil.entrySet()) {
            List<String> drops = drops(table, "winding", e.getKey());
            int expected = e.getValue().isEmpty() ? 1 : 2;
            assertEquals(expected, drops.size(), "winding=" + e.getKey() + " drops " + drops);
            assertTrue(drops.contains("neropower:turbine_stator"));
            if (!e.getValue().isEmpty()) {
                assertTrue(drops.contains(e.getValue()), "winding=" + e.getKey() + " drops " + drops);
            }
        }
    }

    @Test
    void noLootTableCopiesAContainerIntoTheDroppedBlock() throws IOException, URISyntaxException {
        // Machines spill their slots once through the block entity; a loot function that also copied the
        // container (or components) into the dropped item would hand the contents out twice.
        List<String> copying = new ArrayList<>();
        Set<String> tables = RecipeGraphTest.listJson(LOOT_DIR);
        assertTrue(tables.contains("dry_cask") && tables.contains("spent_fuel_pool"), "loot folder not found");
        for (String name : tables) {
            String text = RecipeGraphTest.readJson(LOOT_DIR + name + ".json").toString();
            for (String fn : List.of("copy_components", "set_contents", "copy_nbt", "copy_custom_data",
                    "set_components")) {
                if (text.contains("minecraft:" + fn)) {
                    copying.add(name + " uses " + fn);
                }
            }
        }
        assertTrue(copying.isEmpty(), "loot that may duplicate contents: " + copying);
    }

    @Test
    void hotRodsMustCoolInTheRackBeforeAnythingTakesThem() {
        Set<String> cask = RecipeGraphTest.resolveItemTag("neropower:cask_storable", RecipeGraphTest.Mode.STANDALONE);
        assertFalse(cask.contains("item:neropower:hot_spent_fuel_rod"), "a hot rod may not skip the rack into a cask");
        Set<String> spent = RecipeGraphTest.resolveItemTag("neropower:spent_fuel", RecipeGraphTest.Mode.STANDALONE);
        assertTrue(spent.contains("item:neropower:hot_spent_fuel_rod"), "hot rods are spent fuel (radiation, rack)");
        for (Map.Entry<String, JsonObject> e : RecipeGraphTest.recipes.entrySet()) {
            assertFalse(e.getValue().toString().contains("neropower:hot_spent_fuel_rod"),
                    e.getKey() + " takes or makes a hot rod: they come from a core and leave a rack only");
        }
    }

    @Test
    void reprocessingNeverReturnsMorePelletsThanARodTook() {
        // A fuel rod takes 3 uranium pellets; the best reprocessing gives 2 reprocessed pellets, and 2 of those
        // make 1 uranium pellet. Any route back is a loss, never a pellet mill.
        Map<String, JsonObject> r = RecipeGraphTest.recipes;
        int pelletsPerRod = 0;
        for (JsonElement row : r.get("fuel_rod").getAsJsonArray("pattern")) {
            pelletsPerRod += row.getAsString().chars().filter(c -> c == 'U').count();
        }
        int reprocessedPerPellet = r.get("uranium_pellet_from_reprocessed").getAsJsonArray("ingredients").size();
        int best = 0;
        for (String name : List.of("chemical_processing_spent_fuel_rod", "chemical_processing_fuel_rod",
                "centrifuging_spent_fuel_rod")) {
            JsonObject json = r.get(name);
            int made = 0;
            if (json.has("result")) {
                JsonObject res = json.getAsJsonObject("result");
                made = res.has("count") ? res.get("count").getAsInt() : 1;
            } else {
                for (JsonElement e : json.getAsJsonArray("results")) {
                    JsonObject o = e.getAsJsonObject();
                    if (o.get("item").getAsString().equals("neropower:reprocessed_pellet")) {
                        made += o.has("count") ? o.get("count").getAsInt() : 1;
                    }
                }
            }
            best = Math.max(best, made);
        }
        assertEquals(3, pelletsPerRod);
        assertTrue(best / (double) reprocessedPerPellet < pelletsPerRod, "reprocessing returns more pellets than it took");
    }

    @Test
    void upgradingAPowerCellCarriesNoCharge() {
        // The reacting recipes consume the lower cell and make a new, empty one: charge is lost, never doubled.
        for (String name : List.of("reacting_power_cell_advanced", "reacting_power_cell_elite")) {
            JsonObject json = RecipeGraphTest.recipes.get(name);
            for (JsonElement e : json.getAsJsonArray("results")) {
                assertFalse(e.getAsJsonObject().has("components"), name + " result carries components");
            }
            assertEquals(1, json.getAsJsonArray("results").size());
        }
    }

    @Test
    void aBetaCapsuleGivesItsLifeOnceAndAFreshSlotRestartsOnlyAFreshCapsule() {
        int life = 1_000;
        int rate = 4;
        BetavoltaicCore core = new BetavoltaicCore();
        long total = 0L;
        boolean spent = false;
        for (int t = 0; t < life * 3 && !spent; t++) {
            BetavoltaicCore.Step step = core.tick(BetavoltaicCore.STATE_LIVE, Long.MAX_VALUE, rate, life);
            total += step.generate();
            spent = step.spendCapsule();
        }
        assertTrue(spent);
        assertEquals((long) life * rate, total, "one capsule, one life");
        // A full buffer pauses the clock; it does not reset it.
        BetavoltaicCore paused = new BetavoltaicCore();
        for (int t = 0; t < 100; t++) {
            paused.tick(BetavoltaicCore.STATE_LIVE, Long.MAX_VALUE, rate, life);
        }
        paused.tick(BetavoltaicCore.STATE_LIVE, 0L, rate, life);
        assertEquals(100L, paused.used(), "a stall must not give the capsule its clock back");
        // The saved clock survives a reload.
        BetavoltaicCore reloaded = new BetavoltaicCore();
        reloaded.load(paused.used());
        assertEquals(life - 100L, reloaded.left(life));
    }

    /**
     * A part-burnt rod cannot be pulled from a core and reprocessed as if fresh. Any burn-up
     * makes a leaving rod a Hot Spent Fuel Rod (hand, automation and breaking all go through the same rule),
     * fresh rods leave unchanged, and the only recipe that reprocesses an unspent rod takes the fresh item.
     */
    @Test
    void aPartBurntRodLeavesTheCoreSpent() throws IOException {
        assertFalse(za.co.neroland.neropower.fission.FissionMath.leavesSpent(true, 0), "a fresh rod stays fresh");
        for (int burnup : new int[] {1, 2, 500, 998, 999, za.co.neroland.neropower.fission.FissionMath.MAX_BURNUP}) {
            assertTrue(za.co.neroland.neropower.fission.FissionMath.leavesSpent(true, burnup),
                    "a rod at " + burnup + " permille must leave spent");
        }
        assertFalse(za.co.neroland.neropower.fission.FissionMath.leavesSpent(false, 700),
                "only rods are converted; pellets and spent rods leave as they are");
        JsonObject recipe = RecipeGraphTest.readJson("data/neropower/recipe/chemical_processing_fuel_rod.json")
                .getAsJsonObject();
        String text = recipe.toString();
        assertTrue(text.contains("neropower:fuel_rod"), "the reprocessing recipe takes the Fuel Rod item");
        assertFalse(text.contains("hot_spent_fuel_rod"), "a hot rod is never reprocessed without the rack");
    }

    @Test
    void aStorageRingRebuiltSmallerThenBiggerNeverRefills() {
        SmesCore core = new SmesCore();
        core.applyStructure(true, 56, 50_000_000L);
        core.beginTick(Long.MAX_VALUE);
        core.insert(2_800_000_000L, false);
        long most = core.energy();
        for (int coils = 56; coils >= 1; coils -= 5) {
            core.applyStructure(false, 0, 50_000_000L);
            core.applyStructure(true, coils, 50_000_000L);
            assertTrue(core.energy() <= most);
            most = core.energy();
        }
        core.applyStructure(true, 56, 50_000_000L);
        assertEquals(most, core.energy(), "growing the ring back restores nothing");
    }
}

package za.co.neroland.neropower.balance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.backend.BackEndConfig;

/**
 * The fuel-cycle recipe graph (Stage 3): every NeroPower recipe JSON on the test classpath, NeroPower's
 * own item tags, and the tags and lang files of the Neroland Core and NeroTech artifacts this cell compiles
 * against (they are on the test classpath), plus the machine steps that have no recipe JSON (a reactor
 * burning a rod, the Spent Fuel Rack cooling one, the breeder, the Vitrifier, the Betavoltaic Cell and the
 * Radioisotope Generator using up their fuel), solved for the cheapest way to make every NeroPower item in
 * two modes.
 *
 * <ul>
 *   <li><b>standalone</b>, a Core + NeroTech server: every {@code nerospace:} tag entry is dropped.</li>
 *   <li><b>offworld</b>, Nerospace installed: its ids are mined leaves.</li>
 * </ul>
 *
 * <p>Cost model, aligned with NeroTech's own recipe-graph test: a vanilla leaf is 100 (a nugget a ninth, a
 * storage block nine); a Nerospace item 100; a NeroTech or Core item 2,000 in <i>both</i> modes (its own
 * reachability and its own 3-5x band are NeroTech's test's to prove, so this graph can only under-state how
 * much cheaper offworld is); a process recipe {@code energy x time}; a single-item machine recipe 3,600 plus
 * 2.5 units of oxygen; crafting is free; water 0.1 per mB. A recipe's whole cost is charged to each of its
 * outputs per unit. A NeroTech or Core id counts only if that artifact's lang file names it.
 */
class RecipeGraphTest {

    static final double LEAF = 100.0D;
    static final double FOREIGN_LEAF = 2_000.0D;
    static final double FOREIGN_FLUID_PER_MB = 2.0D;
    static final double FOREIGN_GAS_PER_UNIT = 20.0D;
    static final double WATER_PER_MB = 0.1D;
    static final double SINGLE_MACHINE = 30.0D * 120.0D + 2.5D * FOREIGN_GAS_PER_UNIT;
    static final double RATIO_MIN = 3.0D;
    static final double RATIO_MAX = 5.0D;

    static final String RECIPE_DIR = "data/neropower/recipe/";

    /** The Nerospace-sourced fertile line: these rows must hold the 3-5x band. */
    static final List<String> BAND_ROWS = List.of("pallurite_dust", "pallurite_pellet", "kindled_pellet");

    /** Downstream of the fertile line: cheaper offworld, diluted by other inputs. */
    static final List<String> CHEAPER_ROWS = List.of("braided_fuel_rod");

    /** Vanilla tags the recipes and machines use, reduced to vanilla members. */
    static final Map<String, List<String>> VANILLA_TAGS = Map.of(
            "minecraft:sand", List.of("minecraft:sand", "minecraft:red_sand"));

    static final Map<String, Double> VANILLA_WEIGHTS = Map.of(
            "minecraft:iron_nugget", LEAF / 9.0D, "minecraft:gold_nugget", LEAF / 9.0D,
            "minecraft:iron_block", LEAF * 9.0D, "minecraft:copper_block", LEAF * 9.0D,
            "minecraft:gold_block", LEAF * 9.0D);

    /** Every waste item and the step that takes it (none may be a dead end). */
    static final List<String> WASTE = List.of("hot_spent_fuel_rod", "spent_fuel_rod", "fission_residue",
            "beta_capsule", "spent_beta_capsule", "spent_isotope_pellet", "vitrified_waste");

    enum Mode { STANDALONE, OFFWORLD }

    /** An input: any one of the alternatives, {@code amount} times. */
    record Input(List<String> alternatives, double amount, String written) {
    }

    record Output(String node, double amount) {
    }

    record Edge(String name, List<Input> inputs, List<Output> outputs, double cost) {
    }

    static TreeMap<String, JsonObject> recipes;
    static Set<String> foreignIds;
    static List<String> items;

    @BeforeAll
    static void load() throws IOException, URISyntaxException {
        recipes = new TreeMap<>();
        for (String name : listJson(RECIPE_DIR)) {
            recipes.put(name, readJson(RECIPE_DIR + name + ".json").getAsJsonObject());
        }
        assertTrue(recipes.size() > 50, "recipe folder not found on the test classpath: " + recipes.size());
        foreignIds = new HashSet<>();
        for (String ns : List.of("nerotech", "nerolandcore")) {
            for (String key : langKeys(ns)) {
                Matcher m = Pattern.compile("^(item|block|gas|fluid_type)\\." + ns + "\\.([a-z0-9_]+)$").matcher(key);
                if (m.matches()) {
                    foreignIds.add(ns + ":" + m.group(2));
                }
            }
        }
        assertTrue(foreignIds.contains("nerotech:machine_frame"),
                "the NeroTech artifact's lang file is not on the test classpath");
        Set<String> own = new TreeSet<>();
        for (String key : langKeys("neropower")) {
            Matcher m = Pattern.compile("^(item|block)\\.neropower\\.([a-z0-9_]+)$").matcher(key);
            if (m.matches()) {
                own.add(m.group(2));
            }
        }
        items = new ArrayList<>(own);
    }

    // --- tests -------------------------------------------------------------------------------------

    @Test
    void everyItemIsReachableOnACoreAndNeroTechServer() {
        Map<String, Double> cost = solve(Mode.STANDALONE);
        List<String> unreachable = new ArrayList<>();
        for (String id : items) {
            if (!cost.containsKey("item:neropower:" + id)) {
                unreachable.add(id);
            }
        }
        assertTrue(unreachable.isEmpty(), "not reachable on Core + NeroTech: " + unreachable);
    }

    @Test
    void everyItemIsReachableWithNerospace() {
        Map<String, Double> cost = solve(Mode.OFFWORLD);
        List<String> unreachable = new ArrayList<>();
        for (String id : items) {
            if (!cost.containsKey("item:neropower:" + id)) {
                unreachable.add(id);
            }
        }
        assertTrue(unreachable.isEmpty(), "not reachable with Nerospace: " + unreachable);
    }

    @Test
    void theFertileLineIsThreeToFiveTimesCheaperWithNerospace() {
        Map<String, Double> standalone = solve(Mode.STANDALONE);
        Map<String, Double> offworld = solve(Mode.OFFWORLD);
        List<String> out = new ArrayList<>();
        for (String row : BAND_ROWS) {
            double ratio = standalone.get("item:neropower:" + row) / offworld.get("item:neropower:" + row);
            if (ratio < RATIO_MIN || ratio > RATIO_MAX) {
                out.add(row + "=" + String.format(java.util.Locale.ROOT, "%.2f", ratio));
            }
        }
        assertTrue(out.isEmpty(), "outside the 3-5x band: " + out);
        for (String row : CHEAPER_ROWS) {
            String node = "item:neropower:" + row;
            assertTrue(offworld.get(node) < standalone.get(node), row + " is not cheaper with Nerospace");
        }
    }

    @Test
    void nothingIsDearerWithNerospace() {
        Map<String, Double> standalone = solve(Mode.STANDALONE);
        Map<String, Double> offworld = solve(Mode.OFFWORLD);
        for (String id : items) {
            String node = "item:neropower:" + id;
            assertTrue(offworld.get(node) <= standalone.get(node) + 1e-6, id + " costs more with Nerospace");
        }
    }

    @Test
    void bothFuelLinesAndTheWasteBackEndRunEndToEnd() {
        for (Mode mode : Mode.values()) {
            Map<String, Double> cost = solve(mode);
            // Uranium line: pellet -> rod -> burnt (hot) -> cooled -> reprocessed -> back into pellets and rods.
            for (String id : List.of("uranium_pellet", "fuel_rod", "hot_spent_fuel_rod", "spent_fuel_rod",
                    "reprocessed_pellet", "fission_residue")) {
                assertTrue(cost.containsKey("item:neropower:" + id), mode + ": uranium line stops at " + id);
            }
            // Fertile line: dust -> pellet -> bred kindled pellet -> braided rod -> burnt.
            for (String id : List.of("pallurite_dust", "pallurite_pellet", "kindled_pellet", "braided_fuel_rod")) {
                assertTrue(cost.containsKey("item:neropower:" + id), mode + ": fertile line stops at " + id);
            }
            // Back end: residue -> beta capsule -> spent capsule -> vitrified waste.
            for (String id : WASTE) {
                assertTrue(cost.containsKey("item:neropower:" + id), mode + ": back end stops at " + id);
            }
            // The steam plant's fluids.
            for (String fluid : List.of("fluid:neropower:steam", "fluid:neropower:exhaust_steam",
                    "fluid:neropower:heavy_water")) {
                assertTrue(cost.containsKey(fluid), mode + ": " + fluid + " cannot be made");
            }
        }
    }

    @Test
    void noWasteIsADeadEndAndOnlyVitrifiedWasteIsFinal() {
        List<Edge> edges = edges(Mode.STANDALONE);
        Set<String> consumed = new HashSet<>();
        for (Edge edge : edges) {
            for (Input input : edge.inputs()) {
                consumed.addAll(input.alternatives());
            }
        }
        Set<String> stored = resolveItemTag("neropower:cask_storable", Mode.STANDALONE);
        List<String> dead = new ArrayList<>();
        for (String id : WASTE) {
            String node = "item:neropower:" + id;
            if (!consumed.contains(node) && !stored.contains(node)) {
                dead.add(id);
            }
        }
        assertTrue(dead.isEmpty(), "waste with no sink (no recipe, machine or cask takes it): " + dead);
        assertFalse(consumed.contains("item:neropower:vitrified_waste"),
                "vitrified waste is the inert end product: nothing may use it");
        assertTrue(stored.contains("item:neropower:vitrified_waste"), "vitrified waste must be cask-storable");
    }

    @Test
    void everyRecipeTypeIsModelled() {
        Set<String> types = new TreeSet<>();
        for (JsonObject json : recipes.values()) {
            types.add(json.get("type").getAsString());
        }
        types.removeAll(Set.of("minecraft:crafting_shaped", "minecraft:crafting_shapeless", "nerotech:centrifuging",
                "nerotech:reacting", "nerotech:chemical_processing"));
        assertTrue(types.isEmpty(), "model these recipe types in RecipeGraphTest.edges: " + types);
    }

    @Test
    void everyIngredientResolvesOnACoreAndNeroTechServerOrHasAnotherRoute() {
        // Each written ingredient either resolves standalone or is an optional route (the item has another).
        Map<String, Double> cost = solve(Mode.STANDALONE);
        List<String> dead = new ArrayList<>();
        for (Edge edge : edges(Mode.STANDALONE)) {
            for (Input input : edge.inputs()) {
                if (input.alternatives().isEmpty()) {
                    boolean otherRoute = true;
                    for (Output out : edge.outputs()) {
                        otherRoute &= cost.containsKey(out.node());
                    }
                    if (!otherRoute) {
                        dead.add(edge.name() + " <- " + input.written());
                    }
                }
            }
        }
        assertTrue(dead.isEmpty(), "ingredients nothing on a Core + NeroTech server provides: " + dead);
    }

    @Test
    void thePalluriteRoutesAreTheNumbersDecision006Names() {
        Map<String, Double> standalone = solve(Mode.STANDALONE);
        Map<String, Double> offworld = solve(Mode.OFFWORLD);
        // Per pellet: offworld one source item and one 80 x 200 run; standalone 16 tuff + 8 glowstone and two
        // 80 x 400 runs: four times the energy and the time.
        double nugget = LEAF / 9.0D;
        assertEquals(LEAF + 80.0D * 200.0D + nugget, offworld.get("item:neropower:pallurite_pellet"), 1e-6);
        assertEquals(2.0D * (8 * LEAF + 4 * LEAF + 80.0D * 400.0D) + nugget,
                standalone.get("item:neropower:pallurite_pellet"), 1e-6);
    }

    // --- the solver ----------------------------------------------------------------------------------

    static Map<String, Double> solve(Mode mode) {
        List<Edge> edges = edges(mode);
        Map<String, Double> cost = new HashMap<>();
        for (int pass = 0; pass < 500; pass++) {
            boolean changed = false;
            for (Edge edge : edges) {
                double total = edge.cost();
                boolean ok = true;
                for (Input input : edge.inputs()) {
                    double best = Double.POSITIVE_INFINITY;
                    for (String alt : input.alternatives()) {
                        Double c = leafOrCost(alt, mode, cost);
                        if (c != null && c < best) {
                            best = c;
                        }
                    }
                    if (best == Double.POSITIVE_INFINITY) {
                        ok = false;
                        break;
                    }
                    total += best * input.amount();
                }
                if (!ok) {
                    continue;
                }
                for (Output out : edge.outputs()) {
                    double unit = total / out.amount();
                    Double old = cost.get(out.node());
                    if (old == null || unit < old - 1e-9) {
                        cost.put(out.node(), unit);
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return cost;
            }
        }
        throw new AssertionError("the recipe graph did not settle in 500 passes");
    }

    /** A leaf's fixed cost, a solved node's cost, or null (not reachable in this mode yet). */
    static Double leafOrCost(String node, Mode mode, Map<String, Double> cost) {
        String[] parts = node.split(":", 2);
        String kind = parts[0];
        String id = parts[1];
        String ns = id.substring(0, id.indexOf(':'));
        switch (ns) {
            case "minecraft":
                if (kind.equals("fluid")) {
                    return id.equals("minecraft:water") ? WATER_PER_MB : id.equals("minecraft:lava") ? WATER_PER_MB : null;
                }
                return VANILLA_WEIGHTS.getOrDefault(id, LEAF);
            case "nerotech", "nerolandcore":
                if (!foreignIds.contains(id)) {
                    return null;
                }
                return kind.equals("fluid") ? FOREIGN_FLUID_PER_MB : kind.equals("gas") ? FOREIGN_GAS_PER_UNIT : FOREIGN_LEAF;
            case "nerospace":
                return mode == Mode.OFFWORLD ? LEAF : null;
            default:
                return cost.get(node);
        }
    }

    static List<Edge> edges(Mode mode) {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : recipes.entrySet()) {
            edges.add(recipeEdge(e.getKey(), e.getValue(), mode));
        }
        // Machine steps with no recipe JSON.
        edges.add(step("reactor burns a fuel rod", "item:neropower:fuel_rod", "item:neropower:hot_spent_fuel_rod", 0));
        edges.add(step("reactor burns a braided rod", "item:neropower:braided_fuel_rod",
                "item:neropower:hot_spent_fuel_rod", 0));
        edges.add(step("spent fuel rack cools a rod", "item:neropower:hot_spent_fuel_rod",
                "item:neropower:spent_fuel_rod", 0));
        edges.add(step("breeder blanket", "item:neropower:pallurite_pellet", "item:neropower:kindled_pellet", 0));
        edges.add(step("betavoltaic cell", "item:neropower:beta_capsule", "item:neropower:spent_beta_capsule", 0));
        edges.add(step("radioisotope generator", "item:neropower:isotope_pellet",
                "item:neropower:spent_isotope_pellet", 0));
        List<String> vitrifiable = new ArrayList<>(resolveItemTag("neropower:vitrifiable", mode));
        List<String> sand = new ArrayList<>(resolveItemTag("minecraft:sand", mode));
        edges.add(new Edge("vitrifier", List.of(new Input(vitrifiable, 1, "#neropower:vitrifiable"),
                new Input(sand, 2, "#minecraft:sand")), List.of(new Output("item:neropower:vitrified_waste", 1)),
                (double) BackEndConfig.vitrifierNePerTick() * BackEndConfig.vitrifierTicksPerOp()));
        // Steam plant: a core in steam mode boils water; a turbine exhausts it; a condenser returns water.
        edges.add(new Edge("reactor steam port", List.of(new Input(List.of("fluid:minecraft:water"), 1, "water")),
                List.of(new Output("fluid:neropower:steam", 1)), 0));
        edges.add(new Edge("turbine", List.of(new Input(List.of("fluid:neropower:steam"), 1, "steam")),
                List.of(new Output("fluid:neropower:exhaust_steam", 1)), 0));
        return edges;
    }

    static Edge step(String name, String in, String out, double cost) {
        return new Edge(name, List.of(new Input(List.of(in), 1, in)), List.of(new Output(out, 1)), cost);
    }

    static Edge recipeEdge(String name, JsonObject json, Mode mode) {
        String type = json.get("type").getAsString();
        List<Input> inputs = new ArrayList<>();
        List<Output> outputs = new ArrayList<>();
        double cost = 0.0D;
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                Map<Character, Integer> counts = new LinkedHashMap<>();
                for (JsonElement row : json.getAsJsonArray("pattern")) {
                    for (char c : row.getAsString().toCharArray()) {
                        if (c != ' ') {
                            counts.merge(c, 1, Integer::sum);
                        }
                    }
                }
                JsonObject key = json.getAsJsonObject("key");
                for (Map.Entry<Character, Integer> c : counts.entrySet()) {
                    JsonElement ing = key.get(String.valueOf(c.getKey()));
                    assertNotNull(ing, name + ": pattern symbol " + c.getKey() + " has no key");
                    inputs.add(itemInput(ing, c.getValue(), mode));
                }
                outputs.add(itemResult(json.getAsJsonObject("result")));
            }
            case "minecraft:crafting_shapeless" -> {
                for (JsonElement ing : json.getAsJsonArray("ingredients")) {
                    inputs.add(itemInput(ing, 1, mode));
                }
                outputs.add(itemResult(json.getAsJsonObject("result")));
            }
            case "nerotech:chemical_processing" -> {
                inputs.add(itemInput(json.get("ingredient"), 1, mode));
                outputs.add(itemResult(json.getAsJsonObject("result")));
                cost = SINGLE_MACHINE;
            }
            case "nerotech:centrifuging", "nerotech:reacting" -> {
                if (json.has("items")) {
                    for (JsonElement e : json.getAsJsonArray("items")) {
                        JsonObject o = e.getAsJsonObject();
                        int count = o.has("count") ? o.get("count").getAsInt() : 1;
                        inputs.add(itemInput(o.has("ingredient") ? o.get("ingredient") : o, count, mode));
                    }
                }
                if (json.has("fluid_in")) {
                    inputs.add(stackInput(json.getAsJsonObject("fluid_in"), "fluid", mode));
                }
                if (json.has("gas_in")) {
                    inputs.add(stackInput(json.getAsJsonObject("gas_in"), "gas", mode));
                }
                if (json.has("results")) {
                    for (JsonElement e : json.getAsJsonArray("results")) {
                        outputs.add(itemResult(e.getAsJsonObject()));
                    }
                }
                if (json.has("fluid_out")) {
                    JsonObject o = json.getAsJsonObject("fluid_out");
                    outputs.add(new Output("fluid:" + idOf(o), o.get("amount").getAsDouble()));
                }
                if (json.has("gas_out")) {
                    JsonObject o = json.getAsJsonObject("gas_out");
                    outputs.add(new Output("gas:" + idOf(o), o.get("amount").getAsDouble()));
                }
                cost = json.get("energy").getAsDouble() * json.get("time").getAsDouble();
            }
            default -> {
                // everyRecipeTypeIsModelled names it
            }
        }
        return new Edge(name, inputs, outputs, cost);
    }

    static Output itemResult(JsonObject result) {
        double chance = result.has("chance") ? result.get("chance").getAsDouble() : 1.0D;
        int count = result.has("count") ? result.get("count").getAsInt() : 1;
        return new Output("item:" + idOf(result), count * chance);
    }

    static String idOf(JsonObject o) {
        for (String k : List.of("id", "item", "fluid", "gas")) {
            if (o.has(k) && o.get(k).isJsonPrimitive()) {
                return o.get(k).getAsString();
            }
        }
        throw new AssertionError("no id in " + o);
    }

    /** An item ingredient in any written form: "id", "#tag", {item}, {tag}, or a list of them. */
    static Input itemInput(JsonElement ing, int count, Mode mode) {
        List<String> alts = new ArrayList<>();
        collectItem(ing, mode, alts);
        return new Input(alts, count, ing.toString());
    }

    static void collectItem(JsonElement ing, Mode mode, List<String> out) {
        if (ing.isJsonArray()) {
            for (JsonElement e : ing.getAsJsonArray()) {
                collectItem(e, mode, out);
            }
        } else if (ing.isJsonPrimitive()) {
            String s = ing.getAsString();
            if (s.startsWith("#")) {
                out.addAll(resolveItemTag(s.substring(1), mode));
            } else if (present(s, mode)) {
                out.add("item:" + s);
            }
        } else {
            JsonObject o = ing.getAsJsonObject();
            if (o.has("tag")) {
                out.addAll(resolveItemTag(o.get("tag").getAsString(), mode));
            } else if (o.has("item") && present(o.get("item").getAsString(), mode)) {
                out.add("item:" + o.get("item").getAsString());
            }
        }
    }

    static Input stackInput(JsonObject o, String kind, Mode mode) {
        String raw = o.has("tag") ? "#" + o.get("tag").getAsString() : idOf(o);
        double amount = o.get("amount").getAsDouble();
        List<String> alts = new ArrayList<>();
        if (raw.startsWith("#")) {
            for (String id : resolveTag(raw.substring(1), kind, mode, new HashSet<>())) {
                alts.add(kind + ":" + id);
            }
        } else if (present(raw, mode)) {
            alts.add(kind + ":" + raw);
        }
        return new Input(alts, amount, raw);
    }

    /** Whether an id can exist on this server at all (unknown mods' optional entries never do). */
    static boolean present(String id, Mode mode) {
        String ns = id.substring(0, id.indexOf(':'));
        return switch (ns) {
            case "minecraft", "neropower" -> true;
            case "nerotech", "nerolandcore" -> foreignIds.contains(id);
            case "nerospace" -> mode == Mode.OFFWORLD;
            default -> false;
        };
    }

    static Set<String> resolveItemTag(String tag, Mode mode) {
        Set<String> out = new TreeSet<>();
        for (String id : resolveTag(tag, "item", mode, new HashSet<>())) {
            out.add("item:" + id);
        }
        return out;
    }

    static final Map<String, List<JsonElement>> TAG_CACHE = new HashMap<>();

    static Set<String> resolveTag(String tag, String kind, Mode mode, Set<String> seen) {
        Set<String> out = new TreeSet<>();
        if (!seen.add(kind + "#" + tag)) {
            return out;
        }
        if (kind.equals("item") && VANILLA_TAGS.containsKey(tag)) {
            out.addAll(VANILLA_TAGS.get(tag));
            return out;
        }
        for (JsonElement value : tagValues(tag, kind)) {
            String id;
            if (value.isJsonPrimitive()) {
                id = value.getAsString();
            } else {
                id = value.getAsJsonObject().get("id").getAsString();
            }
            if (id.startsWith("#")) {
                out.addAll(resolveTag(id.substring(1), kind, mode, seen));
            } else if (present(id, mode)) {
                out.add(id);
            }
        }
        return out;
    }

    /** Every value of a tag across the artifacts on the classpath (NeroPower, NeroTech, Core all merge). */
    static List<JsonElement> tagValues(String tag, String kind) {
        return TAG_CACHE.computeIfAbsent(kind + "#" + tag, k -> {
            String ns = tag.substring(0, tag.indexOf(':'));
            String path = tag.substring(tag.indexOf(':') + 1);
            List<JsonElement> values = new ArrayList<>();
            try {
                Enumeration<URL> urls = RecipeGraphTest.class.getClassLoader()
                        .getResources("data/" + ns + "/tags/" + kind + "/" + path + ".json");
                while (urls.hasMoreElements()) {
                    try (InputStream in = urls.nextElement().openStream();
                            Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("values");
                        arr.forEach(values::add);
                    }
                }
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return values;
        });
    }

    // --- resources -----------------------------------------------------------------------------------

    static Set<String> langKeys(String ns) throws IOException {
        Set<String> keys = new HashSet<>();
        Enumeration<URL> urls = RecipeGraphTest.class.getClassLoader().getResources("assets/" + ns + "/lang/en_us.json");
        while (urls.hasMoreElements()) {
            try (InputStream in = urls.nextElement().openStream();
                    Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                keys.addAll(JsonParser.parseReader(r).getAsJsonObject().keySet());
            }
        }
        return keys;
    }

    static JsonElement readJson(String path) throws IOException {
        try (InputStream in = RecipeGraphTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    /** JSON file names (no extension) in a resource folder, from plain folders and jars alike. */
    static Set<String> listJson(String dir) throws IOException, URISyntaxException {
        Set<String> names = new TreeSet<>();
        Enumeration<URL> urls = RecipeGraphTest.class.getClassLoader().getResources(dir);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            if ("file".equals(url.getProtocol())) {
                File[] files = new File(url.toURI()).listFiles((d, n) -> n.endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        names.add(f.getName().substring(0, f.getName().length() - 5));
                    }
                }
            } else if ("jar".equals(url.getProtocol())) {
                JarFile jar = ((JarURLConnection) url.openConnection()).getJarFile();
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String n = entries.nextElement().getName();
                    if (n.startsWith(dir) && n.endsWith(".json") && n.indexOf('/', dir.length()) < 0) {
                        names.add(n.substring(dir.length(), n.length() - 5));
                    }
                }
            }
        }
        return names;
    }
}

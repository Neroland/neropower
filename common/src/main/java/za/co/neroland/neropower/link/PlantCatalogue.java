package za.co.neroland.neropower.link;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The public {@code catalogue} section (link schema 2): static facts about NeroPower that are the same
 * for every player — the blocks by family and role, the fuel lines as step lists, and the storage
 * tiers with their capacity and I/O as this server is configured. No world data, no player data.
 * Pure: {@link #build(Tiers)} is a function of the config numbers only, so two players always get the
 * same answer (tested).
 */
final class PlantCatalogue {

    /** Bumped when the catalogue's shape changes (independent of the module schema). */
    static final int CATALOGUE_VERSION = 1;

    private PlantCatalogue() {
    }

    /** One block: id, family, role. */
    record Block(String id, String family, String role) {
    }

    /** One storage tier: kind, tier key, capacity (NE), I/O (NE/t), and whether it scales per part. */
    record Tier(String kind, String tier, long capacity, long io, String per) {
    }

    /** The storage numbers from the server config, in one immutable bundle. */
    record Tiers(List<Tier> rows) {
    }

    /** Every NeroPower block (41), by family. Ids are frozen; roles are the app's grouping words. */
    static final List<Block> BLOCKS = List.of(
            // fission
            new Block("neropower:fission_core", "fission", "controller"),
            new Block("neropower:fission_casing", "fission", "shell"),
            new Block("neropower:control_rod_assembly", "fission", "interior"),
            new Block("neropower:europium_control_rod_assembly", "fission", "interior"),
            new Block("neropower:graphite_moderator", "fission", "interior"),
            new Block("neropower:moderator_tank", "fission", "interior"),
            new Block("neropower:breeder_blanket", "fission", "interior"),
            new Block("neropower:reactor_steam_port", "fission", "port"),
            new Block("neropower:reactor_control_computer", "fission", "control"),
            // steam
            new Block("neropower:turbine_helm", "steam", "controller"),
            new Block("neropower:turbine_shroud", "steam", "shell"),
            new Block("neropower:turbine_spindle", "steam", "rotor"),
            new Block("neropower:turbine_stator", "steam", "generator"),
            new Block("neropower:turbine_steam_port", "steam", "port"),
            new Block("neropower:condenser", "steam", "cooling"),
            // back end
            new Block("neropower:spent_fuel_pool", "back_end", "cooling"),
            new Block("neropower:vitrifier", "back_end", "processing"),
            new Block("neropower:dry_cask", "back_end", "storage"),
            // generation
            new Block("neropower:radioisotope_generator", "generation", "generator"),
            new Block("neropower:stirling_generator", "generation", "generator"),
            new Block("neropower:fuel_cell", "generation", "generator"),
            new Block("neropower:betavoltaic_cell", "generation", "generator"),
            // storage
            new Block("neropower:battery_bank_controller", "storage", "controller"),
            new Block("neropower:battery_cell_basic", "storage", "cell"),
            new Block("neropower:battery_cell_advanced", "storage", "cell"),
            new Block("neropower:battery_cell_elite", "storage", "cell"),
            new Block("neropower:surge_bank", "storage", "cell"),
            new Block("neropower:momentum_wheel", "storage", "store"),
            new Block("neropower:smes_controller", "storage", "controller"),
            new Block("neropower:smes_coil", "storage", "coil"),
            // beams and grid
            new Block("neropower:beam_transmitter", "beam", "source"),
            new Block("neropower:beam_relay", "beam", "relay"),
            new Block("neropower:beam_receiver", "beam", "sink"),
            new Block("neropower:orbital_receiver", "beam", "sink"),
            new Block("neropower:beam_splitter", "beam", "source"),
            new Block("neropower:phased_array_emitter", "beam", "source"),
            new Block("neropower:circuit_breaker", "grid", "protection"),
            new Block("neropower:load_bank", "grid", "test"),
            new Block("neropower:power_monitor", "grid", "instrument"),
            // radiation
            new Block("neropower:shielding_block", "radiation", "shielding"),
            new Block("neropower:shielding_glass", "radiation", "shielding"));

    /** A fuel line step: what goes in, the process, what comes out. */
    private record Step(String input, String process, String output) {
    }

    private record Line(String id, List<Step> steps) {
    }

    private static final List<Line> FUEL_LINES = List.of(
            new Line("uranium", List.of(
                    new Step("minecraft:glowstone_dust", "crafting", "neropower:uranium_pellet"),
                    new Step("neropower:uranium_pellet", "crafting", "neropower:fuel_rod"),
                    new Step("neropower:fuel_rod", "fission", "neropower:hot_spent_fuel_rod"),
                    new Step("neropower:hot_spent_fuel_rod", "neropower:spent_fuel_pool", "neropower:spent_fuel_rod"),
                    new Step("neropower:spent_fuel_rod", "nerotech:centrifuging", "neropower:reprocessed_pellet"),
                    new Step("neropower:reprocessed_pellet", "crafting", "neropower:uranium_pellet"))),
            new Line("pallurite", List.of(
                    new Step("#neropower:fertile_sources", "nerotech:centrifuging", "neropower:pallurite_dust"),
                    new Step("minecraft:tuff", "nerotech:centrifuging", "neropower:pallurite_dust"),
                    new Step("neropower:pallurite_dust", "crafting", "neropower:pallurite_pellet"),
                    new Step("neropower:pallurite_pellet", "neropower:breeder_blanket", "neropower:kindled_pellet"))),
            new Line("braided", List.of(
                    new Step("neropower:kindled_pellet", "crafting", "neropower:braided_fuel_rod"),
                    new Step("neropower:braided_fuel_rod", "fission", "neropower:hot_spent_fuel_rod"))),
            new Line("waste", List.of(
                    new Step("neropower:spent_fuel_rod", "neropower:dry_cask", "neropower:spent_fuel_rod"),
                    new Step("neropower:fission_residue", "nerotech:centrifuging", "neropower:beta_capsule"),
                    new Step("neropower:beta_capsule", "neropower:betavoltaic_cell", "neropower:spent_beta_capsule"),
                    new Step("#neropower:vitrifiable", "neropower:vitrifier", "neropower:vitrified_waste"))));

    /** The section body. Same input, same output: no clock, no player, no world. */
    static JsonObject build(Tiers tiers) {
        JsonObject out = new JsonObject();
        out.addProperty("section", NeroPowerLinkModule.SECTION_CATALOGUE);
        out.addProperty("schema", NeroPowerLinkModule.SCHEMA_VERSION);
        out.addProperty("catalogueVersion", CATALOGUE_VERSION);
        out.addProperty("scope", "public");

        JsonArray blocks = new JsonArray();
        for (Block b : BLOCKS) {
            JsonObject o = new JsonObject();
            o.addProperty("id", b.id());
            o.addProperty("family", b.family());
            o.addProperty("role", b.role());
            blocks.add(o);
        }
        out.add("blocks", blocks);

        JsonArray lines = new JsonArray();
        for (Line line : FUEL_LINES) {
            JsonObject o = new JsonObject();
            o.addProperty("id", line.id());
            JsonArray steps = new JsonArray();
            for (Step s : line.steps()) {
                JsonObject st = new JsonObject();
                st.addProperty("input", s.input());
                st.addProperty("process", s.process());
                st.addProperty("output", s.output());
                steps.add(st);
            }
            o.add("steps", steps);
            lines.add(o);
        }
        out.add("fuelLines", lines);

        JsonArray storage = new JsonArray();
        for (Tier t : tiers.rows()) {
            JsonObject o = new JsonObject();
            o.addProperty("kind", t.kind());
            o.addProperty("tier", t.tier());
            o.addProperty("capacity", t.capacity());
            o.addProperty("io", t.io());
            o.addProperty("per", t.per());
            storage.add(o);
        }
        out.add("storageTiers", storage);
        return out;
    }
}

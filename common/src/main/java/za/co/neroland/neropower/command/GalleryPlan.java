package za.co.neroland.neropower.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The gallery power station as pure data: every block, label, exhibit setup and tour stop of
 * {@code /neropower gallery}, as offsets from the command origin (the caller's block position; {@code dy 0} is
 * the floor layer; {@code dx} east, {@code dz} south). Pure JVM so tests can check the bounding box, the tour
 * and that nothing in the plan records a player. {@link GalleryStation} places it in a world.
 *
 * <p>Two placement phases: {@link #PHASE_PARTS} (floor, shells, parts, props) and {@link #PHASE_CONTROLLERS}
 * (Fission Cores, Turbine Helm, Storage Ring Controller, Battery Bank Controllers), so every structure sees
 * its parts whole on its first check. {@link Setup} steps run a few ticks later, once the structures formed.
 */
public final class GalleryPlan {

    /** Placement phases. */
    public static final int PHASE_PARTS = 0;
    public static final int PHASE_CONTROLLERS = 1;

    /** The box {@code gallery clear} wipes (inclusive offsets). */
    public static final int CLEAR_MIN_DX = -31;
    public static final int CLEAR_MAX_DX = 31;
    public static final int CLEAR_MIN_DY = 0;
    public static final int CLEAR_MAX_DY = 12;
    public static final int CLEAR_MIN_DZ = -45;
    public static final int CLEAR_MAX_DZ = 5;

    /** Everything the plan places, labels included, lies in this box (inclusive offsets). */
    public static final int BUILD_MIN_DX = -30;
    public static final int BUILD_MAX_DX = 30;
    public static final int BUILD_MAX_DY = 10;
    public static final int BUILD_MIN_DZ = -44;
    public static final int BUILD_MAX_DZ = 4;

    /** Shielding faces each sealed cask must have (all six). */
    public static final int SEALED_FACES = 6;

    /** One block: registry id and block-state properties by name (a property the block lacks is ignored). */
    public record Place(int dx, int dy, int dz, String id, Map<String, String> props, int phase) {
    }

    /** A floating label. */
    public record Label(double dx, double dy, double dz, String text) {
    }

    /** What a setup step does once the structures formed. */
    public enum Kind {
        /** Insert {@code count} of item {@code arg} into the first slots that accept it. */
        ITEMS,
        /** Fill the block's fluid storage with {@code count} mB of fluid {@code arg} ({@code #tag} allowed). */
        FLUID,
        /** Request steam mode on a Fission Core. */
        STEAM_MODE,
        /** Reactor Control Computer: auto-SCRAM on at {@code count} permille. */
        AUTO_SCRAM,
        /** Charge an energy store to {@code count} permille of its capacity. */
        CHARGE_PERMILLE,
        /** Beam link from this block to {@code target} under the gallery token (transmitter / relay). */
        BEAM_LINK,
        /** Beam Splitter link to {@code target} with share {@code count} permille. */
        SPLITTER_LINK,
        /** Phased Array Emitter link to {@code target}. */
        ARRAY_LINK,
        /** Circuit Breaker limit {@code count} NE/t. */
        BREAKER_LIMIT,
        /** Load Bank rate {@code count} NE/t. */
        LOAD_RATE,
        /** Assert this cask has all six faces covered by radiation barriers. */
        SEALED
    }

    /** One setup step at a block; {@code target} is used by the link kinds only. */
    public record Setup(int dx, int dy, int dz, Kind kind, String arg, long count, int[] target) {
    }

    /** One stop of {@code gallery tour}: camera offset, yaw / pitch, and the caption's lang key. */
    public record TourStop(double dx, double dy, double dz, float yaw, float pitch, String captionKey) {
    }

    /** The multiblock controllers the build checks for a formed structure. */
    public record Controller(int dx, int dy, int dz, String what) {
    }

    private final Map<Long, Place> places = new LinkedHashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private final List<Setup> setups = new ArrayList<>();
    private final List<TourStop> tour = new ArrayList<>();
    private final List<Controller> controllers = new ArrayList<>();

    /**
     * Build the plan. {@code fullRods}: load six Fuel Rods in Unit 1 (air a Steam Condenser can cool well) or
     * two (thin air or vacuum, where only the Radiators cool it).
     */
    public static GalleryPlan create(boolean fullRods) {
        GalleryPlan p = new GalleryPlan();
        p.floor();
        p.unit1(fullRods);
        p.unit2();
        p.wasteLine();
        p.storageHall();
        p.breakerWall();
        p.testBench();
        p.beamYard();
        p.smallGenerators();
        p.labels();
        p.tour();
        return p;
    }

    public List<Place> places() {
        return Collections.unmodifiableList(new ArrayList<>(this.places.values()));
    }

    public List<Label> labelList() {
        return Collections.unmodifiableList(this.labels);
    }

    public List<Setup> setupList() {
        return Collections.unmodifiableList(this.setups);
    }

    public List<TourStop> tourStops() {
        return Collections.unmodifiableList(this.tour);
    }

    /**
     * Whether a gallery stands at a candidate origin: every structure controller the plan places is an
     * exhibit at its offset ({@code exhibitAt} gets {@code {dx, dy, dz}}). Used to find a gallery whose
     * origin is not remembered (after a restart) without guessing from where the operator stands.
     */
    public boolean originConfirmed(java.util.function.Predicate<int[]> exhibitAt) {
        if (this.controllers.isEmpty()) {
            return false;
        }
        for (Controller c : this.controllers) {
            if (!exhibitAt.test(new int[] {c.dx(), c.dy(), c.dz()})) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code clear} may remove a block at a planned position: it is still the block the plan put
     * there, and, if it is a machine, it carries the exhibit flag. Anything else (a player's block or
     * machine built there since) stays.
     */
    public static boolean removable(boolean samePlannedBlock, boolean machine, boolean exhibit) {
        return samePlannedBlock && (!machine || exhibit);
    }

    /** The clear box's size, x by y by z, for the build message. */
    public static int[] clearBoxSize() {
        return new int[] {CLEAR_MAX_DX - CLEAR_MIN_DX + 1, CLEAR_MAX_DY - CLEAR_MIN_DY + 1, CLEAR_MAX_DZ - CLEAR_MIN_DZ + 1};
    }

    public List<Controller> controllerList() {
        return Collections.unmodifiableList(this.controllers);
    }

    /** Whether an offset lies in the clear box. */
    public static boolean inClearBox(double dx, double dy, double dz) {
        return dx >= CLEAR_MIN_DX && dx < CLEAR_MAX_DX + 1 && dy >= CLEAR_MIN_DY && dy < CLEAR_MAX_DY + 1
                && dz >= CLEAR_MIN_DZ && dz < CLEAR_MAX_DZ + 1;
    }

    // --- plumbing ----------------------------------------------------------------------------------

    private static long key(int dx, int dy, int dz) {
        return ((long) (dx + 512) << 40) | ((long) (dy + 512) << 20) | (dz + 512);
    }

    private static Map<String, String> props(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private void put(int dx, int dy, int dz, String id, String... kv) {
        put(dx, dy, dz, id, PHASE_PARTS, kv);
    }

    private void put(int dx, int dy, int dz, String id, int phase, String... kv) {
        long k = key(dx, dy, dz);
        this.places.remove(k); // a later entry replaces an earlier one and moves to the end
        this.places.put(k, new Place(dx, dy, dz, id, props(kv), phase));
    }

    private void fill(int x0, int x1, int y0, int y1, int z0, int z1, String id, String... kv) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    put(x, y, z, id, kv);
                }
            }
        }
    }

    private void setup(int dx, int dy, int dz, Kind kind, String arg, long count) {
        this.setups.add(new Setup(dx, dy, dz, kind, arg, count, null));
    }

    private void link(int dx, int dy, int dz, Kind kind, int tx, int ty, int tz, long share) {
        this.setups.add(new Setup(dx, dy, dz, kind, "", share, new int[] {tx, ty, tz}));
    }

    private void plinth(int dx, int dz, int top) {
        for (int y = 1; y <= top; y++) {
            put(dx, y, dz, "minecraft:polished_deepslate");
        }
    }

    private static final String NP = "neropower:";

    // --- zones -------------------------------------------------------------------------------------

    private void floor() {
        fill(-30, 30, 0, 0, -44, 4, "minecraft:gray_concrete");
        fill(-1, 1, 0, 0, -42, 2, "minecraft:light_gray_concrete");
        fill(-7, 20, 0, 0, -17, -16, "minecraft:light_gray_concrete");
        fill(-7, 20, 0, 0, -30, -29, "minecraft:light_gray_concrete");
        fill(-15, 13, 0, 0, -34, -33, "minecraft:light_gray_concrete");
        fill(21, 21, 0, 0, -42, 2, "minecraft:yellow_concrete");
        hazardRing(-15, -7, -15, -7);
        hazardRing(-14, -8, -27, -21);
        for (int z = -42; z <= 2; z += 4) {
            put(-2, 0, z, "minecraft:sea_lantern");
            put(2, 0, z, "minecraft:sea_lantern");
        }
        for (int x : new int[] {-16, -4, 5, 16, 24, 29}) {
            for (int z : new int[] {-40, -32, -24, -16, -8, 0}) {
                put(x, 0, z, "minecraft:sea_lantern");
            }
        }
        put(11, 0, -11, "minecraft:sea_lantern");
    }

    private void hazardRing(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    put(x, 0, z, "minecraft:yellow_concrete");
                }
            }
        }
    }

    /** A hollow cubic Fission Casing shell (interior air). */
    private void shell(int x0, int y0, int z0, int size) {
        for (int x = x0; x < x0 + size; x++) {
            for (int y = y0; y < y0 + size; y++) {
                for (int z = z0; z < z0 + size; z++) {
                    boolean surface = x == x0 || x == x0 + size - 1 || y == y0 || y == y0 + size - 1
                            || z == z0 || z == z0 + size - 1;
                    put(x, y, z, surface ? NP + "fission_casing" : "minecraft:air");
                }
            }
        }
    }

    /** Unit 1: 7x7x7 reactor in steam mode, a length-8 turbine and the condensate return. */
    private void unit1(boolean fullRods) {
        shell(-14, 1, -14, 7);
        put(-8, 2, -11, NP + "reactor_control_computer", "facing", "east");
        put(-11, 3, -8, NP + "reactor_steam_port", "facing", "south");
        put(-13, 6, -8, NP + "reactor_steam_port", "facing", "south");
        put(-13, 2, -13, NP + "control_rod_assembly");
        put(-9, 2, -13, NP + "control_rod_assembly");
        put(-8, 4, -11, NP + "fission_core", PHASE_CONTROLLERS, "facing", "east");
        this.controllers.add(new Controller(-8, 4, -11, "Unit 1 reactor"));
        setup(-13, 6, -8, Kind.FLUID, "minecraft:water", 16_000);
        setup(-8, 4, -11, Kind.ITEMS, NP + "fuel_rod", fullRods ? 6 : 2);
        setup(-8, 4, -11, Kind.STEAM_MODE, "", 0);
        setup(-8, 2, -11, Kind.AUTO_SCRAM, "", 700);

        // Turbine: x -15..-8, y 1..5, z -7..-3; axis x, centre (y 3, z -5).
        for (int x = -15; x <= -8; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = -7; z <= -3; z++) {
                    boolean inner = x > -15 && x < -8 && y >= 2 && y <= 4 && z >= -6 && z <= -4;
                    if (!inner) {
                        put(x, y, z, NP + "turbine_shroud");
                    }
                }
            }
        }
        String[] vanes = {"steel", "cindersteel", "superconducting", "none", "none", "none"};
        String[] windings = {null, null, null, "tier1", "tier2", "tier3"};
        for (int i = 0; i < 6; i++) {
            int x = -9 - i;
            put(x, 3, -5, NP + "turbine_spindle", "axis", "x", "vanes", vanes[i]);
            for (int y = 2; y <= 4; y++) {
                for (int z = -6; z <= -4; z++) {
                    if (y == 3 && z == -5) {
                        continue;
                    }
                    if (windings[i] == null) {
                        put(x, y, z, "minecraft:air");
                    } else {
                        put(x, y, z, NP + "turbine_stator", "axis", "x", "winding", windings[i]);
                    }
                }
            }
        }
        put(-11, 3, -7, NP + "turbine_steam_port", "port_mode", "inlet", "facing", "north");
        put(-13, 5, -6, NP + "turbine_steam_port", "port_mode", "exhaust", "facing", "north");
        put(-8, 3, -5, NP + "turbine_helm", PHASE_CONTROLLERS, "facing", "east");
        this.controllers.add(new Controller(-8, 3, -5, "Unit 1 turbine"));
        setup(-8, 3, -5, Kind.ITEMS, NP + "governor_module", 1);

        // Condensate return.
        put(-13, 6, -6, NP + "condenser", "facing", "south");
        put(-13, 6, -7, "nerolandcore:fluid_tank");
        put(-12, 6, -6, "nerotech:radiator");
        put(-14, 6, -6, "nerotech:radiator");
        put(-13, 7, -6, "nerotech:radiator");

        // Generator output: breaker, bank, monitor.
        put(-7, 3, -5, NP + "circuit_breaker", "facing", "east");
        setup(-7, 3, -5, Kind.BREAKER_LIMIT, "", 2_000);
        put(-6, 2, -5, NP + "battery_cell_elite");
        put(-6, 1, -5, NP + "battery_cell_advanced");
        put(-5, 3, -5, NP + "power_monitor", "facing", "east");
        plinth(-7, -5, 2);
        plinth(-5, -5, 2);
        put(-6, 3, -5, NP + "battery_bank_controller", PHASE_CONTROLLERS, "facing", "east");
    }

    /** Unit 2: the 5x5x5 breeder reactor in direct mode with its cooling cluster. */
    private void unit2() {
        shell(-13, 1, -26, 5);
        put(-9, 2, -23, NP + "reactor_control_computer", "facing", "east");
        for (int[] b : new int[][] {{-12, -25}, {-10, -25}, {-12, -23}, {-10, -23}}) {
            put(b[0], 2, b[1], NP + "breeder_blanket");
        }
        put(-11, 3, -24, NP + "moderator_tank");
        put(-11, 2, -24, NP + "europium_control_rod_assembly");
        put(-11, 4, -24, NP + "graphite_moderator");
        put(-9, 3, -24, NP + "fission_core", PHASE_CONTROLLERS, "facing", "east");
        this.controllers.add(new Controller(-9, 3, -24, "Unit 2 reactor"));
        setup(-9, 3, -24, Kind.ITEMS, NP + "fuel_rod", 2);
        setup(-9, 3, -24, Kind.ITEMS, NP + "pallurite_pellet", 16);
        setup(-9, 2, -23, Kind.AUTO_SCRAM, "", 700);

        put(-8, 2, -24, NP + "battery_cell_elite");
        put(-8, 1, -24, NP + "battery_cell_elite");
        put(-8, 3, -25, NP + "stirling_generator", "facing", "east");
        put(-8, 2, -25, "nerotech:radiator");
        put(-8, 1, -25, "minecraft:polished_deepslate");
        put(-8, 4, -24, "nerotech:coolant_pump");
        put(-8, 5, -24, "nerotech:radiator");
        put(-7, 3, -24, NP + "power_monitor", "facing", "east");
        put(-8, 3, -24, NP + "battery_bank_controller", PHASE_CONTROLLERS, "facing", "east");
    }

    /** The waste line: cooling basin, Vitrifier and the cask yard. */
    private void wasteLine() {
        fill(-14, -10, 0, 0, -40, -36, NP + "shielding_block");
        for (int y = 1; y <= 2; y++) {
            for (int x = -14; x <= -10; x++) {
                for (int z = -40; z <= -36; z++) {
                    boolean ring = x == -14 || x == -10 || z == -40 || z == -36;
                    put(x, y, z, ring ? NP + "shielding_glass" : "minecraft:water");
                }
            }
        }
        put(-12, 1, -38, NP + "spent_fuel_pool", "facing", "south");
        setup(-12, 1, -38, Kind.ITEMS, NP + "hot_spent_fuel_rod", 2);
        setup(-12, 1, -38, Kind.ITEMS, NP + "spent_fuel_rod", 2);

        put(-6, 1, -38, NP + "vitrifier", "facing", "south");
        put(-6, 1, -39, "nerolandcore:creative_battery");
        setup(-6, 1, -38, Kind.ITEMS, NP + "fission_residue", 16);
        setup(-6, 1, -38, Kind.ITEMS, "minecraft:sand", 32);

        for (int x = 2; x <= 11; x++) {
            put(x, 1, -35, NP + "shielding_block");
            put(x, 2, -35, NP + "shielding_glass");
        }
        fill(3, 9, 0, 0, -39, -37, NP + "shielding_block");
        fill(3, 9, 1, 2, -39, -37, NP + "shielding_glass");
        for (int x : new int[] {4, 6, 8}) {
            put(x, 1, -38, NP + "dry_cask", "facing", "south");
            setup(x, 1, -38, Kind.ITEMS, NP + "spent_fuel_rod", 9);
            setup(x, 1, -38, Kind.ITEMS, NP + "fission_residue", 9);
            setup(x, 1, -38, Kind.SEALED, "", SEALED_FACES);
            put(x, 1, -42, NP + "dry_cask", "facing", "south");
            setup(x, 1, -42, Kind.ITEMS, NP + "vitrified_waste", 9);
        }
    }

    /** The storage hall: an edge-7 Storage Ring, a Surge Bank and a Momentum Wheel. */
    private void storageHall() {
        for (int x = 8; x <= 14; x++) {
            for (int z = -14; z <= -8; z++) {
                boolean border = x == 8 || x == 14 || z == -14 || z == -8;
                if (border && !(x == 11 && z == -8)) {
                    put(x, 1, z, NP + "smes_coil");
                }
            }
        }
        put(11, 1, -7, NP + "power_monitor", "facing", "south");
        put(11, 1, -8, NP + "smes_controller", PHASE_CONTROLLERS, "facing", "south");
        this.controllers.add(new Controller(11, 1, -8, "Storage Ring"));
        setup(11, 1, -8, Kind.FLUID, "#nerotech:cryo_coolant", 8_000);
        setup(11, 1, -8, Kind.CHARGE_PERMILLE, "", 600);

        put(18, 1, -12, "nerolandcore:creative_battery");
        put(17, 1, -12, NP + "surge_bank", "facing", "west");
        put(16, 1, -12, NP + "power_monitor", "facing", "west");
        put(18, 1, -9, "nerolandcore:creative_battery");
        put(17, 1, -9, NP + "momentum_wheel", "facing", "west");
        put(16, 1, -9, NP + "power_monitor", "facing", "west");
    }

    /** Three live breaker rows (calm, strained, tripped) and the two display breakers. */
    private void breakerWall() {
        long[] limits = {2_000, 1_200, 500};
        for (int i = 0; i < 3; i++) {
            int y = 1 + i;
            put(6, y, -20, "nerolandcore:creative_battery");
            put(7, y, -20, NP + "battery_cell_basic");
            setup(7, y, -20, Kind.CHARGE_PERMILLE, "", 1_000);
            put(8, y, -20, NP + "circuit_breaker", "facing", "east");
            setup(8, y, -20, Kind.BREAKER_LIMIT, "", limits[i]);
            put(9, y, -20, NP + "surge_bank", "facing", "south");
            put(10, y, -20, NP + "load_bank", "facing", "south");
            setup(10, y, -20, Kind.LOAD_RATE, "", 800);
            put(9, y, -19, NP + "power_monitor", "facing", "south");
            put(11, y, -20, "nerotech:radiator");
        }
        put(13, 1, -20, "minecraft:polished_deepslate");
        put(15, 1, -20, "minecraft:polished_deepslate");
        put(13, 2, -20, NP + "circuit_breaker", "facing", "south", "tripped", "false");
        put(15, 2, -20, NP + "circuit_breaker", "facing", "south", "tripped", "true");
    }

    /** The Load Bank test bench feeding a Stirling Generator. */
    private void testBench() {
        put(8, 2, -26, "nerolandcore:creative_battery");
        put(9, 2, -26, NP + "load_bank", "facing", "south");
        setup(9, 2, -26, Kind.LOAD_RATE, "", 2_000);
        put(10, 2, -26, NP + "stirling_generator", "facing", "south");
        put(10, 1, -26, "nerotech:radiator");
        put(11, 2, -26, NP + "battery_cell_basic");
        put(11, 2, -25, NP + "power_monitor", "facing", "south");
        put(8, 1, -26, "minecraft:polished_deepslate");
        put(9, 1, -26, "minecraft:polished_deepslate");
        put(11, 1, -26, "minecraft:polished_deepslate");
    }

    /** The beam yard: transmitter, relay, splitter fan and the phased array, all overhead at dy 4. */
    private void beamYard() {
        int y = 4;
        int[][] raised = {{29, -2}, {28, -2}, {25, -2}, {25, -17}, {22, -17}, {22, -18}, {28, -17}, {28, -18},
                {25, -25}, {25, -26}, {22, -40}, {25, -40}, {28, -40}, {22, -41}, {25, -41}, {28, -41}};
        for (int[] r : raised) {
            plinth(r[0], r[1], 3);
        }
        put(29, y, -2, "nerolandcore:creative_battery");
        put(28, y, -2, NP + "beam_transmitter", "facing", "west");
        put(25, y, -2, NP + "beam_relay", "facing", "north");
        put(25, y, -17, NP + "beam_splitter", "facing", "south");
        put(22, y, -17, NP + "beam_receiver", "facing", "east");
        put(22, y, -18, NP + "battery_cell_basic");
        put(28, y, -17, NP + "beam_receiver", "facing", "west");
        put(28, y, -18, NP + "battery_cell_basic");
        put(25, y, -25, "nerolandcore:creative_battery");
        put(25, y, -26, NP + "phased_array_emitter", "facing", "north");
        for (int x : new int[] {22, 25, 28}) {
            put(x, y, -40, NP + "beam_receiver", "facing", "south");
            put(x, y, -41, NP + "battery_cell_basic");
        }
        put(23, 1, -6, NP + "orbital_receiver", "facing", "west");

        link(28, y, -2, Kind.BEAM_LINK, 25, y, -2, 0);
        link(25, y, -2, Kind.BEAM_LINK, 25, y, -17, 0);
        setup(25, y, -17, Kind.ITEMS, "nerotech:quartz_focus_lens", 1);
        link(25, y, -17, Kind.SPLITTER_LINK, 22, y, -17, 500);
        link(25, y, -17, Kind.SPLITTER_LINK, 28, y, -17, 500);
        for (int x : new int[] {22, 25, 28}) {
            link(25, y, -26, Kind.ARRAY_LINK, x, y, -40, 0);
        }
    }

    /** The small generators and the three battery cells by the entrance. */
    private void smallGenerators() {
        put(6, 1, -1, NP + "radioisotope_generator", "facing", "south");
        setup(6, 1, -1, Kind.ITEMS, NP + "isotope_pellet", 1);
        put(8, 1, -1, NP + "betavoltaic_cell");
        put(10, 1, -1, NP + "fuel_cell", "facing", "south");
        put(12, 1, -1, NP + "battery_cell_basic");
        put(13, 1, -1, NP + "battery_cell_advanced");
        put(14, 1, -1, NP + "battery_cell_elite");
    }

    private void label(double dx, double dy, double dz, String text) {
        this.labels.add(new Label(dx, dy, dz, text));
    }

    private void labels() {
        label(0, 3, -3, "NeroPower Station - follow the grey walk north");
        label(-11, 9, -11, "Unit 1 - 7x7x7 reactor, steam mode");
        label(-7, 5, -11, "Fission Core - makes steam, not power");
        label(-7, 3, -11, "Control Computer - automatic SCRAM");
        label(-11, 7, -5, "Steam Turbine - 3 vane sets, 3 stator rings");
        label(-8, 5, -5, "Turbine Helm - rotor speed and trip");
        label(-13, 9, -6, "Steam Condenser - exhaust back to water");
        label(-6, 5, -5, "Generator Breaker and Battery Bank");
        label(-11, 7, -24, "Unit 2 - breeder reactor, 5x5x5");
        label(-8, 7, -24, "Breeder Blankets turn Pallurite into Kindled Pellets");
        label(-8, 5, -26, "Stirling Generator - reactor heat to power");
        label(-12, 4, -38, "Spent Fuel Rack - hot rods cool under water");
        label(-6, 3, -38, "Vitrifier - waste and sand to glass");
        label(6, 4, -38, "Dry Casks - sealed behind Shielding Glass");
        label(6, 3, -42, "Dry Casks - Vitrified Waste, safe to open");
        label(7, 3, -35, "Shielding Block and Shielding Glass stop radiation");
        label(11, 3, -11, "Storage Ring - 23 coils, kept cold");
        label(11, 3, -7, "Power Monitor - one store, one graph");
        label(17, 3, -12, "Surge Bank - small and very fast");
        label(17, 3, -9, "Momentum Wheel - loses a little every second");
        label(8, 5, -20, "Circuit Breakers - calm, strained, tripped");
        label(10, 5, -20, "Load Banks - a set load");
        label(14, 4, -20, "Breaker front: closed and tripped");
        label(9, 4, -26, "Load Bank - burns power into heat");
        label(10, 4, -26, "Stirling - gets less than half back");
        label(28, 6, -2, "Beam Transmitter");
        label(25, 6, -2, "Beam Relay - bends the beam north");
        label(25, 6, -17, "Beam Splitter - 50 / 50 through a lens");
        label(25, 6, -26, "Phased Array Emitter - three receivers in turn");
        label(25, 6, -40, "Beam Receivers");
        label(23, 3, -6, "Orbital Receiver - space dimensions only");
        label(6, 3, -1, "Radioisotope Generator");
        label(8, 3, -1, "Betavoltaic Cell");
        label(10, 3, -1, "Hydrogen Fuel Cell");
        label(13, 3, -1, "Battery Cells - basic, advanced, elite");
    }

    private void stop(double dx, double dy, double dz, float yaw, float pitch) {
        this.tour.add(new TourStop(dx, dy, dz, yaw, pitch,
                "command.neropower.gallery.tour." + (this.tour.size() + 1)));
    }

    private void tour() {
        stop(0.5, 1, 1.5, 180, 0);
        stop(-3.5, 3, -11.5, 90, -5);
        stop(-10.5, 2, 0.5, 180, 0);
        stop(-13.5, 8, -2.5, 180, 30);
        stop(-3.5, 3, -4.5, 90, 0);
        stop(-4.5, 3, -23.5, 90, 0);
        stop(-12.5, 4, -33.5, 180, 25);
        stop(-5.5, 2, -35.5, 180, 10);
        stop(6.5, 2, -33.5, 180, 0);
        stop(11.5, 6, -4.5, 180, 40);
        stop(14.5, 2, -10.5, -90, 0);
        stop(9.5, 2, -16.5, 180, 0);
        stop(9.5, 3, -23.5, 180, 5);
        stop(19.5, 5, -1.5, -120, -5);
        stop(19.5, 5, -33.5, -90, -5);
        stop(4.5, 9, 3.5, 150, 20);
    }
}

package za.co.neroland.neropower.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.multiblock.Cell;
import za.co.neroland.neropower.multiblock.ReactorShape;
import za.co.neroland.neropower.multiblock.ShapeResult;
import za.co.neroland.neropower.multiblock.SmesRingShape;
import za.co.neroland.neropower.multiblock.TurbineShape;

/** The gallery plan: bounding box, clear coverage, the tour, sealed casks, formed shapes and no credit paths. */
class GalleryPlanTest {

    private static final GalleryPlan PLAN = GalleryPlan.create(true);

    private static Map<String, String> blocks() {
        Map<String, String> m = new HashMap<>();
        for (GalleryPlan.Place p : PLAN.places()) {
            m.put(p.dx() + "," + p.dy() + "," + p.dz(), p.id());
        }
        return m;
    }

    @Test
    void everythingLiesInsideTheBuildBox() {
        for (GalleryPlan.Place p : PLAN.places()) {
            assertTrue(p.dx() >= GalleryPlan.BUILD_MIN_DX && p.dx() <= GalleryPlan.BUILD_MAX_DX
                    && p.dy() >= 0 && p.dy() <= GalleryPlan.BUILD_MAX_DY
                    && p.dz() >= GalleryPlan.BUILD_MIN_DZ && p.dz() <= GalleryPlan.BUILD_MAX_DZ, p.toString());
        }
        for (GalleryPlan.Label l : PLAN.labelList()) {
            assertTrue(l.dx() >= GalleryPlan.BUILD_MIN_DX && l.dx() <= GalleryPlan.BUILD_MAX_DX
                    && l.dy() >= 0 && l.dy() <= GalleryPlan.BUILD_MAX_DY
                    && l.dz() >= GalleryPlan.BUILD_MIN_DZ && l.dz() <= GalleryPlan.BUILD_MAX_DZ, l.text());
        }
    }

    @Test
    void clearCoversEverythingBuiltWithAMargin() {
        assertTrue(GalleryPlan.CLEAR_MIN_DX < GalleryPlan.BUILD_MIN_DX && GalleryPlan.CLEAR_MAX_DX > GalleryPlan.BUILD_MAX_DX);
        assertTrue(GalleryPlan.CLEAR_MIN_DZ < GalleryPlan.BUILD_MIN_DZ && GalleryPlan.CLEAR_MAX_DZ > GalleryPlan.BUILD_MAX_DZ);
        assertTrue(GalleryPlan.CLEAR_MAX_DY > GalleryPlan.BUILD_MAX_DY);
        for (GalleryPlan.Place p : PLAN.places()) {
            assertTrue(GalleryPlan.inClearBox(p.dx(), p.dy(), p.dz()), p.toString());
        }
        // Labels stand up to 2 blocks tall: the entity sweep reaches 4 above the box.
        for (GalleryPlan.Label l : PLAN.labelList()) {
            assertTrue(GalleryPlan.inClearBox(l.dx(), l.dy(), l.dz()), l.text());
        }
    }

    @Test
    void clearRemovesOnlyWhatTheGalleryPlaced() {
        assertTrue(GalleryPlan.removable(true, false, false), "a planned plain block still in place");
        assertTrue(GalleryPlan.removable(true, true, true), "a planned machine still an exhibit");
        assertFalse(GalleryPlan.removable(false, false, false), "a block someone built there since");
        assertFalse(GalleryPlan.removable(true, true, false), "the same machine, but a player's (no exhibit flag)");
        assertFalse(GalleryPlan.removable(false, true, true));
    }

    @Test
    void anUnknownOriginIsFoundFromTheExhibitsOrNotAtAll() {
        Set<String> exhibits = new HashSet<>();
        for (GalleryPlan.Controller c : PLAN.controllerList()) {
            exhibits.add(c.dx() + "," + c.dy() + "," + c.dz());
        }
        assertTrue(PLAN.controllerList().size() >= 2, "more than one controller confirms an origin");
        assertTrue(PLAN.originConfirmed(o -> exhibits.contains(o[0] + "," + o[1] + "," + o[2])), "the real origin");
        assertFalse(PLAN.originConfirmed(o -> false), "no exhibits: refuse, never guess");
        GalleryPlan.Controller first = PLAN.controllerList().get(0);
        assertFalse(PLAN.originConfirmed(o -> o[0] == first.dx() && o[1] == first.dy() && o[2] == first.dz()),
                "one exhibit alone (say a single player-built machine marked by an old build) is not a gallery");
        // shifted by one block: the controllers do not line up
        assertFalse(PLAN.originConfirmed(o -> exhibits.contains((o[0] + 1) + "," + o[1] + "," + o[2])));
    }

    @Test
    void buildMessageGivesTheBoxSize() {
        int[] size = GalleryPlan.clearBoxSize();
        assertEquals(63, size[0]);
        assertEquals(13, size[1]);
        assertEquals(51, size[2]);
    }

    @Test
    void clearNeverFallsBackToTheOperatorsFeet() throws IOException {
        String cmd = Files.readString(root().resolve(
                "common/src/main/java/za/co/neroland/neropower/command/NeroPowerCommands.java"), StandardCharsets.UTF_8);
        assertFalse(cmd.contains("getOrDefault(level.dimension(), player.blockPosition())"));
        assertTrue(cmd.contains("command.neropower.gallery.clear.none"), "refuses with a message");
        assertTrue(cmd.contains("command.neropower.gallery.exists"), "one gallery per dimension");
        assertTrue(cmd.contains("o.server() != level.getServer()"), "an origin from another world is never used");
    }

    @Test
    void tourHasSixteenStopsInsideTheBoxWithEnglishCaptions() throws IOException {
        assertEquals(16, PLAN.tourStops().size());
        String lang = Files.readString(root().resolve(
                "common/src/main/resources/assets/neropower/lang/en_us.json"), StandardCharsets.UTF_8);
        Set<String> keys = new HashSet<>();
        for (GalleryPlan.TourStop s : PLAN.tourStops()) {
            assertTrue(GalleryPlan.inClearBox(s.dx(), s.dy(), s.dz()), s.captionKey());
            assertTrue(keys.add(s.captionKey()));
            assertTrue(lang.contains("\"" + s.captionKey() + "\""), s.captionKey());
        }
        for (String k : new String[] {"tour.next", "tour.end", "tour.none", "not_loaded", "not_formed", "not_sealed",
                "exists", "box", "clear.none"}) {
            assertTrue(lang.contains("\"command.neropower.gallery." + k + "\""), k);
        }
    }

    @Test
    void sealedCasksAreBoxedInByShieldingAndOpenCasksHoldOnlyVitrifiedWaste() {
        Map<String, String> b = blocks();
        int sealed = 0;
        for (GalleryPlan.Setup s : PLAN.setupList()) {
            if (s.kind() == GalleryPlan.Kind.SEALED) {
                sealed++;
                int[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] f : faces) {
                    String id = b.get((s.dx() + f[0]) + "," + (s.dy() + f[1]) + "," + (s.dz() + f[2]));
                    assertTrue("neropower:shielding_block".equals(id) || "neropower:shielding_glass".equals(id),
                            "cask face " + f[0] + f[1] + f[2] + " is " + id);
                }
            }
        }
        assertEquals(3, sealed);
        for (GalleryPlan.Setup s : PLAN.setupList()) {
            boolean cask = "neropower:dry_cask".equals(b.get(s.dx() + "," + s.dy() + "," + s.dz()));
            if (cask && s.kind() == GalleryPlan.Kind.ITEMS && !s.arg().equals("neropower:vitrified_waste")) {
                assertTrue(PLAN.setupList().stream().anyMatch(o -> o.kind() == GalleryPlan.Kind.SEALED
                        && o.dx() == s.dx() && o.dy() == s.dy() && o.dz() == s.dz()), "an open cask holds " + s.arg());
            }
        }
    }

    /** A probe over the plan's block ids. */
    private static Cell cell(Map<String, String> b, int x, int y, int z) {
        String id = b.get(x + "," + y + "," + z);
        if (id == null || id.equals("minecraft:air")) {
            return Cell.AIR;
        }
        return switch (id) {
            case "neropower:fission_casing" -> Cell.FISSION_CASING;
            case "neropower:fission_core" -> Cell.FISSION_CORE;
            case "neropower:control_rod_assembly" -> Cell.CONTROL_ROD;
            case "neropower:europium_control_rod_assembly" -> Cell.EUROPIUM_CONTROL_ROD;
            case "neropower:graphite_moderator" -> Cell.GRAPHITE_MODERATOR;
            case "neropower:moderator_tank" -> Cell.MODERATOR_TANK;
            case "neropower:breeder_blanket" -> Cell.BREEDER_BLANKET;
            case "neropower:reactor_steam_port" -> Cell.REACTOR_STEAM_PORT;
            case "neropower:reactor_control_computer" -> Cell.REACTOR_CONTROL_COMPUTER;
            case "neropower:turbine_helm" -> Cell.TURBINE_HELM;
            case "neropower:turbine_shroud" -> Cell.TURBINE_SHROUD;
            case "neropower:turbine_spindle" -> Cell.TURBINE_SPINDLE;
            case "neropower:turbine_stator" -> Cell.TURBINE_STATOR;
            case "neropower:turbine_steam_port" -> Cell.TURBINE_STEAM_PORT;
            case "neropower:smes_controller" -> Cell.SMES_CONTROLLER;
            case "neropower:smes_coil" -> Cell.SMES_COIL;
            default -> Cell.OTHER;
        };
    }

    @Test
    void bothReactorsTheTurbineAndTheRingForm() {
        Map<String, String> b = blocks();
        ShapeResult unit1 = ReactorShape.evaluate((x, y, z) -> cell(b, x, y, z), -8, 4, -11, 1, 0, 0);
        assertTrue(unit1.valid(), "unit 1");
        assertEquals(7, unit1.size());
        ShapeResult unit2 = ReactorShape.evaluate((x, y, z) -> cell(b, x, y, z), -9, 3, -24, 1, 0, 0);
        assertTrue(unit2.valid(), "unit 2");
        assertEquals(5, unit2.size());
        ShapeResult turbine = TurbineShape.evaluate((x, y, z) -> cell(b, x, y, z), -8, 3, -5, -1, 0, 0);
        assertTrue(turbine.valid(), "turbine");
        ShapeResult ring = SmesRingShape.evaluate((x, y, z) -> cell(b, x, y, z), 11, 1, -8);
        assertTrue(ring.valid(), "storage ring");
    }

    @Test
    void theGalleryCreditsNothingAndRecordsNoPlayer() throws IOException {
        StringBuilder src = new StringBuilder();
        for (String f : new String[] {"GalleryPlan", "GalleryStation", "GalleryTasks", "NeroPowerCommands"}) {
            src.append(Files.readString(root().resolve(
                    "common/src/main/java/za/co/neroland/neropower/command/" + f + ".java"), StandardCharsets.UTF_8));
        }
        String s = src.toString();
        for (String banned : new String[] {"MilestoneGates", "ProgressionGates", "OperatorCredit", "setLastOperator",
                "awardStat", "setOwner(", "getUUID()", ".add(chunk", "ThresholdEvents", "FissionWorldLog"}) {
            assertFalse(s.contains(banned), "gallery source uses " + banned);
        }
        assertTrue(s.contains("Exhibits.mark(be)"), "every machine is marked an exhibit");
        assertTrue(s.replaceAll("\\s+", " ").contains("NeroPowerCommands.GALLERY_LINK_TOKEN"), "beams link under the token");
    }

    private static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "repository root not found");
        return dir;
    }
}

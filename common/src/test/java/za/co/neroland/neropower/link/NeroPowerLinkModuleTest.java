package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.event.ThresholdEvents;
import za.co.neroland.nerolandcore.link.LinkActionHandler;
import za.co.neroland.nerolandcore.link.LinkActionResult;
import za.co.neroland.nerolandcore.link.LinkEvent;
import za.co.neroland.nerolandcore.link.LinkModuleInfo;
import za.co.neroland.nerolandcore.link.LinkSnapshotProvider;
import za.co.neroland.nerolandcore.link.NeroLinkRegistry;

import za.co.neroland.nerotech.api.MachineFailureEvents;

/**
 * Plain-JVM tests for NeroPower's NeroLink module, mirroring NeroTech's: no game bootstrap and no
 * running server, so every assertion exercises a path that resolves deterministically without the
 * captured server — registration / discovery, the no-server (offline requester) branch of every plant
 * section, pre-server action validation, the failure-event forwarding filter, and the pure scoping
 * rules in {@link LinkScope}. Schema-2 shape, scope and rate limits: {@link LinkSchema2Test},
 * {@link LinkScopeTest}, {@link LinkRateLimitTest}. Chunk enumeration and the owner-gated actions run
 * only in-game and are covered by runtime verification.
 */
class NeroPowerLinkModuleTest {

    private static final List<String> PLANT_SECTIONS = List.of("fission", "turbines", "storage", "beams",
            "generators");

    @BeforeAll
    static void registerModule() {
        NeroPowerLinkModule.register();
    }

    private static LinkSnapshotProvider provider() {
        return NeroLinkRegistry.snapshotProvider("neropower").orElseThrow();
    }

    private static LinkActionHandler handler() {
        return NeroLinkRegistry.actionHandler("neropower").orElseThrow();
    }

    // --- discovery ----------------------------------------------------------------------------

    @Test
    void registersDiscoveryMetadataWithExpectedSectionsAndActions() {
        LinkModuleInfo info = NeroLinkRegistry.module("neropower").orElseThrow();
        assertEquals("neropower", info.moduleId());
        assertEquals(2, info.schemaVersion());
        assertTrue(info.dataSections().containsAll(PLANT_SECTIONS), "all plant sections must be advertised");
        assertTrue(info.dataSections().contains("catalogue"));
        assertTrue(info.actionIds().containsAll(List.of("acknowledge_alarm", "scram", "turbine_trip")));
        assertEquals(provider().sections(), info.dataSections());
        assertEquals(handler().actionIds(), info.actionIds());
        assertFalse(info.modVersion().isBlank());
    }

    @Test
    void registerIsIdempotent() {
        NeroPowerLinkModule.register();
        assertTrue(NeroLinkRegistry.module("neropower").isPresent());
    }

    // --- snapshot sections without a server: never a roster ---------------------------------------

    @Test
    void everyPlantSectionIsEmptyWithNoteWhenRequesterCannotBeResolved() {
        for (String section : PLANT_SECTIONS) {
            UUID requester = UUID.randomUUID();
            JsonObject out = provider().snapshot(requester, section, Map.of());
            assertEquals(section, out.get("section").getAsString());
            assertEquals("proximity", out.get("scope").getAsString());
            assertEquals(LinkScope.PROXIMITY_BLOCKS, out.get("radius").getAsInt());
            assertEquals(0, out.getAsJsonArray("machines").size(), section + " must list nothing");
            assertTrue(out.has("note"), section + " must explain the proximity posture");
            assertTrue(out.has("asOf"));
            assertFalse(out.has("dim"), "no dimension is ever named");
            assertFalse(NeroPowerLinkModule.HANDLES.holds(requester), "no handles for an offline requester");
        }
    }

    @Test
    void unknownSectionAnswersAsUnknown() {
        JsonObject out = provider().snapshot(UUID.randomUUID(), "roster", Map.of());
        assertEquals("roster", out.get("section").getAsString());
        assertTrue(out.has("note"));
        assertFalse(out.has("machines"));
    }

    // --- actions without a server ----------------------------------------------------------------

    @Test
    void unknownActionIsValidationError() {
        LinkActionResult result = handler().execute(UUID.randomUUID(), "bogus_action", new JsonObject());
        assertFalse(result.ok());
        assertEquals(LinkActionResult.Error.VALIDATION, result.error());
    }

    @Test
    void actionsWithoutAHandleAreRefusedBeforeTheWorldIsTouched() {
        for (String action : handler().actionIds()) {
            LinkActionResult result = handler().execute(UUID.randomUUID(), action, new JsonObject());
            assertFalse(result.ok());
            assertEquals(LinkActionResult.Error.VALIDATION, result.error());
            assertEquals("h is required", result.message());
        }
    }

    @Test
    void actionsAreOnlineOnlyAndFailInternalWithoutAServer() {
        JsonObject params = new JsonObject();
        params.addProperty("h", "abc-0");
        for (String action : handler().actionIds()) {
            assertFalse(handler().allowOffline(action), action + " changes world state: online only");
            LinkActionResult result = handler().execute(UUID.randomUUID(), action, params);
            assertFalse(result.ok());
            assertEquals(LinkActionResult.Error.INTERNAL, result.error(), action + " without a server");
        }
    }

    // --- failure-event forwarding ------------------------------------------------------------------

    private static List<LinkEvent> capture(Runnable body) {
        List<LinkEvent> seen = new ArrayList<>();
        Consumer<LinkEvent> subscriber = seen::add;
        NeroLinkRegistry.eventBus().subscribe(subscriber);
        try {
            body.run();
        } finally {
            NeroLinkRegistry.eventBus().unsubscribe(subscriber);
        }
        return seen;
    }

    @Test
    void busCrossingsAreNotForwarded() {
        // The module does not listen on NeroTech's failure channel: FailureTelegraph hands it the
        // machine directly, because the bus scope carries no position to resolve recipients from.
        List<LinkEvent> seen = capture(() -> {
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(MachineFailureEvents.CHANNEL,
                    "neropower:fission_core@minecraft:overworld", MachineFailureEvents.STAGE_UNSTABLE,
                    MachineFailureEvents.STAGE_UNSTABLE, true));
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                    Identifier.parse("nerotech:pollution"), "neropower:not_a_failure", 1, 1, true));
        });
        assertTrue(seen.stream().noneMatch(e -> "neropower".equals(e.moduleId())));
    }

    @Test
    void failureBodyCarriesTypeStageAndDirectionOnly() {
        JsonObject body = PlantEvents.body("neropower:fission_core", MachineFailureEvents.STAGE_UNSTABLE, true);
        assertEquals(List.of("type", "machineId", "stage", "rising"), new ArrayList<>(body.keySet()));
        assertEquals("neropower:fission_core", body.get("machineId").getAsString(), "schema-1 name kept");
        assertFalse(body.toString().contains("minecraft:"), "no dimension, no place");
    }

    // --- LinkScope: the pure rules ------------------------------------------------------------------

    @Test
    void proximityIsDecidedOnTheChunkGrid() {
        assertTrue(LinkScope.withinProximity(0, 64, 0, 8 * 16 + 15, 64, 0), "the eighth chunk counts, all of it");
        assertFalse(LinkScope.withinProximity(0, 64, 0, 9 * 16, 64, 0), "the ninth chunk does not");
        assertTrue(LinkScope.withinProximity(0, 64, 0, 100, 64, 100), "Chebyshev: a diagonal chunk inside the square");
        assertTrue(LinkScope.withinProximity(0, 64, 0, 0, -60, 0), "no height edge");
        assertTrue(LinkScope.withinProximity(-1000, 0, -1000, -1100, 20, -1050));
        assertFalse(LinkScope.withinProximity(-1, 64, 0, 8 * 16, 64, 0), "chunk -1 to chunk 8 is nine chunks");
    }

    @Test
    void chunkRadiusCoversTheProximityRadius() {
        assertEquals(8, LinkScope.chunkRadius());
        assertTrue(LinkScope.chunkRadius() * 16 >= LinkScope.PROXIMITY_BLOCKS);
    }

    @Test
    void ownedMachinesAreVisibleToTheirOwnerOnlyAndUnownedByProximity() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(LinkScope.visibleTo(Optional.empty(), other), "unowned: proximity alone");
        assertTrue(LinkScope.visibleTo(Optional.of(owner), owner));
        assertFalse(LinkScope.visibleTo(Optional.of(owner), other), "someone else's reactor is invisible");
    }

    @Test
    void ownedMachinesObeyTheirOwnerOnly() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(LinkScope.mayAct(Optional.of(owner), owner), "the owner may act from anywhere in scope");
        assertFalse(LinkScope.mayAct(Optional.of(owner), other), "a nearby player is still not the owner");
    }

    @Test
    void unownedMachinesAcceptNoRemoteAction() {
        UUID requester = UUID.randomUUID();
        assertFalse(LinkScope.mayAct(Optional.empty(), requester), "unowned: in person only (screen, SCRAM Key)");
        assertFalse(LinkScope.mayAct(Optional.empty(), null), "no requester");
    }
}

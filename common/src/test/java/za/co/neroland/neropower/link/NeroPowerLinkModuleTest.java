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
 * captured server — registration / discovery, the no-server (offline requester) branch of every
 * section, pre-server action validation, the failure-event forwarding filter and payload, and the
 * pure scoping rules in {@link LinkScope}. Chunk enumeration and the owner-gated actions run only
 * in-game and are covered by runtime verification.
 */
class NeroPowerLinkModuleTest {

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
        assertEquals(1, info.schemaVersion());
        assertTrue(info.dataSections().containsAll(List.of("beams", "fission", "storage", "generators")),
                "all four data sections must be advertised");
        assertTrue(info.actionIds().containsAll(List.of("acknowledge_alarm", "scram")),
                "both actions must be advertised");
        assertEquals(provider().sections(), info.dataSections());
        assertEquals(handler().actionIds(), info.actionIds());
    }

    @Test
    void registerIsIdempotent() {
        NeroPowerLinkModule.register();
        assertTrue(NeroLinkRegistry.module("neropower").isPresent());
    }

    // --- snapshot sections without a server: never a roster ---------------------------------------

    @Test
    void everySectionIsEmptyWithNoteWhenRequesterCannotBeResolved() {
        // No server captured ⇒ the requester is not an online player ⇒ nothing is enumerated, and the
        // section says so. This is the same shape a non-owner far from any machine sees in-game.
        for (String section : provider().sections()) {
            JsonObject out = provider().snapshot(UUID.randomUUID(), section, Map.of());
            assertEquals(section, out.get("section").getAsString());
            assertEquals("proximity", out.get("scope").getAsString());
            assertEquals(LinkScope.PROXIMITY_BLOCKS, out.get("radius").getAsInt());
            assertEquals(0, out.getAsJsonArray("machines").size(), section + " must list nothing");
            assertTrue(out.has("note"), section + " must explain the proximity posture");
            assertTrue(out.has("asOf"));
            assertFalse(out.has("dim"), "no dimension may be named when the requester is not online");
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
    void actionsAreOnlineOnlyAndFailInternalWithoutAServer() {
        for (String action : handler().actionIds()) {
            assertFalse(handler().allowOffline(action), action + " changes world state: online only");
            LinkActionResult result = handler().execute(UUID.randomUUID(), action, new JsonObject());
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
    void failureCrossingsAreNeverBroadcastAndDropWithoutAServer() {
        // No captured server ⇒ neither an owner nor a nearby online player can be resolved ⇒ the
        // event is dropped. In-game it goes player-targeted to the owner or nearby players only.
        String scope = "neropower:fission_core@minecraft:overworld:12345";
        List<LinkEvent> seen = capture(() -> ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                MachineFailureEvents.CHANNEL, scope, MachineFailureEvents.STAGE_UNSTABLE,
                MachineFailureEvents.STAGE_UNSTABLE, true)));
        List<LinkEvent> ours = seen.stream().filter(e -> "neropower".equals(e.moduleId())).toList();
        assertTrue(ours.isEmpty(), "no recipients resolvable ⇒ nothing published");
        assertTrue(seen.stream().filter(e -> "neropower".equals(e.moduleId())).noneMatch(LinkEvent::isBroadcast));
    }

    @Test
    void failurePayloadCarriesScopeStageAndDirection() {
        String scope = "neropower:fission_core@minecraft:overworld:12345";
        JsonObject payload = NeroPowerLinkModule.failurePayload(new ThresholdEvents.ThresholdCrossing(
                MachineFailureEvents.CHANNEL, scope, MachineFailureEvents.STAGE_UNSTABLE,
                MachineFailureEvents.STAGE_UNSTABLE, true));
        assertEquals(scope, payload.get("scope").getAsString());
        assertEquals("neropower:fission_core", payload.get("machineId").getAsString());
        assertEquals(MachineFailureEvents.STAGE_UNSTABLE, payload.get("stage").getAsInt());
        assertTrue(payload.get("rising").getAsBoolean());
    }

    @Test
    void otherPublishersAndChannelsAreIgnored() {
        List<LinkEvent> seen = capture(() -> {
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(MachineFailureEvents.CHANNEL,
                    "nerotech:fusion_reactor@minecraft:overworld:1", 3, 3, true));
            ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                    Identifier.parse("nerotech:pollution"), "neropower:not_a_failure", 1, 1, true));
        });
        assertTrue(seen.stream().noneMatch(e -> "neropower".equals(e.moduleId())),
                "only NeroPower machines on the failure channel are forwarded");
    }

    @Test
    void failurePayloadCarriesNoPersonalFields() {
        JsonObject payload = NeroPowerLinkModule.failurePayload(new ThresholdEvents.ThresholdCrossing(
                MachineFailureEvents.CHANNEL, "neropower:fission_core@minecraft:the_nether:7", 1, 1, false));
        assertEquals(List.of("scope", "machineId", "stage", "rising"), new ArrayList<>(payload.keySet()));
        assertFalse(payload.get("rising").getAsBoolean());
    }

    // --- LinkScope: the pure rules ------------------------------------------------------------------

    @Test
    void proximityIsEuclideanAndInclusive() {
        assertTrue(LinkScope.withinProximity(0, 64, 0, 128, 64, 0), "exactly on the radius counts");
        assertFalse(LinkScope.withinProximity(0, 64, 0, 129, 64, 0));
        assertFalse(LinkScope.withinProximity(0, 64, 0, 100, 64, 100), "diagonal beyond the radius");
        assertTrue(LinkScope.withinProximity(0, 64, 0, 0, 64 + 128, 0), "vertical distance counts too");
        assertTrue(LinkScope.withinProximity(-1000, 0, -1000, -1100, 20, -1050));
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
    void isOwnerNeedsARecordedMatchingOwner() {
        UUID owner = UUID.randomUUID();
        assertTrue(LinkScope.isOwner(Optional.of(owner), owner));
        assertFalse(LinkScope.isOwner(Optional.of(owner), UUID.randomUUID()));
        assertFalse(LinkScope.isOwner(Optional.empty(), owner));
    }

    @Test
    void ownedMachinesObeyTheirOwnerOnlyWhereverTheyStand() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertTrue(LinkScope.mayAct(Optional.of(owner), owner, false, false), "owner from anywhere");
        assertTrue(LinkScope.mayAct(Optional.of(owner), owner, true, true));
        assertFalse(LinkScope.mayAct(Optional.of(owner), other, true, true),
                "a nearby player who may interact is still not the owner");
    }

    @Test
    void unownedMachinesObeyANearbyPlayerWhoMayInteract() {
        UUID requester = UUID.randomUUID();
        assertTrue(LinkScope.mayAct(Optional.empty(), requester, true, true));
        assertFalse(LinkScope.mayAct(Optional.empty(), requester, false, true), "too far / other dimension");
        assertFalse(LinkScope.mayAct(Optional.empty(), requester, true, false), "protection refuses");
        assertFalse(LinkScope.mayAct(Optional.empty(), requester, false, false));
        assertFalse(LinkScope.mayAct(Optional.empty(), null, true, true), "no requester");
    }

    @Test
    void scopeDimensionAndPositionAreParsed() {
        String scope = "neropower:fission_core@minecraft:the_nether:-274877906944";
        assertEquals("minecraft:the_nether", LinkScope.dimensionOf(scope));
        assertEquals(-274877906944L, LinkScope.packedPosOf(scope).orElseThrow());
        assertEquals("nerospace:moon", LinkScope.dimensionOf("neropower:beam_relay@nerospace:moon:7"));
        assertEquals(7L, LinkScope.packedPosOf("neropower:beam_relay@nerospace:moon:7").orElseThrow());
        for (String bad : new String[] {null, "", "neropower:fission_core", "neropower:x@:5",
                "neropower:x@minecraft:overworld", "neropower:x@minecraft:overworld:", "neropower:x@minecraft:overworld:abc"}) {
            assertEquals("", LinkScope.dimensionOf(bad), String.valueOf(bad));
            assertTrue(LinkScope.packedPosOf(bad).isEmpty(), String.valueOf(bad));
        }
    }

    @Test
    void scopeStringsAreParsedByPrefix() {
        assertTrue(LinkScope.isNeroPowerScope("neropower:fission_core@minecraft:overworld:1"));
        assertFalse(LinkScope.isNeroPowerScope("nerotech:fusion_reactor@minecraft:overworld:1"));
        assertFalse(LinkScope.isNeroPowerScope(null));
        assertEquals("neropower:fission_core", LinkScope.machineIdOf("neropower:fission_core@minecraft:overworld:1"));
        assertEquals("neropower:fission_core", LinkScope.machineIdOf("neropower:fission_core"));
        assertEquals("", LinkScope.machineIdOf(null));
    }
}

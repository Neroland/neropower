package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkEvent;

import za.co.neroland.nerotech.api.MachineFailureEvents;

/**
 * Link schema 2 scope: a player out of range reads nothing, a second player learns nothing about the
 * first's owned plant, an expired or foreign handle fails exactly like an unknown one, and failure
 * events reach only the players who could see the machine, with a push only for the recorded owner.
 */
class LinkScopeTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static JsonObject entry(String type) {
        JsonObject e = new JsonObject();
        e.addProperty("type", type);
        e.addProperty("state", "working");
        return e;
    }

    private static PlantScope.Candidate<String> machine(String target, int x, Optional<UUID> owner) {
        return new PlantScope.Candidate<>(target, OVERWORLD, x, 64, 0, owner, entry("neropower:fission_core"));
    }

    // --- reads -----------------------------------------------------------------------------

    @Test
    void playerOutOfRangeReadsNothing() {
        UUID me = UUID.randomUUID();
        List<PlantScope.Candidate<String>> found = List.of(machine("edge", 8 * 16 + 15, Optional.empty()),
                machine("beyond", 9 * 16, Optional.empty()));
        PlantScope.Selection<String> near = PlantScope.select(new PlantScope.Viewer(me, OVERWORLD, 0, 64, 0),
                found, new Random(3));
        assertEquals(List.of("edge"), near.targets(), "the eighth chunk counts, the ninth does not");

        PlantScope.Selection<String> far = PlantScope.select(new PlantScope.Viewer(me, OVERWORLD, -9 * 16, 64, 0),
                List.of(machine("edge", 0, Optional.empty())), new Random(3));
        assertTrue(far.entries().isEmpty(), "a player nine chunks away reads nothing");

        PlantScope.Selection<String> otherDim = PlantScope.select(
                new PlantScope.Viewer(me, "minecraft:the_nether", 0, 64, 0), found, new Random(3));
        assertTrue(otherDim.entries().isEmpty(), "another dimension reads nothing");

        assertTrue(PlantScope.select(null, found, new Random(3)).entries().isEmpty(),
                "an offline / unresolved requester reads nothing, never a roster");
    }

    @Test
    void secondPlayerLearnsNothingAboutTheFirstsOwnedReactor() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlantScope.Candidate<String> shared = machine("shared", 5, Optional.empty());
        PlantScope.Candidate<String> firsts = machine("firsts", 6, Optional.of(first));
        PlantScope.Viewer secondViewer = new PlantScope.Viewer(second, OVERWORLD, 0, 64, 0);

        PlantScope.Selection<String> withReactor = PlantScope.select(secondViewer, List.of(shared, firsts),
                new Random(7));
        PlantScope.Selection<String> withoutReactor = PlantScope.select(secondViewer, List.of(shared),
                new Random(7));
        assertEquals(withoutReactor.entries(), withReactor.entries(),
                "the first player's owned reactor changes nothing in the second player's answer");
        assertEquals(withoutReactor.targets(), withReactor.targets());
        assertFalse(withReactor.entries().get(0).get("ownedByYou").getAsBoolean());

        PlantScope.Selection<String> mine = PlantScope.select(new PlantScope.Viewer(first, OVERWORLD, 0, 64, 0),
                List.of(shared, firsts), new Random(7));
        assertEquals(2, mine.entries().size(), "the owner sees their own reactor");
        long ownedCount = mine.entries().stream().filter(e -> e.get("ownedByYou").getAsBoolean()).count();
        assertEquals(1, ownedCount);
    }

    @Test
    void sectionsAreShuffledAndCapped() {
        UUID me = UUID.randomUUID();
        List<PlantScope.Candidate<String>> found = new ArrayList<>();
        for (int i = 0; i < PlantScope.MAX_ENTRIES + 10; i++) {
            found.add(machine("m" + i, i % 100, Optional.empty()));
        }
        PlantScope.Selection<String> sel = PlantScope.select(new PlantScope.Viewer(me, OVERWORLD, 0, 64, 0),
                found, new Random(11));
        assertEquals(PlantScope.MAX_ENTRIES, sel.entries().size());
        assertTrue(sel.truncated());
    }

    // --- handles -----------------------------------------------------------------------------

    @Test
    void expiredForeignAndUnknownHandlesFailTheSame() {
        AtomicLong now = new AtomicLong(1_000L);
        PlantHandles<String> handles = new PlantHandles<>(now::get);
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<String> issued = handles.issue(me, "fission", List.of("core-a", "core-b"));
        assertEquals(Optional.of("core-b"), handles.resolve(me, issued.get(1)));

        assertEquals(Optional.empty(), handles.resolve(other, issued.get(0)), "foreign");
        assertEquals(Optional.empty(), handles.resolve(me, "deadbeef-0"), "unknown");
        assertEquals(Optional.empty(), handles.resolve(me, issued.get(0) + "0"), "malformed index");
        assertEquals(Optional.empty(), handles.resolve(me, null));
        now.addAndGet(PlantHandles.TTL_MS + 1);
        assertEquals(Optional.empty(), handles.resolve(me, issued.get(0)), "expired");
    }

    @Test
    void handlesAreKeptPerSectionAndReplacedOnTheNextAnswer() {
        AtomicLong now = new AtomicLong(0L);
        PlantHandles<String> handles = new PlantHandles<>(now::get);
        UUID me = UUID.randomUUID();
        String core = handles.issue(me, "fission", List.of("core")).get(0);
        String turbine = handles.issue(me, "turbines", List.of("turbine")).get(0);
        assertEquals(Optional.of("core"), handles.resolve(me, core), "reading turbines keeps fission handles");
        assertEquals(Optional.of("turbine"), handles.resolve(me, turbine));
        handles.issue(me, "fission", List.of("core"));
        assertEquals(Optional.empty(), handles.resolve(me, core), "superseded by the next fission answer");
        handles.forget(me);
        assertFalse(handles.holds(me));
        assertEquals(Optional.empty(), handles.resolve(me, turbine));
    }

    @Test
    void outOfScopeAtExecutionReadsLikeAnUnknownHandle() {
        UUID me = UUID.randomUUID();
        PlantScope.Viewer moved = new PlantScope.Viewer(me, OVERWORLD, 500, 64, 0);
        assertFalse(PlantScope.inScope(moved, OVERWORLD, 0, 64, 0, Optional.empty()));
        assertFalse(PlantScope.inScope(new PlantScope.Viewer(me, OVERWORLD, 0, 64, 0), OVERWORLD, 0, 64, 0,
                Optional.of(UUID.randomUUID())), "someone else's machine is never in scope");
        assertTrue(PlantScope.inScope(new PlantScope.Viewer(me, OVERWORLD, 0, 64, 0), OVERWORLD, 0, 64, 0,
                Optional.of(me)));
    }

    // --- events ------------------------------------------------------------------------------

    @Test
    void failureEventsReachOnlyPlayersInRangeAndPushOnlyTheOwner() {
        UUID near = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        List<PlantEvents.Nearby> players = List.of(new PlantEvents.Nearby(near, 10, 64, 0),
                new PlantEvents.Nearby(far, 200, 64, 0));
        List<PlantEvents.Delivery> out = PlantEvents.recipients(players, 0, 64, 0, Optional.empty(), null,
                MachineFailureEvents.STAGE_FAILURE, true);
        assertEquals(List.of(new PlantEvents.Delivery(near, false)), out, "no push without a recorded owner");

        LinkEvent event = PlantEvents.event("neropower", out.get(0), "neropower:fission_core",
                MachineFailureEvents.STAGE_FAILURE, true);
        assertEquals(PlantEvents.BYSTANDER_TOPIC, event.topic());
        assertEquals(PlantEvents.BYSTANDER_VERB, event.payload().get("event").getAsString(),
                "the bridge never pushes an alerts event whose verb is not raised");
        assertFalse(event.payload().has("_notify"));
        assertFalse(PlantScope.leaks(event.payload()));
    }

    @Test
    void secondPlayerGetsNoEventForTheFirstsOwnedMachine() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        List<PlantEvents.Nearby> players = List.of(new PlantEvents.Nearby(other, 1, 64, 0));
        List<PlantEvents.Delivery> out = PlantEvents.recipients(players, 0, 64, 0, Optional.of(owner), owner,
                MachineFailureEvents.STAGE_UNSTABLE, true);
        assertEquals(List.of(new PlantEvents.Delivery(owner, true)), out,
                "the bystander learns nothing; the away owner gets the push-eligible copy");
        LinkEvent event = PlantEvents.event("neropower", out.get(0), "neropower:turbine_helm",
                MachineFailureEvents.STAGE_UNSTABLE, true);
        assertEquals(PlantEvents.TOPIC_FAILURE, event.topic());
        assertTrue(event.payload().has("_notify"));
        assertEquals(List.of("type", "machineId", "stage", "rising", "_notify"),
                new ArrayList<>(event.payload().keySet()));
    }

    @Test
    void ownerIsPushedOnlyOnARiseIntoUnstableOrFailure() {
        UUID owner = UUID.randomUUID();
        List<PlantEvents.Nearby> none = List.of();
        assertTrue(PlantEvents.recipients(none, 0, 0, 0, Optional.of(owner), owner,
                MachineFailureEvents.STAGE_WARNING, true).isEmpty(), "a warning is not pushed");
        assertTrue(PlantEvents.recipients(none, 0, 0, 0, Optional.of(owner), owner,
                MachineFailureEvents.STAGE_FAILURE, false).isEmpty(), "leaving a stage is not pushed");
        List<PlantEvents.Nearby> ownerNear = List.of(new PlantEvents.Nearby(owner, 0, 0, 0));
        assertEquals(List.of(new PlantEvents.Delivery(owner, false)), PlantEvents.recipients(ownerNear, 0, 0, 0,
                Optional.of(owner), owner, MachineFailureEvents.STAGE_WARNING, true), "a live notice only");
    }

    @Test
    void failureThrottleAllowsFiveEventsPerMachinePerMinute() {
        AtomicLong now = new AtomicLong(0L);
        FailureThrottle throttle = new FailureThrottle(now::get);
        for (int i = 0; i < FailureThrottle.MAX_EVENTS; i++) {
            assertTrue(throttle.tryFire("m"));
        }
        assertFalse(throttle.tryFire("m"), "the sixth event in a minute is dropped");
        assertTrue(throttle.tryFire("other"), "per machine");
        now.addAndGet(FailureThrottle.WINDOW_MS);
        assertTrue(throttle.tryFire("m"));
        now.addAndGet(FailureThrottle.WINDOW_MS);
        throttle.pruneExpired();
        assertEquals(0, throttle.tracked());
    }
}

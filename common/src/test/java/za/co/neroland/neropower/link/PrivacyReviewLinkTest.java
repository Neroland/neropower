package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * Privacy rules in the link module: reach on the chunk grid
 * with a coarse band as the only distance, a per-section read budget, the NeroTech owner as the fallback
 * owner, and no remote action on an unowned machine.
 */
class PrivacyReviewLinkTest {

    private static final String OVERWORLD = "minecraft:overworld";

    private static PlantScope.Candidate<String> at(String id, int x, int y, int z) {
        JsonObject e = new JsonObject();
        e.addProperty("type", "neropower:battery_bank");
        return new PlantScope.Candidate<>(id, OVERWORLD, x, y, z, Optional.empty(), e);
    }

    @Test
    void twoRequestersInTheSameChunkAlwaysGetTheSameSet() {
        List<PlantScope.Candidate<String>> found = new ArrayList<>();
        Random r = new Random(7);
        for (int i = 0; i < 40; i++) {
            found.add(at("m" + i, r.nextInt(400) - 200, r.nextInt(300) - 60, r.nextInt(400) - 200));
        }
        UUID me = UUID.randomUUID();
        for (int[] pair : new int[][] { { 0, 0, 15, 15 }, { 16, 32, 31, 47 }, { -1, -1, -16, -16 } }) {
            PlantScope.Selection<String> a = PlantScope.select(new PlantScope.Viewer(me, OVERWORLD, pair[0], 64, pair[1]),
                    found, new Random(1));
            PlantScope.Selection<String> b = PlantScope.select(new PlantScope.Viewer(me, OVERWORLD, pair[2], -40, pair[3]),
                    found, new Random(1));
            assertEquals(Set.copyOf(a.targets()), Set.copyOf(b.targets()),
                    "anywhere in one chunk, at any height, the same machines are in reach");
        }
    }

    @Test
    void theBandIsTheOnlyDistanceAndIsCoarse() {
        assertEquals(LinkScope.BAND_NEAR, LinkScope.band(0, 0, 47, -32), "two chunks: near");
        assertEquals(LinkScope.BAND_IN_RANGE, LinkScope.band(0, 0, 48, 0), "three chunks: in range");
        assertEquals(LinkScope.band(1, 1, 40, 40), LinkScope.band(14, 14, 40, 40), "same chunk, same band");
        PlantScope.Selection<String> s = PlantScope.select(new PlantScope.Viewer(UUID.randomUUID(), OVERWORLD, 0, 64, 0),
                List.of(at("near", 5, 64, 5), at("far", 100, 64, 0)), new Random(2));
        for (JsonObject entry : s.entries()) {
            assertTrue(entry.has("band"));
            for (String key : List.of("x", "y", "z", "distance", "direction", "height", "bearing")) {
                assertFalse(entry.has(key), key);
            }
        }
    }

    @Test
    void theNeroTechOwnerIsTheFallbackOwner() {
        UUID linker = UUID.randomUUID();
        UUID placer = UUID.randomUUID();
        assertEquals(Optional.of(linker), LinkScope.ownerOf(Optional.of(linker), Optional.of(placer)));
        assertEquals(Optional.of(placer), LinkScope.ownerOf(Optional.empty(), Optional.of(placer)),
                "an unlinked transmitter keeps its NeroTech owner");
        assertEquals(Optional.empty(), LinkScope.ownerOf(Optional.empty(), Optional.empty()));
        UUID stranger = UUID.randomUUID();
        assertFalse(LinkScope.visibleTo(LinkScope.ownerOf(Optional.empty(), Optional.of(placer)), stranger));
        assertFalse(LinkScope.mayAct(LinkScope.ownerOf(Optional.empty(), Optional.of(placer)), stranger));
        assertTrue(LinkScope.mayAct(LinkScope.ownerOf(Optional.empty(), Optional.of(placer)), placer));
    }

    @Test
    void readBudgetRepeatsTheLastAnswerAndForgetsOnErasure() {
        AtomicLong now = new AtomicLong(0L);
        SectionReadBudget budget = new SectionReadBudget(now::get);
        UUID me = UUID.randomUUID();
        for (int i = 0; i < SectionReadBudget.MAX_READS; i++) {
            assertTrue(budget.tryRead(me, "storage"), "read " + (i + 1));
            now.addAndGet(1_000L);
        }
        assertFalse(budget.tryRead(me, "storage"), "the seventh fresh read in a minute is refused");
        assertTrue(budget.tryRead(me, "fission"), "each section has its own budget");
        JsonObject answer = new JsonObject();
        answer.addProperty("asOf", 1L);
        budget.remember(me, "storage", answer);
        assertEquals(answer, budget.lastAnswer(me, "storage").orElseThrow());
        now.addAndGet(SectionReadBudget.WINDOW_MS);
        assertTrue(budget.tryRead(me, "storage"), "the window slides");

        budget.retainOnly(Set.of());
        assertTrue(budget.lastAnswer(me, "storage").isEmpty(), "a logged-out player's answers go");
        assertTrue(budget.holds(me), "but the window stays, so a relog does not refill it");
        budget.forget(me);
        assertFalse(budget.holds(me), "erasure leaves nothing");
    }
}

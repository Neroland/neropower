package za.co.neroland.neropower.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The in-memory part index: breaking any shell position of a 7-edge reactor marks exactly that
 * controller dirty, positions outside mark nothing, dimensions are separate, unregistering stops it, and
 * the per-dimension budget allows four revalidations per tick. Pure JVM.
 */
class MultiblockIndexTest {

    private static final String OVERWORLD = "overworld";
    private static final String NETHER = "the_nether";

    @AfterEach
    void reset() {
        MultiblockIndex.clear();
    }

    @Test
    void everyShellPositionOfASevenMarksExactlyItsController() {
        Bounds seven = new Bounds(0, 60, 0, 6, 66, 6);
        Bounds neighbour = new Bounds(20, 60, 0, 24, 64, 4);
        AtomicInteger mine = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        MultiblockIndex.register(OVERWORLD, 1L, seven, mine::incrementAndGet);
        MultiblockIndex.register(OVERWORLD, 2L, neighbour, other::incrementAndGet);
        int shell = 0;
        for (int x = 0; x <= 6; x++) {
            for (int y = 60; y <= 66; y++) {
                for (int z = 0; z <= 6; z++) {
                    if (seven.faces(x, y, z) > 0) {
                        assertEquals(1, MultiblockIndex.markDirty(OVERWORLD, x, y, z));
                        shell++;
                    }
                }
            }
        }
        assertEquals(7 * 7 * 7 - 5 * 5 * 5, shell);
        assertEquals(shell, mine.get());
        assertEquals(0, other.get());
        assertEquals(0, MultiblockIndex.markDirty(OVERWORLD, 10, 60, 0), "outside every structure");
        assertEquals(0, MultiblockIndex.markDirty(NETHER, 3, 63, 3), "another dimension");
    }

    @Test
    void unregisterStopsMarking() {
        AtomicInteger hits = new AtomicInteger();
        MultiblockIndex.register(OVERWORLD, 5L, new Bounds(0, 0, 0, 2, 2, 2), hits::incrementAndGet);
        assertTrue(MultiblockIndex.registered(OVERWORLD, 5L));
        MultiblockIndex.unregister(OVERWORLD, 5L);
        assertFalse(MultiblockIndex.registered(OVERWORLD, 5L));
        assertEquals(0, MultiblockIndex.markDirty(OVERWORLD, 1, 1, 1));
        assertEquals(0, hits.get());
    }

    @Test
    void budgetAllowsFourPerDimensionPerTick() {
        for (int i = 0; i < MultiblockIndex.REVALIDATIONS_PER_TICK; i++) {
            assertTrue(MultiblockIndex.tryRevalidate(OVERWORLD, 100L));
        }
        assertFalse(MultiblockIndex.tryRevalidate(OVERWORLD, 100L));
        assertTrue(MultiblockIndex.tryRevalidate(NETHER, 100L), "budgets are per dimension");
        assertTrue(MultiblockIndex.tryRevalidate(OVERWORLD, 101L), "and refill each tick");
    }
}

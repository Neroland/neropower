package za.co.neroland.neropower.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Shared port tanks on the plain JVM: a reactor's or turbine's ports fill and drain as one tank,
 * one fluid at a time, and pooling creates and voids nothing (every fill and drain, including a
 * transactional undo, balances to the mB).
 */
class PortPoolTest {

    private static final String WATER = "minecraft:water";
    private static final String CRYO = "nerotech:cryo_coolant";
    private static final String STEAM = "neropower:steam";

    private static long total(List<TestTank> tanks) {
        long sum = 0L;
        for (TestTank t : tanks) {
            sum += t.amount();
        }
        return sum;
    }

    @Test
    void fillsSpillAcrossPortsAndDrainsFromAny() {
        List<TestTank> pool = List.of(TestTank.of(1_000, STEAM), TestTank.of(1_000, STEAM), TestTank.of(1_000, STEAM));
        assertEquals(3_000L, PortPool.capacity(pool));
        assertEquals(2_500L, PortPool.fill(pool, STEAM, 2_500L, false));
        assertEquals(2_500L, PortPool.amount(pool));
        assertEquals(500L, PortPool.roomFor(pool, STEAM));
        assertEquals(1_000L, pool.get(0).amount());
        assertEquals(1_000L, pool.get(1).amount());
        assertEquals(500L, pool.get(2).amount());
        // Draining through "the third port" takes from the first tanks just the same: one tank.
        assertEquals(2_000L, PortPool.drain(pool, 2_000L, false));
        assertEquals(500L, total(pool));
        assertEquals(0L, PortPool.fill(pool, STEAM, 0L, false));
        assertEquals(500L, PortPool.drain(pool, 9_999L, false));
        assertNull(PortPool.fluid(pool));
    }

    @Test
    void oneFluidAtATimeSoAnUndoTakesBackWhatWasFilled() {
        List<TestTank> coolant = List.of(TestTank.of(1_000, WATER, CRYO), TestTank.of(1_000, WATER, CRYO));
        assertEquals(600L, PortPool.fill(coolant, WATER, 600L, false));
        assertEquals(0L, PortPool.fill(coolant, CRYO, 100L, false), "a second fluid is refused while water is held");
        // A loader's transactional undo of that water fill is a plain drain: it returns exactly the water.
        assertEquals(600L, PortPool.drain(coolant, 600L, false));
        assertEquals(0L, total(coolant));
        assertEquals(100L, PortPool.fill(coolant, CRYO, 100L, false), "empty again, cryo is taken");
        assertEquals(CRYO, PortPool.fluid(coolant));
    }

    @Test
    void fillTopsUpTheHeldFluidBeforeEmptyPortsAndSkipsPortsThatRefuse() {
        TestTank steamOnly = TestTank.of(1_000, STEAM);
        TestTank partly = TestTank.of(1_000, WATER).with(WATER, 400L);
        TestTank empty = TestTank.of(1_000, WATER);
        List<TestTank> pool = List.of(steamOnly, empty, partly);
        assertEquals(1_000L, PortPool.fill(pool, WATER, 1_000L, false));
        assertEquals(1_000L, partly.amount(), "the tank already holding water fills first");
        assertEquals(400L, empty.amount());
        assertEquals(0L, steamOnly.amount());
    }

    @Test
    void mixedLegacyContentsStayAndCanEachBeDrawn() {
        // A save from before pooling may hold cryo in one port and water in another: both survive, the core
        // can draw each by name, and nothing is merged or lost.
        TestTank a = TestTank.of(1_000, WATER, CRYO).with(CRYO, 300L);
        TestTank b = TestTank.of(1_000, WATER, CRYO).with(WATER, 700L);
        List<TestTank> pool = List.of(a, b);
        assertEquals(CRYO, PortPool.fluid(pool));
        assertEquals(700L, PortPool.amountOf(pool, WATER));
        assertEquals(0L, PortPool.fill(pool, WATER, 50L, false), "single-fluid rule while cryo leads");
        assertEquals(700L, PortPool.drainFluid(pool, WATER, 9_999L, false));
        assertEquals(300L, PortPool.drainFluid(pool, CRYO, 9_999L, false));
        assertEquals(0L, total(pool));
    }

    @Test
    void randomisedTransfersConserveEveryMillibucket() {
        java.util.Random rnd = new java.util.Random(16L);
        List<TestTank> a = List.of(TestTank.of(1_000, STEAM), TestTank.of(700, STEAM));
        List<TestTank> b = List.of(TestTank.of(500, STEAM), TestTank.of(900, STEAM), TestTank.of(300, STEAM));
        PortPool.fill(a, STEAM, 1_500L, false);
        long start = total(a) + total(b);
        for (int i = 0; i < 5_000; i++) {
            boolean ab = rnd.nextBoolean();
            List<TestTank> from = ab ? a : b;
            List<TestTank> to = ab ? b : a;
            long want = rnd.nextInt(1_200);
            long offer = PortPool.drain(from, want, true);
            long room = PortPool.fill(to, STEAM, offer, true);
            long taken = PortPool.drain(from, room, false);
            long put = PortPool.fill(to, STEAM, taken, false);
            assertEquals(taken, put, "simulated room is honoured exactly");
            assertEquals(start, total(a) + total(b));
        }
    }
}

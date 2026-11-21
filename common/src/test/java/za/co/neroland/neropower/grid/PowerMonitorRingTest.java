package za.co.neroland.neropower.grid;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Rule test for the Power Monitor: the ring keeps the last 60 samples oldest-first, survives its compact
 * sync form, rejects malformed input, and splits a stored change into in or out.
 */
class PowerMonitorRingTest {

    @Test
    void keepsTheLastSixtyInOrder() {
        MonitorRing ring = new MonitorRing();
        for (int i = 0; i < 75; i++) {
            ring.push(i, 0, i % 1001);
        }
        assertEquals(60, ring.size());
        assertEquals(15L, ring.in(0));
        assertEquals(74L, ring.in(59));
    }

    @Test
    void roundTripsTheSyncForm() {
        MonitorRing ring = new MonitorRing();
        ring.push(5, 1, 100);
        ring.push(7, 2, 200);
        MonitorRing copy = new MonitorRing();
        copy.fromArray(ring.toArray());
        assertArrayEquals(ring.toArray(), copy.toArray());
        copy.fromArray(MonitorRing.decode(MonitorRing.encode(ring.toArray())));
        assertArrayEquals(ring.toArray(), copy.toArray());
        assertEquals(0, MonitorRing.decode("zz,%%").length);
        copy.fromArray(new int[] {5, 1});
        assertEquals(0, copy.size());
    }

    @Test
    void flowAndComparator() {
        assertArrayEquals(new long[] {50L, 0L}, MonitorRing.flowFromDelta(0L, 1_000L, 20));
        assertArrayEquals(new long[] {0L, 50L}, MonitorRing.flowFromDelta(1_000L, 0L, 20));
        assertEquals(15, MonitorRing.comparator(10L, 10L));
        assertEquals(7, MonitorRing.comparator(5L, 10L));
        assertEquals(0, MonitorRing.comparator(5L, 0L));
    }
}

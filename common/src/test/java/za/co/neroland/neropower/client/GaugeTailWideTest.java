package za.co.neroland.neropower.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.menu.GaugeTail;

/**
 * The wide gauge encoding: three 15-bit thirds survive a ContainerData short each and round-trip every
 * value up to 2^45 - 1, so an SMES ring past NeroTech's 2^30 cap reads correctly; out-of-range values
 * clamp. Pure JVM.
 */
class GaugeTailWideTest {

    private static long roundTrip(long value) {
        int hi = (short) GaugeTail.hi(value);
        int mid = (short) GaugeTail.mid(value);
        int lo = (short) GaugeTail.lo(value);
        return GaugeTail.join3(hi, mid, lo);
    }

    @Test
    void roundTripsStorageScaleValues() {
        for (long v : new long[] {0L, 1L, 32_767L, 1L << 30, (1L << 30) - 1L, 2_800_000_000L,
                1_000_000_000_000L, GaugeTail.MAX}) {
            assertEquals(v, roundTrip(v), "value " + v);
        }
    }

    @Test
    void clampsOutOfRange() {
        assertEquals(0L, roundTrip(-5L));
        assertEquals(GaugeTail.MAX, roundTrip(Long.MAX_VALUE));
    }

    @Test
    void pairEncodingStillWorksForSmallValues() {
        long v = 123_456_789L;
        assertEquals(v, GaugeTail.join((short) GaugeTail.hi2(v), (short) GaugeTail.lo2(v)));
    }
}

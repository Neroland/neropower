package za.co.neroland.neropower.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Readout precedence and the back-end stall causes match the gauge kit's table. Pure JVM. */
class BackEndReadoutTest {

    @Test
    void worstStateWins() {
        assertEquals(BackEndReadout.FAIL, BackEndReadout.readout(true, true, 4, true));
        assertEquals(BackEndReadout.HOT, BackEndReadout.readout(false, true, 4, true));
        assertEquals(BackEndReadout.STALL, BackEndReadout.readout(false, false, 4, true));
        assertEquals(BackEndReadout.WORK, BackEndReadout.readout(false, false, 0, true));
        assertEquals(BackEndReadout.REST, BackEndReadout.readout(false, false, 0, false));
    }

    @Test
    void causeKeysFollowTheKitTable() {
        assertEquals("no_water", BackEndReadout.causeKey(BackEndReadout.CAUSE_NO_WATER));
        assertEquals("output_full", BackEndReadout.causeKey(BackEndReadout.CAUSE_OUTPUT_FULL));
        assertEquals("no_power", BackEndReadout.causeKey(BackEndReadout.CAUSE_NO_POWER));
        assertEquals("nothing_to_do", BackEndReadout.causeKey(BackEndReadout.CAUSE_NOTHING_TO_DO));
        assertEquals("none", BackEndReadout.causeKey(99));
    }

    @Test
    void poolStallsOnlyWithHotRodsAndNoWater() {
        assertEquals(BackEndReadout.CAUSE_NO_WATER, BackEndReadout.poolCause(1, 0));
        assertEquals(BackEndReadout.CAUSE_NONE, BackEndReadout.poolCause(0, 0));
        assertEquals(BackEndReadout.CAUSE_NONE, BackEndReadout.poolCause(4, 1));
    }

    @Test
    void vitrifierCauses() {
        assertEquals(BackEndReadout.CAUSE_NONE, BackEndReadout.vitrifierCause(0, 0, false, false));
        assertEquals(BackEndReadout.CAUSE_NOTHING_TO_DO, BackEndReadout.vitrifierCause(1, 1, false, true));
        assertEquals(BackEndReadout.CAUSE_NOTHING_TO_DO, BackEndReadout.vitrifierCause(0, 4, false, true));
        assertEquals(BackEndReadout.CAUSE_OUTPUT_FULL, BackEndReadout.vitrifierCause(1, 2, true, true));
        assertEquals(BackEndReadout.CAUSE_NO_POWER, BackEndReadout.vitrifierCause(1, 2, false, false));
        assertEquals(BackEndReadout.CAUSE_NONE, BackEndReadout.vitrifierCause(1, 2, false, true));
    }
}

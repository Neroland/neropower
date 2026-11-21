package za.co.neroland.neropower.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@code /neropower budget}: nothing is recorded outside a window, a window closes itself after its
 * ticks, and the report gives ms per tick per type (highest first) and the total. Pure JVM.
 */
class BudgetProbeTest {

    @Test
    void windowRecordsAndClosesItself() {
        BudgetProbe.start(4);
        assertTrue(BudgetProbe.sampling());
        for (int t = 0; t < 4; t++) {
            BudgetProbe.record("neropower:fission_core", 2_000_000L); // 2 ms
            BudgetProbe.record("neropower:battery_cell", 250_000L);
            BudgetProbe.record("neropower:battery_cell", 250_000L);
            BudgetProbe.serverTick();
        }
        assertFalse(BudgetProbe.sampling());
        BudgetProbe.record("neropower:fission_core", 99_000_000L); // after the window: ignored

        BudgetProbe.Report r = BudgetProbe.report();
        assertEquals(4, r.ticks());
        assertFalse(r.running());
        assertEquals(2, r.rows().size());
        assertEquals("neropower:fission_core", r.rows().get(0).type());
        assertEquals(2.0D, r.rows().get(0).msPerTick(), 1e-9);
        assertEquals(0.5D, r.rows().get(1).msPerTick(), 1e-9);
        assertEquals(2.0D, r.rows().get(1).callsPerTick(), 1e-9);
        assertEquals(2.5D, r.totalMsPerTick(), 1e-9);
    }

    @Test
    void windowLengthIsClamped() {
        BudgetProbe.start(0);
        BudgetProbe.serverTick();
        assertFalse(BudgetProbe.sampling(), "at least one tick, then closed");
        BudgetProbe.start(Integer.MAX_VALUE);
        for (int t = 0; t < BudgetProbe.MAX_TICKS; t++) {
            BudgetProbe.serverTick();
        }
        assertFalse(BudgetProbe.sampling());
    }
}

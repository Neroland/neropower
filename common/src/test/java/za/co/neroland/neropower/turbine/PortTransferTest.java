package za.co.neroland.neropower.turbine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.config.ConfigValue;
import za.co.neroland.nerolandcore.sideconfig.SideMode;

import za.co.neroland.neropower.config.NeroPowerConfig;

/**
 * The pipe-less exchange rules: the bounded {@code portTransferInterval} key, the per-block phase (never
 * per tick), and which faces push or pull under the side configuration.
 */
class PortTransferTest {

    @SuppressWarnings("unchecked")
    private static Integer sanitize(ConfigValue<Integer> value, Integer raw) throws ReflectiveOperationException {
        Field f = ConfigValue.class.getDeclaredField("sanitizer");
        f.setAccessible(true);
        return ((UnaryOperator<Integer>) f.get(value)).apply(raw);
    }

    @Test
    @SuppressWarnings("unchecked")
    void intervalKeyIsBoundedWithItsDefaultInside() throws ReflectiveOperationException {
        TurbineConfig.init();
        ConfigValue<Integer> key = (ConfigValue<Integer>) NeroPowerConfig.schema().value("portTransferInterval");
        assertNotNull(key, "portTransferInterval is on the neropower schema");
        assertTrue(key.serverAuthoritative());
        assertEquals(10, key.defaultValue());
        assertEquals(PortTransfer.MIN_INTERVAL, sanitize(key, 0));
        assertEquals(PortTransfer.MIN_INTERVAL, sanitize(key, -50));
        assertEquals(PortTransfer.MAX_INTERVAL, sanitize(key, 1_000_000));
        assertEquals(37, sanitize(key, 37));
        assertEquals(10, TurbineConfig.portTransferInterval());
        assertEquals(1, PortTransfer.clampInterval(0));
        assertEquals(200, PortTransfer.clampInterval(Integer.MAX_VALUE));
    }

    @Test
    void exchangeRunsOncePerIntervalAtEachBlocksOwnPhase() {
        for (int interval : new int[] {1, 10, 37, 200}) {
            for (int seed : new int[] {0, 1, -7, 123_456_789, Integer.MIN_VALUE}) {
                int phase = PortTransfer.phase(seed, interval);
                assertTrue(phase >= 0 && phase < interval);
                int runs = 0;
                for (long t = 1_000L; t < 1_000L + interval * 20L; t++) {
                    if (PortTransfer.due(t, phase, interval)) {
                        runs++;
                    }
                }
                assertEquals(20, runs, "exactly one exchange per interval");
            }
        }
        assertEquals(20_000L, PortTransfer.budget(10));
    }

    @Test
    void facesFollowTheSideConfigAndGuardedPushesSkipWaterTanks() {
        // Default preset is I/O with auto-eject on: steam goes into a steam-only neighbour (turbine inlet)...
        assertTrue(PortTransfer.pushes(SideMode.IO, true, true, false));
        // ...but not into a tank that also takes water (the condenser's relay tank), unless set to PUSH.
        assertFalse(PortTransfer.pushes(SideMode.IO, true, true, true));
        assertTrue(PortTransfer.pushes(SideMode.PUSH, false, true, true));
        // Water from the condenser is unguarded.
        assertTrue(PortTransfer.pushes(SideMode.IO, true, false, true));
        assertTrue(PortTransfer.pushes(SideMode.OUTPUT, true, false, false));
        assertFalse(PortTransfer.pushes(SideMode.IO, false, false, false), "auto-eject off");
        assertFalse(PortTransfer.pushes(SideMode.INPUT, true, false, false));
        assertFalse(PortTransfer.pushes(SideMode.DISABLED, true, false, false));
        assertFalse(PortTransfer.pushes(null, true, false, false));
        assertTrue(PortTransfer.pulls(SideMode.IO, true));
        assertTrue(PortTransfer.pulls(SideMode.INPUT, true));
        assertFalse(PortTransfer.pulls(SideMode.IO, false), "auto-input off");
        assertFalse(PortTransfer.pulls(SideMode.OUTPUT, true));
        assertFalse(PortTransfer.pulls(SideMode.PUSH, true));
        assertFalse(PortTransfer.pulls(SideMode.DISABLED, true));
    }
}

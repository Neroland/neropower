package za.co.neroland.neropower.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.menu.GaugeTail;

/**
 * Each gauge tooltip is shown exactly once per screen (NeroTech 0.8.0 migration). NeroTech's
 * {@code MachineScreen} is the single emitter; NeroPower's base screen only extends
 * {@code gaugeTooltip(int, int)} with its readout, the NeroPower tooltip builder has no emitter, no
 * NeroPower screen re-answers a default gauge, and NeroPower's tail no longer duplicates NeroTech's
 * energy / heat values. Reflection only: classes are loaded without initialising Minecraft.
 */
class GaugeTooltipOnceTest {

    private static final List<String> SCREENS = List.of(
            "za.co.neroland.neropower.fission.client.FissionCoreScreen",
            "za.co.neroland.neropower.storage.client.BatteryCellScreen",
            "za.co.neroland.neropower.storage.client.BankControllerScreen",
            "za.co.neroland.neropower.beam.client.BeamScreen",
            "za.co.neroland.neropower.environmental.client.RtgScreen",
            "za.co.neroland.neropower.environmental.client.StirlingScreen");

    /** Methods that answer a default gauge or emit; only NeroTech's base and PowerMachineScreen may declare them. */
    private static final Set<String> GAUGE_PROVIDERS = Set.of(
            "gaugeTooltip", "energyTooltip", "heatTooltip", "progressTooltip", "emitGaugeTooltip");

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, GaugeTooltipOnceTest.class.getClassLoader());
    }

    private static boolean declares(Class<?> type, String method) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void nerotechMachineScreenIsTheOnlyEmitter() throws Exception {
        Class<?> machineScreen = load("za.co.neroland.nerotech.client.MachineScreen");
        assertTrue(declares(machineScreen, "emitGaugeTooltip"), "NeroTech's base emits");
        Class<?> base = load("za.co.neroland.neropower.client.PowerMachineScreen");
        assertFalse(declares(base, "emitGaugeTooltip"), "PowerMachineScreen must not emit a second time");
        assertTrue(declares(base, "gaugeTooltip"), "it extends the single provider chain instead");
        assertFalse(declares(GaugeTooltips.class, "show"), "the NeroPower builder has no emitter");
    }

    @Test
    void everyScreenGoesThroughTheBaseAndAddsNoSecondProvider() throws Exception {
        Class<?> base = load("za.co.neroland.neropower.client.PowerMachineScreen");
        for (String name : SCREENS) {
            Class<?> screen = load(name);
            assertTrue(base.isAssignableFrom(screen), name + " extends PowerMachineScreen");
            for (String provider : GAUGE_PROVIDERS) {
                assertFalse(declares(screen, provider), name + " re-declares " + provider);
            }
        }
    }

    @Test
    void tailCarriesNothingNeroTechAlreadySyncs() {
        for (Field f : GaugeTail.class.getDeclaredFields()) {
            String n = f.getName();
            assertFalse(n.equals("ENERGY") || n.equals("ENERGY_CAPACITY") || n.startsWith("HEAT"),
                    "GaugeTail duplicates NeroTech's tail value " + n);
        }
        assertEquals(9, GaugeTail.COUNT, "extra + wide energy + wide capacity, three ints each");
    }
}

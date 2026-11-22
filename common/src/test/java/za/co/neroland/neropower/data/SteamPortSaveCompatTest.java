package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.turbine.PortPool;
import za.co.neroland.neropower.turbine.PortTankSave;
import za.co.neroland.neropower.turbine.TestTank;

/**
 * Shared-tank save compatibility: steam-port saves written before the shared tanks
 * ({@code fixtures/neropower_0_4_0_pre_steam_ports.snbt}) load through the current reader, their per-port
 * contents are the shared tank's contents with nothing moved or lost (all of it can be drawn back through
 * the pool), and writing them again gives the same keys and values. No 0.3.x release had steam ports, so
 * the 0.3.2 fixture carries no port keys to migrate. Pure JVM.
 */
class SteamPortSaveCompatTest {

    private static final String FIXTURE = "common/src/test/resources/fixtures/neropower_0_4_0_pre_steam_ports.snbt";
    private static final String OLD_FIXTURE = "common/src/test/resources/fixtures/neropower_0_3_2_saves.snbt";
    private static final long TANK = 16_000L;

    private static Path find(String rel) {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(rel);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("fixture not found above " + Paths.get("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> load(String rel) throws IOException {
        return (Map<String, Map<String, Object>>) MiniSnbt.parse(Files.readString(find(rel), StandardCharsets.UTF_8));
    }

    private static TestTank tank(PortTankSave save) {
        TestTank t = new TestTank(TANK, f -> true);
        return save.empty() ? t : t.with(save.fluid(), save.amount());
    }

    @Test
    void reactorPortsLoadIntoTheSharedTanksWithNothingLost() throws IOException {
        Map<String, Map<String, Object>> all = load(FIXTURE);
        List<String> roles = List.of("reactor_port_water", "reactor_port_steam_full", "reactor_port_cryo");
        java.util.ArrayList<TestTank> coolant = new java.util.ArrayList<>();
        java.util.ArrayList<TestTank> steam = new java.util.ArrayList<>();
        for (String role : roles) {
            SaveKeys.Reader in = SaveKeys.of(all.get(role));
            coolant.add(tank(PortTankSave.read(in, PortTankSave.REACTOR_COOLANT)));
            steam.add(tank(PortTankSave.read(in, PortTankSave.REACTOR_STEAM)));
        }
        assertEquals(18_500L, PortPool.amount(steam), "2 500 + 16 000 mB of steam, one tank");
        assertEquals(12_000L, PortPool.amountOf(coolant, "minecraft:water"));
        assertEquals(800L, PortPool.amountOf(coolant, "nerotech:cryo_coolant"));
        assertEquals(3 * TANK, PortPool.capacity(steam));
        // Every mB can leave through the pool, whichever port it sat in.
        assertEquals(18_500L, PortPool.drain(steam, Long.MAX_VALUE, false));
        assertEquals(12_000L, PortPool.drainFluid(coolant, "minecraft:water", Long.MAX_VALUE, false));
        assertEquals(800L, PortPool.drainFluid(coolant, "nerotech:cryo_coolant", Long.MAX_VALUE, false));
    }

    @Test
    void turbinePortsLoadAndWriteBackByteStable() throws IOException {
        Map<String, Map<String, Object>> all = load(FIXTURE);
        for (String role : List.of("reactor_port_water", "reactor_port_steam_full", "reactor_port_cryo",
                "turbine_port_inlet", "turbine_port_exhaust")) {
            Map<String, Object> saved = all.get(role);
            boolean reactor = role.startsWith("reactor");
            String[] prefixes = reactor
                    ? new String[] {PortTankSave.REACTOR_COOLANT, PortTankSave.REACTOR_STEAM}
                    : new String[] {PortTankSave.TURBINE_STEAM, PortTankSave.TURBINE_EXHAUST};
            Map<String, Object> written = new LinkedHashMap<>();
            for (String prefix : prefixes) {
                PortTankSave.read(SaveKeys.of(saved), prefix).write(SaveKeys.into(written), prefix);
            }
            Map<String, Object> expected = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : saved.entrySet()) {
                for (String prefix : prefixes) {
                    if (e.getKey().equals(prefix + PortTankSave.FLUID) || e.getKey().equals(prefix + PortTankSave.AMOUNT)) {
                        expected.put(e.getKey(), e.getValue());
                    }
                }
            }
            assertEquals(expected, written, role);
        }
        SaveKeys.Reader inlet = SaveKeys.of(all.get("turbine_port_inlet"));
        assertEquals(new PortTankSave("neropower:steam", 7_000), PortTankSave.read(inlet, PortTankSave.TURBINE_STEAM));
        assertTrue(PortTankSave.read(inlet, PortTankSave.TURBINE_EXHAUST).empty());
    }

    @Test
    void the032FixtureHasNoPortKeysToMigrate() throws IOException {
        for (Map<String, Object> compound : load(OLD_FIXTURE).values()) {
            for (String key : compound.keySet()) {
                assertTrue(!key.startsWith(PortTankSave.REACTOR_COOLANT) && !key.startsWith(PortTankSave.TURBINE_EXHAUST)
                        && !key.equals("SteamFluid") && !key.equals("SteamAmount"), key);
            }
        }
    }
}

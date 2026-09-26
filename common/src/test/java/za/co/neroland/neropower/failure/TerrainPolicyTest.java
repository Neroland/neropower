package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the {@code terrainDamageMode} toggle through {@link TerrainPolicy} (what
 * {@code NeroPowerConfig.terrainDamage(boolean)} delegates to): the three modes on a dedicated and an
 * integrated server, and every unrecognised value falling back to {@code auto}. Pure JVM.
 */
class TerrainPolicyTest {

    @Test
    void onBreaksTerrainEverywhere() {
        assertTrue(TerrainPolicy.resolve("on", true));
        assertTrue(TerrainPolicy.resolve("on", false));
    }

    @Test
    void offNeverBreaksTerrain() {
        assertFalse(TerrainPolicy.resolve("off", true));
        assertFalse(TerrainPolicy.resolve("off", false));
    }

    @Test
    void autoIsOffOnDedicatedAndOnInSingleplayer() {
        assertFalse(TerrainPolicy.resolve("auto", true), "dedicated server: shared world is spared");
        assertTrue(TerrainPolicy.resolve("auto", false), "singleplayer / LAN: the player's own blast");
    }

    @Test
    void invalidValuesReadAsAuto() {
        for (String bad : new String[] {null, "", "  ", "yes", "true", "0", "maybe"}) {
            assertEquals("auto", TerrainPolicy.normalize(bad), String.valueOf(bad));
            assertFalse(TerrainPolicy.resolve(bad, true), String.valueOf(bad));
            assertTrue(TerrainPolicy.resolve(bad, false), String.valueOf(bad));
        }
    }

    @Test
    void modesAreCaseAndSpaceInsensitive() {
        assertTrue(TerrainPolicy.resolve(" ON ", true));
        assertFalse(TerrainPolicy.resolve("Off", false));
        assertEquals("on", TerrainPolicy.normalize("On"));
    }
}

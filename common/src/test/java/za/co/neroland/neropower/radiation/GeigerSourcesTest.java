package za.co.neroland.neropower.radiation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Privacy: the Geiger counts only sources that leak, so a fully shielded cask is not detectable. */
class GeigerSourcesTest {

    @Test
    void aFullyShieldedSourceIsNotCounted() {
        assertFalse(RadiationSources.counts(true, 0), "loaded cask behind shielding on all six faces");
        assertTrue(RadiationSources.counts(true, 1), "one open face leaks, so it counts");
        assertFalse(RadiationSources.counts(false, 6), "an empty cask emits nothing");
    }
}

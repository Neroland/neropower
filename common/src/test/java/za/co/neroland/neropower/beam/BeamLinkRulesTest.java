package za.co.neroland.neropower.beam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.beam.BeamLinkRules.Decision;

/**
 * Locks the Configurator link decision ({@link BeamLinkRules}): authorisation refusal at either end,
 * the loaded-endpoints requirement, and the cross-dimension toggle (only onto an orbital receiver in
 * a space dimension). Pure JVM.
 */
class BeamLinkRulesTest {

    /** Same-dimension, everything loaded and authorised. */
    private static Decision same(boolean srcLoaded, boolean tgtLoaded, boolean maySrc, boolean mayTgt) {
        return BeamLinkRules.decide(srcLoaded, tgtLoaded, maySrc, mayTgt, true, false, false, false);
    }

    /** Cross-dimension with every endpoint loaded and authorised. */
    private static Decision cross(boolean toggle, boolean orbital, boolean space) {
        return BeamLinkRules.decide(true, true, true, true, false, toggle, orbital, space);
    }

    @Test
    void sameDimensionAuthorisedLinkIsAllowed() {
        assertEquals(Decision.OK, same(true, true, true, true));
        assertTrue(BeamLinkRules.allowed(true, true, true, true, true, false, false, false));
    }

    @Test
    void authRefusalAtEitherEndDenies() {
        assertEquals(Decision.DENIED, same(true, true, false, true), "no mayInteract at the source");
        assertEquals(Decision.DENIED, same(true, true, true, false), "no mayInteract at the target");
        assertEquals(Decision.DENIED, same(true, true, false, false));
        assertFalse(BeamLinkRules.allowed(true, true, false, true, true, false, false, false));
    }

    @Test
    void unloadedEndpointIsSourceGone() {
        assertEquals(Decision.SOURCE_GONE, same(false, true, true, true));
        assertEquals(Decision.SOURCE_GONE, same(true, false, true, true));
        assertEquals(Decision.SOURCE_GONE, same(false, true, false, true), "gone is reported before denied");
    }

    @Test
    void crossDimensionNeedsToggleOrbitalAndSpace() {
        assertEquals(Decision.OK, cross(true, true, true));
        assertEquals(Decision.CROSS_DIMENSION, cross(false, true, true), "toggle off");
        assertEquals(Decision.CROSS_DIMENSION, cross(true, false, true), "not an orbital receiver");
        assertEquals(Decision.CROSS_DIMENSION, cross(true, true, false), "target dimension is not space");
        assertEquals(Decision.CROSS_DIMENSION, cross(false, false, false));
    }

    @Test
    void crossDimensionCheckRunsFirst() {
        assertEquals(Decision.CROSS_DIMENSION,
                BeamLinkRules.decide(false, true, false, false, false, false, true, true));
    }

    @Test
    void crossDimensionStillNeedsLoadAndAuth() {
        assertEquals(Decision.SOURCE_GONE, BeamLinkRules.decide(false, true, true, true, false, true, true, true));
        assertEquals(Decision.DENIED, BeamLinkRules.decide(true, true, true, false, false, true, true, true));
    }

    @Test
    void dimensionAllowedTruthTable() {
        assertTrue(BeamLinkRules.dimensionAllowed(true, false, false, false), "same dimension ignores the rest");
        assertTrue(BeamLinkRules.dimensionAllowed(false, true, true, true));
        assertFalse(BeamLinkRules.dimensionAllowed(false, true, true, false));
        assertFalse(BeamLinkRules.dimensionAllowed(false, true, false, true));
        assertFalse(BeamLinkRules.dimensionAllowed(false, false, true, true));
    }
}

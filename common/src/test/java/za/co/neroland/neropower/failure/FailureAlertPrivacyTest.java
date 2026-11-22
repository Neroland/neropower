package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The owner's Core link alert is persisted by Core against the owner and pushed to their app, so it
 * must carry no coordinates: the text names the machine type and stage only, and the id keys the
 * machine through a salted hash that is stable within a run but is not the packed position.
 */
class FailureAlertPrivacyTest {

    @Test
    void alertTextNamesTheMachineAndStageOnly() {
        String failed = FailureTelegraph.alertText("neropower:fission_core", FailureStage.FAILURE);
        String unstable = FailureTelegraph.alertText("neropower:turbine_helm", FailureStage.UNSTABLE);
        assertEquals("Your NeroPower machine has failed (neropower:fission_core).", failed);
        assertEquals("Your NeroPower machine is unstable (neropower:turbine_helm).", unstable);
        assertFalse(failed.matches(".*\\d.*"), "no number, so no coordinate: " + failed);
        assertFalse(unstable.matches(".*\\d.*"), "no number, so no coordinate: " + unstable);
    }

    @Test
    void alertIdIsStablePerMachineButNotThePosition() {
        long packed = 274_877_906_944L + 64L * 4_096L + 17L; // some packed block position
        String id = FailureTelegraph.alertId(FailureStage.FAILURE, packed);
        assertEquals(id, FailureTelegraph.alertId(FailureStage.FAILURE, packed), "a repeat replaces the alert");
        assertNotEquals(id, FailureTelegraph.alertId(FailureStage.UNSTABLE, packed), "one alert per stage");
        assertNotEquals(id, FailureTelegraph.alertId(FailureStage.FAILURE, packed + 1), "one alert per machine");
        assertTrue(id.startsWith("failure_failure_"), id);
        assertFalse(id.contains(Long.toString(packed)), "the packed position is not in the id: " + id);
        assertFalse(id.contains(Long.toHexString(packed)), "nor its hex form: " + id);
    }
}

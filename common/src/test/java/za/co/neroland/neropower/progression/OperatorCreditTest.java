package za.co.neroland.neropower.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Operator-in-the-moment credit (trophy hooks): the pure decisions, and a source check that the credit
 * paths persist nothing about the player and never look up a nearest player.
 */
class OperatorCreditTest {

    private static final UUID A = new UUID(1L, 1L);
    private static final UUID B = new UUID(2L, 2L);

    @Test
    void triesOncePerOperatorWhileTheConditionHolds() {
        assertFalse(OperatorCredit.shouldTry(false, A, null), "no event, no credit");
        assertFalse(OperatorCredit.shouldTry(true, null, null), "no operator in the moment, no credit");
        assertTrue(OperatorCredit.shouldTry(true, A, null));
        assertFalse(OperatorCredit.shouldTry(true, A, A), "the same operator is not re-tried every tick");
        assertTrue(OperatorCredit.shouldTry(true, B, A), "a new operator of a running machine is credited too");
    }

    @Test
    void multiblockOperatorPrefersTheCore() {
        assertEquals(A, OperatorCredit.firstKnown(A, B));
        assertEquals(B, OperatorCredit.firstKnown(null, B));
        assertNull(OperatorCredit.firstKnown(null, null));
        assertNull(OperatorCredit.firstKnown((UUID[]) null));
    }

    @Test
    void criticalityIsARealBurnNotTheCore() {
        assertFalse(OperatorCredit.critical(false, 5), "an unformed core is not critical");
        assertFalse(OperatorCredit.critical(true, 0), "a formed core that aged no rod is not critical");
        assertTrue(OperatorCredit.critical(true, 1));
    }

    @Test
    void milestoneConditions() {
        assertFalse(OperatorCredit.spunUp(0L, 1800, 1800), "no output");
        assertFalse(OperatorCredit.spunUp(10L, 1799, 1800), "below rated speed");
        assertTrue(OperatorCredit.spunUp(10L, 1800, 1800));
        assertFalse(OperatorCredit.bankCapacity(false, 10L, 5L));
        assertFalse(OperatorCredit.bankCapacity(true, 4L, 5L));
        assertTrue(OperatorCredit.bankCapacity(true, 5L, 5L));
        assertFalse(OperatorCredit.ringCharged(true, 0L, 0L), "an empty ring of no capacity is not charged");
        assertFalse(OperatorCredit.ringCharged(false, 10L, 10L));
        assertFalse(OperatorCredit.ringCharged(true, 9L, 10L));
        assertTrue(OperatorCredit.ringCharged(true, 10L, 10L));
    }

    @Test
    void gatesAreDistinctNeroPowerIds() {
        List<String> gates = List.of(MilestoneGates.CRITICALITY, MilestoneGates.BANK_CAPACITY,
                MilestoneGates.BEAM_LINKED, MilestoneGates.FIRST_BREED, MilestoneGates.TURBINE_SPINUP,
                MilestoneGates.SMES_CHARGED, MilestoneGates.FIRST_VITRIFIED).stream().map(Object::toString).toList();
        assertEquals(gates.size(), gates.stream().distinct().count());
        assertTrue(gates.stream().allMatch(g -> g.startsWith("neropower:")));
        assertTrue(gates.contains("neropower:criticality") && gates.contains("neropower:bank_capacity"),
                "the trophy hooks' gate ids are unchanged");
    }

    /** The credit call sites keep the operator only in transient fields and never use a nearest-player lookup. */
    @Test
    void creditPathsPersistNothingAndUseNoNearestPlayer() throws IOException {
        for (String file : List.of("progression/MilestoneGates.java", "progression/OperatorCredit.java",
                "fission/FissionCoreBlockEntity.java", "turbine/TurbineHelmBlockEntity.java",
                "storage/BankControllerBlockEntity.java", "storage/SmesControllerBlockEntity.java")) {
            String src = source(file);
            if (src == null) {
                continue; // sources not beside the test classpath (packaged run)
            }
            assertFalse(src.contains("getNearestPlayer") || src.contains("nearestPlayer("),
                    file + " must not credit or authorise by nearest player");
            for (String field : List.of("criticalityTried", "breedTried", "spinUpTried", "capacityTried", "chargeTried")) {
                assertFalse(src.matches("(?s).*(putUUID|store|output\\.put\\w*)\\([^;]*" + field + ".*"),
                        file + " must not save " + field);
            }
        }
    }

    private static String source(String rel) throws IOException {
        java.nio.file.Path p = java.nio.file.Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            java.nio.file.Path f = p.resolve("common/src/main/java/za/co/neroland/neropower/" + rel);
            if (java.nio.file.Files.isRegularFile(f)) {
                try (InputStream in = java.nio.file.Files.newInputStream(f)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}

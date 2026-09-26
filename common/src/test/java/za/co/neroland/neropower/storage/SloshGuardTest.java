package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Locks the storage slosh guard ({@link PoolMath#shouldSkip}, applied by
 * {@code StorageEnergy.pushToNeighbours}): a storage push never feeds another storage block nor
 * the pusher's own member cells, and does feed an ordinary machine. Pure JVM.
 */
class SloshGuardTest {

    @Test
    void ordinaryMachineIsFed() {
        assertFalse(PoolMath.shouldSkip(false, false));
    }

    @Test
    void storageNeighbourIsSkipped() {
        assertTrue(PoolMath.shouldSkip(true, false), "cell / controller / NeroTech Battery Bank");
    }

    @Test
    void ownMemberCellIsSkipped() {
        assertTrue(PoolMath.shouldSkip(false, true));
        assertTrue(PoolMath.shouldSkip(true, true));
    }
}

package za.co.neroland.neropower.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/**
 * Locks storage mode switching: {@link BankMode#next()} cycles BUFFER → PRIORITY_SOURCE → BUFFER
 * (the sneak-use toggle), the persisted ordinal ({@code BankMode} int in the controller's save and
 * the menu's synced int) round-trips, and a bad ordinal falls back to BUFFER. Pure JVM —
 * {@link BankMode} is a plain enum.
 */
class BankModeTest {

    @Test
    void nextCyclesThroughEveryModeAndWraps() {
        assertEquals(BankMode.PRIORITY_SOURCE, BankMode.BUFFER.next());
        assertEquals(BankMode.BUFFER, BankMode.PRIORITY_SOURCE.next());
        BankMode mode = BankMode.BUFFER;
        for (int i = 0; i < BankMode.values().length; i++) {
            mode = mode.next();
        }
        assertEquals(BankMode.BUFFER, mode, "a full cycle returns to the start");
    }

    @Test
    void nextAlwaysChangesTheMode() {
        for (BankMode mode : BankMode.values()) {
            assertNotEquals(mode, mode.next());
        }
    }

    @Test
    void ordinalRoundTripsForPersistence() {
        for (BankMode mode : BankMode.values()) {
            assertEquals(mode, BankMode.byOrdinal(mode.ordinal()));
        }
        assertEquals(0, BankMode.BUFFER.ordinal(), "BUFFER is the saved default (ordinal 0) — do not reorder");
        assertEquals(1, BankMode.PRIORITY_SOURCE.ordinal());
    }

    @Test
    void badOrdinalsFallBackToBuffer() {
        assertEquals(BankMode.BUFFER, BankMode.byOrdinal(-1));
        assertEquals(BankMode.BUFFER, BankMode.byOrdinal(BankMode.values().length));
        assertEquals(BankMode.BUFFER, BankMode.byOrdinal(Integer.MAX_VALUE));
    }

    @Test
    void translationKeysAreNamespaced() {
        assertEquals("neropower.bank_mode.buffer", BankMode.BUFFER.translationKey());
        assertEquals("neropower.bank_mode.priority_source", BankMode.PRIORITY_SOURCE.translationKey());
    }
}

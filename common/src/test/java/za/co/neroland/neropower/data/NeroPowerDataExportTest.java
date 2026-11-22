package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.google.gson.JsonObject;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.beam.BeamLinkSession;

/** The export reports the player's own half-made link only, and never another player's id. */
class NeroPowerDataExportTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test
    void reportsOnlyTheRequestersPendingLink() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, "minecraft:overworld", 10, 64, -5, 100L);
        session.begin(BOB, "minecraft:the_nether", 1, 2, 3, 100L);

        JsonObject alice = NeroPowerDataExport.exportWith(session, ALICE, 150L);
        JsonObject link = alice.getAsJsonObject("pendingBeamLink");
        assertEquals("minecraft:overworld", link.get("dimension").getAsString());
        assertEquals(10, link.get("x").getAsInt());
        assertEquals(BeamLinkSession.TIMEOUT_TICKS - 50L, link.get("ticksLeft").getAsLong());
        assertFalse(alice.toString().contains(BOB.toString()));
        assertFalse(alice.toString().contains("the_nether"));
        assertFalse(alice.toString().contains(ALICE.toString()), "the export never repeats the id");
    }

    @Test
    void aLapsedOrAbsentLinkLeavesOnlyTheOwnershipNote() {
        BeamLinkSession session = new BeamLinkSession();
        session.begin(ALICE, "minecraft:overworld", 0, 0, 0, 0L);
        JsonObject later = NeroPowerDataExport.exportWith(session, ALICE, BeamLinkSession.TIMEOUT_TICKS + 1L);
        assertFalse(later.has("pendingBeamLink"));
        assertTrue(later.get("beamLinks").getAsString().contains("no list"));
        assertFalse(NeroPowerDataExport.exportWith(new BeamLinkSession(), BOB, 0L).has("pendingBeamLink"));
    }
}

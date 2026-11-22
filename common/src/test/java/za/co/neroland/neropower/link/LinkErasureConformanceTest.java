package za.co.neroland.neropower.link;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.data.ErasureConformance;
import za.co.neroland.nerolandcore.data.PlayerDataErasure;

/**
 * POPIA/GDPR: link schema 2 keeps per-requester state in memory only (opaque handle tables and the
 * action window). One Core erase request must drop both, through the eraser the module registers
 * itself, and leave every other requester alone. Run through Core's {@link ErasureConformance}
 * harness with a {@code null} server, as the rest of the suite does.
 */
class LinkErasureConformanceTest {

    @BeforeAll
    static void registerModule() {
        NeroPowerLinkModule.register(); // registers the module's own eraser once per process
    }

    private static void seed(UUID player) {
        NeroPowerLinkModule.HANDLES.issue(player, NeroPowerLinkModule.SECTION_FISSION, List.of(
                new NeroPowerLinkModule.PlantTarget(null, null)));
        assertTrue(NeroPowerLinkModule.LIMITER.tryAcquire(player));
        assertTrue(NeroPowerLinkModule.READS.tryRead(player, NeroPowerLinkModule.SECTION_STORAGE));
        assertTrue(NeroPowerLinkModule.holdsPlayer(player), "seeded, so the probe is not vacuous");
    }

    @Test
    void oneEraseRequestDropsHandlesAndTheActionWindow() {
        UUID player = UUID.randomUUID();
        seed(player);
        ErasureConformance.create()
                .probe("neropower:link_handles", NeroPowerLinkModule.HANDLES::holds)
                .probe("neropower:link_rate_window", NeroPowerLinkModule.LIMITER::holds)
                .probe("neropower:link_read_budget", NeroPowerLinkModule.READS::holds)
                .verify(null, player);
        assertFalse(NeroPowerLinkModule.holdsPlayer(player));
    }

    @Test
    void erasureLeavesOtherRequestersAlone() {
        UUID erased = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        seed(erased);
        seed(kept);
        PlayerDataErasure.erase(null, erased);
        assertFalse(NeroPowerLinkModule.holdsPlayer(erased));
        assertTrue(NeroPowerLinkModule.HANDLES.holds(kept), "another player's handles survive");
        assertTrue(NeroPowerLinkModule.LIMITER.holds(kept), "another player's window survives");
        NeroPowerLinkModule.forgetPlayer(kept);
    }
}

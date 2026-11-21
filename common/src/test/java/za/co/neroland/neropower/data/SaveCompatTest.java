package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import za.co.neroland.neropower.beam.BeamTarget;
import za.co.neroland.neropower.failure.FailureController;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.failure.FailureThresholds;
import za.co.neroland.neropower.fission.FissionCoreSave;
import za.co.neroland.neropower.storage.BankMode;

/**
 * A 0.3.2 world loads unchanged: the reactor, bank and beam-link saves in
 * {@code fixtures/neropower_0_3_2_saves.snbt} read back every field through the current readers, the
 * absent 0.4.0 keys (failure stage) read as their defaults, and writing a loaded reactor back produces
 * the old keys with the old values (byte-stable for the 0.3.x key set). Pure JVM.
 */
class SaveCompatTest {

    private static final String FIXTURE = "common/src/test/resources/fixtures/neropower_0_3_2_saves.snbt";

    /**
     * The fixture file. Shared test resources are not on each node's classpath (only the test sources
     * are spliced in), so it is found by walking up from the node's working directory to the repo root.
     */
    private static Path fixturePath() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(FIXTURE);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("fixture not found above " + Paths.get("").toAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> fixture(String role) throws IOException {
        Map<String, Object> all = (Map<String, Object>) MiniSnbt.parse(
                Files.readString(fixturePath(), StandardCharsets.UTF_8));
        Map<String, Object> compound = (Map<String, Object>) all.get(role);
        assertNotNull(compound, role);
        return compound;
    }

    @Test
    void reactorLoadsEveryField() throws IOException {
        Map<String, Object> core = fixture("fission_core");
        FissionCoreSave save = FissionCoreSave.read(SaveKeys.of(core));
        assertEquals(5, save.shellSize());
        assertEquals(2, save.rodCount());
        assertEquals(731_552L, save.burnupAccumulator());
        assertEquals(140, save.poison());
        assertFalse(save.stalled());
        assertEquals(300, save.scramTicks());

        // Rods keep their burn-up component in the slots vanilla restores (the four-slot container is unchanged).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) core.get("Items");
        assertEquals(3, items.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) items.get(0).get("components");
        assertEquals(420, components.get("neropower:burnup"));

        // 0.4.0 additive keys are absent: the failure ladder starts STABLE, as in 0.3.x.
        FailureController ladder = new FailureController(FailureThresholds.DEFAULTS);
        SaveKeys.Reader in = SaveKeys.of(core);
        ladder.load(in.getInt("FailureStage", 0), in.getInt("FailureTicks", 0));
        assertEquals(FailureStage.STABLE, ladder.stage());
    }

    @Test
    void reactorWritesTheOldKeysWithTheOldValues() throws IOException {
        Map<String, Object> core = fixture("fission_core");
        Map<String, Object> written = new LinkedHashMap<>();
        FissionCoreSave.read(SaveKeys.of(core)).write(SaveKeys.into(written));
        assertEquals(List.of("ShellSize", "RodCount", "BurnupAcc", "Poison", "Stalled", "ScramTicks"),
                List.copyOf(written.keySet()));
        for (String key : written.keySet()) {
            Object old = core.get(key);
            Object now = written.get(key);
            if (old instanceof Byte b) {
                assertEquals(b != 0, now, key);
            } else {
                assertEquals(((Number) old).longValue(), ((Number) now).longValue(), key);
            }
        }
        // And a second round trip is identical.
        Map<String, Object> again = new LinkedHashMap<>();
        FissionCoreSave.read(SaveKeys.of(written)).write(SaveKeys.into(again));
        assertEquals(written, again);
    }

    @Test
    void bankModeLoads() throws IOException {
        Map<String, Object> bank = fixture("bank_controller");
        assertEquals(BankMode.PRIORITY_SOURCE, BankMode.byOrdinal(SaveKeys.of(bank).getInt("BankMode", 0)));
    }

    @Test
    void beamLinkAndOwnerLoad() throws IOException {
        SaveKeys.Reader tx = SaveKeys.of(fixture("beam_transmitter"));
        BeamTarget target = BeamTarget.read(tx);
        assertNotNull(target);
        assertEquals(64, target.pos().getX());
        assertEquals(70, target.pos().getY());
        assertEquals(-20, target.pos().getZ());
        assertEquals("minecraft:overworld", target.dimension());
        assertEquals(new UUID(4_822_678_189_205_111L, -6_534_453_187_483_893_493L), SaveKeys.readLinkOwner(tx));

        Map<String, Object> written = new LinkedHashMap<>();
        BeamTarget.write(SaveKeys.into(written), target);
        SaveKeys.writeLinkOwner(SaveKeys.into(written), SaveKeys.readLinkOwner(tx));
        assertEquals(target, BeamTarget.read(SaveKeys.of(written)));
        assertEquals(SaveKeys.readLinkOwner(tx), SaveKeys.readLinkOwner(SaveKeys.of(written)));

        SaveKeys.Reader rx = SaveKeys.of(fixture("beam_receiver_unlinked"));
        assertNull(BeamTarget.read(rx));
        assertNull(SaveKeys.readLinkOwner(rx), "zero halves mean no owner");
    }
}

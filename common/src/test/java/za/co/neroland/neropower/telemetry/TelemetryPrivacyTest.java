package za.co.neroland.neropower.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * POPIA/GDPR for crash reports: the scrubber removes account paths, UUIDs and coordinates, and the
 * SDK is configured opt-out with no default PII, no server name and no user. The configuration half
 * is checked against the source, because starting Sentry in a unit test would send a report.
 */
class TelemetryPrivacyTest {

    @Test
    void scrubRemovesAccountPathsUuidsAndCoordinates() {
        UUID id = UUID.randomUUID();
        String raw = "Ticking block entity at BlockPos{x=120, y=64, z=-33} owned by " + id
                + " in C:\\Users\\someone\\curseforge\\x.jar, chunk [7, 4, -3], at x: 1.5, y: 70, z: -2";
        String out = NeroPowerTelemetry.scrub(raw);
        assertFalse(out.contains(id.toString()), out);
        assertFalse(out.contains(id.toString().toUpperCase()), out);
        assertFalse(out.contains("someone"), out);
        assertFalse(out.contains("120"), out);
        assertFalse(out.contains("-33"), out);
        assertFalse(out.contains("7, 4, -3"), out);
        assertFalse(out.contains("70"), out);
        assertTrue(out.contains("<uuid>"), out);
        assertTrue(out.startsWith("Ticking block entity"), "the triage text survives: " + out);
    }

    @Test
    void scrubRemovesEntityNamesWorldNamesAndDecimalTriples() {
        String raw = "ServerPlayer['Dario'/7, l='ServerLevel[My World]', x=1.0, y=2.0, z=3.0] at (12.5, 64.0, -30.2)";
        String out = NeroPowerTelemetry.scrub(raw);
        assertFalse(out.contains("Dario"), out);
        assertFalse(out.contains("My World"), out);
        assertFalse(out.contains("12.5"), out);
        assertFalse(out.contains("-30.2"), out);
        assertTrue(out.startsWith("ServerPlayer['<name>'/?"), out);
    }

    @Test
    void scrubLeavesVersionsAndClassNamesAlone() {
        String raw = "za.co.neroland.neropower.fission.FissionCoreBlockEntity failed on 26.3 (Core 1.20.0)";
        assertEquals(raw, NeroPowerTelemetry.scrub(raw));
    }

    @Test
    void sdkIsOptOutAndCollectsNoIdentity() throws IOException {
        String telemetry = read("telemetry/NeroPowerTelemetry.java");
        assertTrue(telemetry.contains("options.setSendDefaultPii(false)"), "default PII must stay off");
        assertTrue(telemetry.contains("options.setAttachServerName(false)"), "server name must stay off");
        assertTrue(telemetry.contains("event.setUser(null)"), "no user on any event");
        assertTrue(telemetry.contains("event.setServerName(null)"), "no server name on any event");
        assertTrue(telemetry.contains("frame.setAbsPath(null)"), "absolute frame paths are dropped");
        assertTrue(telemetry.contains("if (!NeroPowerConfig.telemetryEnabled())"),
                "the opt-out is read before the SDK starts");
        assertFalse(telemetry.contains("setUser(new"), "no user object is ever built");

        String config = read("config/NeroPowerConfig.java").replaceAll("\\s+", " ");
        assertTrue(config.contains("SCHEMA.bool(\"telemetryEnabled\", true, false,"),
                "telemetryEnabled defaults to on (opt-out) and is not server-synced");
    }

    private static String read(String relative) throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "repository root not found");
        return Files.readString(dir.resolve("common/src/main/java/za/co/neroland/neropower").resolve(relative),
                StandardCharsets.UTF_8);
    }
}

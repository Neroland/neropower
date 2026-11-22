package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Privacy: {@code /neropower gallery} links its exhibit beams under a token, never the operator. */
class GalleryPrivacyTest {

    @Test
    void galleryBeamsRecordNoPlayer() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "repository root not found");
        String src = Files.readString(dir.resolve(
                "common/src/main/java/za/co/neroland/neropower/command/NeroPowerCommands.java"), StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertFalse(src.matches(".*\\.link\\([^;]*getUUID\\(\\)[^;]*\\);.*"), "a gallery link must not pass a player UUID");
        assertTrue(src.contains("GALLERY_LINK_TOKEN = java.util.UUID.randomUUID()"), "the token is random per process");
    }
}

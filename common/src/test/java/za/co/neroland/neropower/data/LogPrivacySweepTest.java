package za.co.neroland.neropower.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * POPIA/GDPR log sweep over every NeroPower main source file (common and the three loaders): no
 * logger call at INFO, WARN or ERROR may pass a player identifier, a name, a position or free text
 * as an argument, and nothing prints to {@code System.out} / {@code System.err}. The scan reads the
 * source text, so it covers code behind every {@code //?} version comment too.
 *
 * <p>Only the call's arguments outside string literals are checked: a literal message is fixed
 * text the mod wrote. The forbidden tokens are the ways an identity or a place reaches a value.
 */
class LogPrivacySweepTest {

    private static final Pattern LOG_CALL =
            Pattern.compile("\\b(?:LOGGER|LOG|logger|log)\\s*\\.\\s*(info|warn|error)\\s*\\(");

    /** Tokens that would put an identity, a place or free text into a log line. */
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(?:UUID|getUUID|getStringUUID|uuid|"
            + "getName|getGameProfile|getScoreboardName|getDisplayName|getPlainTextName|player|players|owner|"
            + "ownerId|linkOwner|lastOperator|BlockPos|getBlockPos|pos|getX|getY|getZ|asLong|ChunkPos|"
            + "toShortString|getString|getIpAddress|getRemoteAddress)\\b");

    private static final Pattern STDOUT = Pattern.compile("System\\s*\\.\\s*(?:out|err)\\b|printStackTrace\\s*\\(");

    @Test
    void noLogCallAtInfoOrAbovePassesIdentityPositionOrFreeText() throws IOException {
        List<String> violations = new ArrayList<>();
        int calls = 0;
        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = LOG_CALL.matcher(text);
            while (m.find()) {
                calls++;
                String args = codeOnly(argumentsFrom(text, m.end()));
                Matcher bad = FORBIDDEN.matcher(args);
                if (bad.find()) {
                    violations.add(where(file, text, m.start()) + " passes '" + bad.group() + "'");
                }
            }
            Matcher out = STDOUT.matcher(codeOnly(stripComments(text)));
            if (out.find()) {
                violations.add(file.getFileName() + " writes to " + out.group());
            }
        }
        assertTrue(calls >= 10, "the sweep found only " + calls + " logger calls; is the source root right?");
        assertTrue(violations.isEmpty(), "identity, position or free text in logs:\n" + String.join("\n", violations));
    }

    /** The detector itself: a planted call with a UUID argument is caught, a literal-only one is not. */
    @Test
    void theDetectorCatchesAPlantedLeak() {
        String leak = "LOGGER.info(\"[NeroPower] linked by {}\", player.getUUID());";
        Matcher m = LOG_CALL.matcher(leak);
        assertTrue(m.find());
        assertTrue(FORBIDDEN.matcher(codeOnly(argumentsFrom(leak, m.end()))).find());

        String fine = "LOGGER.info(\"[NeroPower] the player UUID is never logged\");";
        Matcher f = LOG_CALL.matcher(fine);
        assertTrue(f.find());
        assertTrue(!FORBIDDEN.matcher(codeOnly(argumentsFrom(fine, f.end()))).find());
    }

    // --- helpers ---------------------------------------------------------------------------------

    private static List<Path> sources() throws IOException {
        Path root = repoRoot();
        List<Path> files = new ArrayList<>();
        for (String module : List.of("common", "fabric", "neoforge", "forge")) {
            Path dir = root.resolve(module).resolve("src").resolve("main").resolve("java");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        assertTrue(files.size() > 50, "too few sources found under " + root);
        return files;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("stonecutter.gradle")) && Files.isDirectory(dir.resolve("common"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new UncheckedIOException(new IOException("repository root not found above the test directory"));
    }

    /** The text from just after a call's opening parenthesis to its matching close. */
    static String argumentsFrom(String text, int start) {
        int depth = 1;
        boolean inString = false;
        boolean inChar = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString || inChar) {
                if (c == '\\') {
                    i++;
                } else if (inString && c == '"') {
                    inString = false;
                } else if (inChar && c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return text.substring(start, i);
            }
        }
        return text.substring(start);
    }

    /** Blank out string and char literals so only code is checked. */
    static String codeOnly(String code) {
        StringBuilder sb = new StringBuilder(code.length());
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (inString || inChar) {
                if (c == '\\') {
                    i++;
                } else if ((inString && c == '"') || (inChar && c == '\'')) {
                    inString = false;
                    inChar = false;
                    sb.append(c);
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ");
    }

    private static String where(Path file, String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return file.getFileName() + ":" + line;
    }
}

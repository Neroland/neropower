package za.co.neroland.neropower.failure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** One rule for every screen: a menu that takes button presses asks the protection check before acting. */
class MenuProtectionTest {

    @Test
    void everyMenuButtonAsksTheProtectionCheck() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("stonecutter.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "repository root not found");
        Path main = dir.resolve("common/src/main/java/za/co/neroland/neropower");
        List<String> missing = new ArrayList<>();
        int menus = 0;
        try (Stream<Path> files = Files.walk(main)) {
            for (Path p : files.filter(f -> f.toString().endsWith("Menu.java")).toList()) {
                String s = Files.readString(p, StandardCharsets.UTF_8);
                if (!s.contains("public boolean clickMenuButton(")) {
                    continue;
                }
                menus++;
                if (!s.contains("Protection.get().mayInteract(") && !s.contains("Protection.mayUse(")) {
                    missing.add(main.relativize(p).toString());
                }
            }
        }
        assertTrue(menus >= 7, "found " + menus + " menus with buttons");
        assertTrue(missing.isEmpty(), "menus acting without the protection check: " + missing);
    }
}

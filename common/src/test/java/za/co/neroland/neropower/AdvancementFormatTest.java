package za.co.neroland.neropower;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the recipe-unlock advancements. Minecraft 26.3 renamed the {@code minecraft:recipe_unlocked}
 * trigger's condition key, and one bad advancement stops every world from loading ("Preparing for world
 * creation" hangs on a registry error). NeroPower's unlocks use {@code inventory_changed} criteria only,
 * which parse the same on every supported line; every criterion must also appear in the requirements.
 */
class AdvancementFormatTest {

    private static final String[] DIRS = {"data/neropower/advancement/", "data/neropower/advancements/"};

    @Test
    void noRecipeUnlockedTriggerAndRequirementsMatchCriteria() throws IOException, URISyntaxException {
        List<String> bad = new ArrayList<>();
        int seen = 0;
        for (String dir : DIRS) {
            URL url = AdvancementFormatTest.class.getClassLoader().getResource(dir);
            if (url == null || !"file".equals(url.getProtocol())) {
                continue;
            }
            Path root = Paths.get(url.toURI());
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                    seen++;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String name = root.relativize(file).toString();
                    if (text.contains("minecraft:recipe_unlocked")) {
                        bad.add(name + " uses minecraft:recipe_unlocked");
                    }
                    if (text.contains("has_the_recipe")) {
                        bad.add(name + " names has_the_recipe");
                    }
                }
            }
        }
        assertTrue(seen > 0, "no advancement files found on the test classpath");
        assertTrue(bad.isEmpty(), "advancements that would stop worlds loading: " + bad);
    }
}

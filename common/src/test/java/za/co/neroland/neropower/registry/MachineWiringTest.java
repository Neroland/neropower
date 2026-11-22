package za.co.neroland.neropower.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every NeroPower machine is wired the same way on all three loaders, because each loader's capability code
 * (NeroTech's) reads NeroTech's {@code MachineTypeRegistry}, which only {@link ModBlockEntities} feeds. This
 * test runs in every cell (so once per loader and line) and checks the source the cell compiled:
 * <ul>
 * <li>every concrete block entity sets up a side config, and every {@code SideConfig.builder()} chain names a
 * {@code defaultPreset} (Core's builder defaults to all-disabled, which silently cuts a machine off);</li>
 * <li>every capability list a content holder declares ({@code energyTypes}, {@code itemTypes},
 * {@code fluidTypes}, {@code gasTypes}) is aggregated by {@link ModBlockEntities} and so registered.</li>
 * </ul>
 */
class MachineWiringTest {

    private static final Pattern CLASS = Pattern.compile("public (abstract )?class (\\w+BlockEntity) extends (\\w+)");
    private static final Pattern BUILDER = Pattern.compile("SideConfig\\.builder\\(\\)(.*?)\\.build\\(\\)", Pattern.DOTALL);
    private static final Pattern LIST = Pattern.compile(
            "public static List<Supplier<BlockEntityType<\\? extends NeroTechMachineBlockEntity>>> (energyTypes|itemTypes|fluidTypes|gasTypes)\\(\\)");

    private static Path sourceRoot() {
        Path at = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (Path p = at; p != null; p = p.getParent()) {
            Path candidate = p.resolve("common/src/main/java/za/co/neroland/neropower");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("common sources not found above " + at);
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void everyMachineHasASideConfigWithADefaultPreset() throws IOException {
        List<String> problems = new ArrayList<>();
        java.util.Map<String, String> parentOf = new java.util.HashMap<>();
        java.util.Map<String, Boolean> setsUp = new java.util.HashMap<>();
        java.util.Set<String> concrete = new java.util.TreeSet<>();
        for (Path file : javaFiles(sourceRoot())) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher cls = CLASS.matcher(text);
            if (cls.find()) {
                parentOf.put(cls.group(2), cls.group(3));
                setsUp.put(cls.group(2), text.contains("setupSideConfig("));
                if (cls.group(1) == null) {
                    concrete.add(cls.group(2));
                }
            }
            Matcher chain = BUILDER.matcher(text);
            while (chain.find()) {
                if (!chain.group(1).contains(".defaultPreset(")) {
                    problems.add(file.getFileName() + ": SideConfig.builder() without defaultPreset");
                }
            }
        }
        for (String be : concrete) {
            boolean ok = false;
            for (String c = be; c != null && !ok; c = parentOf.get(c)) {
                ok = Boolean.TRUE.equals(setsUp.get(c));
            }
            if (!ok) {
                problems.add(be + ": no setupSideConfig in it or a NeroPower superclass");
            }
        }
        assertFalse(concrete.isEmpty(), "no block entities found");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void everyCapabilityListIsRegistered() throws IOException {
        Path root = sourceRoot();
        String aggregator = Files.readString(root.resolve("registry/ModBlockEntities.java"), StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(root.resolve("registry/content"))) {
            for (Path file : files.filter(f -> f.toString().endsWith("Content.java")).toList()) {
                String name = file.getFileName().toString().replace(".java", "");
                Matcher list = LIST.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (list.find()) {
                    String call = name + "." + list.group(1) + "()";
                    if (!aggregator.contains(call)) {
                        missing.add(call);
                    }
                }
            }
        }
        for (String surface : List.of("registerEnergy", "registerItem", "registerFluid", "registerGas")) {
            assertTrue(aggregator.contains("MachineTypeRegistry::" + surface), "ModBlockEntities never calls " + surface);
        }
        assertTrue(missing.isEmpty(), "not aggregated by ModBlockEntities: " + missing);
    }
}

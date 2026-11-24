package za.co.neroland.neropower;

public final class TestResourcePaths {
    private TestResourcePaths() { }

    public static String packPath(String path) {
        //? if >=1.21 {
        return path;
        //?} else {
        /*return path.replaceFirst("^(data/[^/]+/)(advancement|loot_table|recipe)(?=/|$)", "$1$2s")
                .replaceFirst("^(data/[^/]+/tags/)(block|entity_type|fluid|item)(?=/|$)", "$1$2s");
        *///?}
    }
}

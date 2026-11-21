package za.co.neroland.neropower.fluid;

/**
 * NeroPower's three transport fluids and their physical attributes. They are not placeable: no flowing
 * variant, no liquid block, no bucket. The attributes feed the loaders' fluid types (NeoForge / Forge)
 * and the client fluid models; the tint colours the stub sprites until the final art lands.
 */
public enum FluidSpec {

    /** Live steam ({@code c:steam}). */
    STEAM("steam", true, 500, -100, 200, 0xFFE8ECEF),
    /** Spent steam leaving a turbine ({@code neropower:exhaust_steam}, never in {@code c:steam}). */
    EXHAUST_STEAM("exhaust_steam", true, 375, -50, 300, 0xFFB9C2C8),
    /** Heavy water ({@code c:heavy_water}). */
    HEAVY_WATER("heavy_water", false, 300, 1100, 1200, 0xFF2E6BAA);

    private final String id;
    private final boolean gaseous;
    private final int temperature;
    private final int density;
    private final int viscosity;
    private final int tint;

    FluidSpec(String id, boolean gaseous, int temperature, int density, int viscosity, int tint) {
        this.id = id;
        this.gaseous = gaseous;
        this.temperature = temperature;
        this.density = density;
        this.viscosity = viscosity;
        this.tint = tint;
    }

    /** Registry path under {@code neropower:}. */
    public String id() {
        return this.id;
    }

    public boolean gaseous() {
        return this.gaseous;
    }

    /** Kelvin. */
    public int temperature() {
        return this.temperature;
    }

    public int density() {
        return this.density;
    }

    public int viscosity() {
        return this.viscosity;
    }

    /** ARGB tint for the fluid model. */
    public int tint() {
        return this.tint;
    }

    /** Still sprite, relative to {@code textures/} ({@code block/fluid/<id>_still}). */
    public String stillTexture() {
        return "block/fluid/" + this.id + "_still";
    }

    /** Flowing sprite, relative to {@code textures/}. */
    public String flowTexture() {
        return "block/fluid/" + this.id + "_flow";
    }

    /** Lang key of the fluid's name. */
    public String translationKey() {
        return "fluid.neropower." + this.id;
    }
}

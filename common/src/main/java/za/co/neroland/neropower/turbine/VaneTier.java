package za.co.neroland.neropower.turbine;

import net.minecraft.util.StringRepresentable;

/** The {@code vanes} block-state value of a Turbine Spindle: which vane set rides it, if any. */
public enum VaneTier implements StringRepresentable {
    NONE("none"),
    STEEL("steel"),
    CINDERSTEEL("cindersteel"),
    SUPERCONDUCTING("superconducting");

    private final String name;

    VaneTier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

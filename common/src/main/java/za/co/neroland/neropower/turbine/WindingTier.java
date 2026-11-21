package za.co.neroland.neropower.turbine;

import net.minecraft.util.StringRepresentable;

/** The {@code winding} block-state value of a Turbine Stator: its coil tier, or bare laminations. */
public enum WindingTier implements StringRepresentable {
    NONE("none"),
    TIER1("tier1"),
    TIER2("tier2"),
    TIER3("tier3");

    private final String name;

    WindingTier(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}

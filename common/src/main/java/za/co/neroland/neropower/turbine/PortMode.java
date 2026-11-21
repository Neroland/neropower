package za.co.neroland.neropower.turbine;

import net.minecraft.util.StringRepresentable;

/**
 * The {@code port_mode} block-state value of a Turbine Steam Port: live steam in, exhaust steam out to a
 * pipe or condenser, or exhaust vented to the air (at {@code turbineVentEfficiencyPermille} of the yield).
 */
public enum PortMode implements StringRepresentable {
    INLET("inlet"),
    EXHAUST("exhaust"),
    VENT("vent");

    private final String name;

    PortMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /** The next mode in the right-click cycle. */
    public PortMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}

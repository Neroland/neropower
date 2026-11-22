package za.co.neroland.neropower.turbine;

import za.co.neroland.neropower.data.SaveKeys;

/**
 * The saved form of one port tank, shared by Reactor Steam Ports, Turbine Steam Ports and the Steam
 * Condenser: {@code <prefix>Fluid} (registry id) and {@code <prefix>Amount} (mB), nothing when empty.
 * The shared tank keeps each port's own tank as the storage behind the structure's shared tank, so these keys
 * are unchanged and a pre-pooling save loads in place: its per-port contents are the shared tank's
 * contents on the first tick, with nothing to move and nothing lost. Pure JVM.
 */
public record PortTankSave(String fluid, int amount) {

    public static final String FLUID = "Fluid";
    public static final String AMOUNT = "Amount";

    /** Reactor Steam Port prefixes. */
    public static final String REACTOR_COOLANT = "Coolant";
    public static final String REACTOR_STEAM = "Steam";
    /** Turbine Steam Port prefixes. */
    public static final String TURBINE_STEAM = "Steam";
    public static final String TURBINE_EXHAUST = "Exhaust";

    public static final PortTankSave EMPTY = new PortTankSave("", 0);

    public boolean empty() {
        return this.fluid.isEmpty() || this.amount <= 0;
    }

    /** Read {@code prefix}; a missing id or a non-positive amount reads as empty. */
    public static PortTankSave read(SaveKeys.Reader in, String prefix) {
        String fluid = in.getString(prefix + FLUID, "");
        int amount = in.getInt(prefix + AMOUNT, 0);
        return fluid == null || fluid.isEmpty() || amount <= 0 ? EMPTY : new PortTankSave(fluid, amount);
    }

    /** Write {@code prefix}; nothing when empty (as the tanks do). */
    public void write(SaveKeys.Writer out, String prefix) {
        if (empty()) {
            return;
        }
        out.putString(prefix + FLUID, this.fluid);
        out.putInt(prefix + AMOUNT, this.amount);
    }
}

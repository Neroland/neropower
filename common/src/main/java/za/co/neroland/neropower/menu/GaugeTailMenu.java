package za.co.neroland.neropower.menu;

/**
 * A NeroPower machine menu that appends a {@link GaugeTail}, read by
 * {@code za.co.neroland.neropower.client.PowerMachineScreen}. Stored energy, capacity, heat and the safe
 * band come from NeroTech's {@code MachineMenu} tail (its public getters); this interface only adds what
 * that tail cannot carry.
 */
public interface GaugeTailMenu {

    /** The tail added after NeroTech's shared data and tail. */
    GaugeTail gaugeTail();

    /** The machine-specific amount (see the menu). */
    default long gaugeExtra() {
        return gaugeTail().value(GaugeTail.EXTRA);
    }

    /** Stored energy in NE beyond NeroTech's 2^30 cap (storage screens only; 0 elsewhere). */
    default long wideEnergyStored() {
        return gaugeTail().value(GaugeTail.WIDE_ENERGY);
    }

    /** Energy capacity in NE beyond NeroTech's 2^30 cap (storage screens only; 0 elsewhere). */
    default long wideEnergyCapacity() {
        return gaugeTail().value(GaugeTail.WIDE_CAPACITY);
    }
}

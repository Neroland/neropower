package za.co.neroland.neropower.storage;

import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

import za.co.neroland.neropower.energy.PortableEnergy;

/**
 * The one charge slot a Battery Cell and a Surge Bank carry (decision 008; appended slot, so a save
 * from before 0.4.0 loads with it empty). Each tick it moves energy from the block's own buffer into the
 * portable energy item in the slot: never more than the block's rate, the item's rate, what the buffer
 * holds or the room on the item. Exactly the NE the item accepted leaves the buffer, so nothing is made.
 */
public final class ChargeSlot {

    /** The charge slot's container index (the first and only machine slot). */
    public static final int SLOT = 0;

    private ChargeSlot() {
    }

    /** Whether {@code stack} may sit in the charge slot. */
    public static boolean accepts(ItemStack stack) {
        return PortableEnergy.holds(stack);
    }

    /** Whether an automation face may take the stack out: only once it is full. */
    public static boolean canTake(ItemStack stack) {
        return !PortableEnergy.holds(stack) || PortableEnergy.stored(stack) >= PortableEnergy.capacity(stack);
    }

    /**
     * Charge {@code stack} from {@code source} at up to {@code rate} NE this tick.
     *
     * @return NE moved
     */
    public static long charge(ItemStack stack, NeroEnergyStorage source, long rate) {
        if (rate <= 0 || !PortableEnergy.holds(stack)) {
            return 0L;
        }
        long offer = source.extract(rate, true);
        if (offer <= 0) {
            return 0L;
        }
        long accepted = PortableEnergy.insert(stack, offer, true);
        if (accepted <= 0) {
            return 0L;
        }
        long taken = source.extract(accepted, false);
        if (taken > 0) {
            PortableEnergy.insert(stack, taken, false);
        }
        return taken;
    }
}

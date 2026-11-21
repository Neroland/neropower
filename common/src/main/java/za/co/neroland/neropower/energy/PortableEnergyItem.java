package za.co.neroland.neropower.energy;

import net.minecraft.world.item.ItemStack;

/**
 * An item that keeps NE in the {@code neropower:cell_energy} component and may be charged or drawn
 * through {@link PortableEnergy}: the three power cells, and any NeroPower tool that wants Induction
 * Charger top-ups. The item declares its own capacity and rate; the charge lives on the stack only.
 */
public interface PortableEnergyItem {

    /** NE the stack can hold. */
    long portableCapacity(ItemStack stack);

    /** NE the stack may take or give in one transfer (one tick). */
    long portableIo(ItemStack stack);
}

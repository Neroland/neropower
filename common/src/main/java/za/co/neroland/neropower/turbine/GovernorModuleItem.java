package za.co.neroland.neropower.turbine;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Governor Module: an upgrade card for the Turbine Helm only. It sits in one of the helm's Core upgrade
 * slots, but Core's classifier does not know it (Core's upgrade types are a closed set), so Core never
 * counts it; the helm reads it itself ({@link #fitted}). Fitted, the rotor never targets above rated
 * speed: steam the stators and buffer cannot take is bled to the exhaust instead of running the rotor
 * into an overspeed trip.
 */
public class GovernorModuleItem extends Item {

    public GovernorModuleItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code stack} is a Governor Module. */
    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GovernorModuleItem;
    }
}

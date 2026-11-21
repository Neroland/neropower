package za.co.neroland.neropower.radiation;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neropower.registry.ModTags;

/**
 * Counts the worn armour pieces that shield from radiation (decision 007). A piece counts when it is
 * in {@code #neropower:radiation_shielding}; if it is also in {@code #neropower:radiation_shielding_lined}
 * it counts only while it carries the component registered as {@code nerospace:rad_lining}. That
 * component is resolved once by id through the data-component registry: no Nerospace class, no
 * reflection, and when Nerospace is absent nothing in the lined tag ever counts.
 */
public final class ShieldingArmour {

    /** Nerospace's suit lining component, looked up by id only. */
    public static final Identifier RAD_LINING_ID = Identifier.fromNamespaceAndPath("nerospace", "rad_lining");

    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static volatile boolean resolved;
    private static volatile DataComponentType<?> lining;

    private ShieldingArmour() {
    }

    /** Shielding pieces {@code entity} wears (0..4). */
    public static int pieces(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : ARMOUR) {
            if (counts(entity.getItemBySlot(slot))) {
                n++;
            }
        }
        return n;
    }

    /** Whether one stack counts as a shielding piece. */
    public static boolean counts(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ModTags.RADIATION_SHIELDING)) {
            return false;
        }
        if (!stack.is(ModTags.RADIATION_SHIELDING_LINED)) {
            return true;
        }
        DataComponentType<?> type = lining();
        return type != null && stack.has(type);
    }

    private static DataComponentType<?> lining() {
        if (!resolved) {
            lining = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(RAD_LINING_ID);
            resolved = true;
        }
        return lining;
    }
}

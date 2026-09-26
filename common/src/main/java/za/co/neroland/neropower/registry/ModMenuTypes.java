package za.co.neroland.neropower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Container menu types for NeroPower's machines, registered cross-loader via {@link RegistrationProvider}.
 * Empty until Stage 4: menus are {@code new MenuType<>(XMenu::new, FeatureFlags.VANILLA_SET)} built on
 * NeroTech's {@code MachineMenu} / {@code MachineScreen} with the documented {@code extraData} hook; the
 * matching screens are registered by each loader's client setup.
 */
public final class ModMenuTypes {

    public static final RegistrationProvider<MenuType<?>> MENUS =
            RegistrationProvider.get(Registries.MENU, NeroPowerCommon.MOD_ID);

    private ModMenuTypes() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}

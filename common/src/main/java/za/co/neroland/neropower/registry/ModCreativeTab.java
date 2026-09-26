package za.co.neroland.neropower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower's own dedicated creative tab, registered cross-loader via {@link RegistrationProvider} over
 * the vanilla {@code CREATIVE_MODE_TAB} registry (the ecosystem convention: each mod gets its own tab,
 * none pours into Core's shared "Neroland" tab).
 */
public final class ModCreativeTab {

    public static final RegistrationProvider<CreativeModeTab> TABS =
            RegistrationProvider.get(Registries.CREATIVE_MODE_TAB, NeroPowerCommon.MOD_ID);

    // NOTE: vanilla CreativeModeTab.builder takes (Row, column); the no-arg overload and
    // withTabsBefore/After are NeoForge-only extensions, so they are avoided here (common = raw vanilla).
    public static final RegistryEntry<CreativeModeTab> NEROPOWER = TABS.register("neropower",
            key -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.neropower"))
                    .icon(() -> new ItemStack(ModItems.CONTROL_ROD.get()))
                    .displayItems((params, output) -> ModItems.creativeContents().forEach(output::accept))
                    .build());

    private ModCreativeTab() {
    }

    /** Force class-load so the static tab registration runs. */
    public static void init() {
    }
}

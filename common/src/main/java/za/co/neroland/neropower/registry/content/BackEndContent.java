package za.co.neroland.neropower.registry.content;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.backend.DryCaskBlock;
import za.co.neroland.neropower.backend.DryCaskBlockEntity;
import za.co.neroland.neropower.backend.DryCaskMenu;
import za.co.neroland.neropower.backend.SpentFuelPoolBlock;
import za.co.neroland.neropower.backend.SpentFuelPoolBlockEntity;
import za.co.neroland.neropower.backend.SpentFuelPoolMenu;
import za.co.neroland.neropower.backend.VitrifierBlock;
import za.co.neroland.neropower.backend.VitrifierBlockEntity;
import za.co.neroland.neropower.backend.VitrifierMenu;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * The waste back end — Spent Fuel Rack ({@code spent_fuel_pool}), Vitrifier and Dry Cask, their block
 * items, block-entity types and menus, plus the back-end items ({@code fission_residue},
 * {@code vitrified_waste}, {@code beta_capsule}, {@code spent_beta_capsule}) — registered through
 * NeroPower's shared providers. {@code NeroPowerCommon.init()} calls {@link #init()}.
 *
 * <p>Client wiring (each loader's client setup):
 * <ul>
 *   <li>{@link #SPENT_FUEL_POOL_MENU} → {@code backend.client.SpentFuelPoolScreen}</li>
 *   <li>{@link #VITRIFIER_MENU} → {@code backend.client.VitrifierScreen}</li>
 *   <li>{@link #DRY_CASK_MENU} → {@code backend.client.DryCaskScreen}</li>
 * </ul>
 *
 * <p>Capability wiring: {@link #energyTypes()} (the Vitrifier) and {@link #itemTypes()} (all three).
 */
public final class BackEndContent {

    /** The core's output, cooled only in a Spent Fuel Rack (registered by the fission package). */
    public static final Identifier HOT_SPENT_FUEL_ROD_ID =
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "hot_spent_fuel_rod");

    // --- tags ----------------------------------------------------------------------------------------
    /** What the Vitrifier accepts: fission residue, spent beta capsules, spent isotope pellets. */
    public static final TagKey<Item> VITRIFIABLE = itemTag("vitrifiable");
    /** What the Dry Cask accepts: spent fuel rods, fission residue, vitrified waste. */
    public static final TagKey<Item> CASK_STORABLE = itemTag("cask_storable");

    // --- blocks --------------------------------------------------------------------------------------
    public static final RegistryEntry<SpentFuelPoolBlock> SPENT_FUEL_POOL =
            ModBlocks.register("spent_fuel_pool", SpentFuelPoolBlock::new);
    public static final RegistryEntry<VitrifierBlock> VITRIFIER =
            ModBlocks.register("vitrifier", VitrifierBlock::new);
    public static final RegistryEntry<DryCaskBlock> DRY_CASK =
            ModBlocks.register("dry_cask", DryCaskBlock::new);

    // --- block items ---------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> SPENT_FUEL_POOL_ITEM = ModItems.ITEMS.register("spent_fuel_pool",
            key -> new BlockItem(SPENT_FUEL_POOL.get(), new Item.Properties().setId(key)));
    public static final RegistryEntry<BlockItem> VITRIFIER_ITEM = ModItems.ITEMS.register("vitrifier",
            key -> new BlockItem(VITRIFIER.get(), new Item.Properties().setId(key)));
    public static final RegistryEntry<BlockItem> DRY_CASK_ITEM = ModItems.ITEMS.register("dry_cask",
            key -> new BlockItem(DRY_CASK.get(), new Item.Properties().setId(key)));

    // --- items ---------------------------------------------------------------------------------------
    /** The final waste of reprocessing: vitrify it, cask it, or spin it into beta capsules. */
    public static final RegistryEntry<Item> FISSION_RESIDUE = item("fission_residue");
    /** Inert end product of the Vitrifier. No recipe takes it; it is the one waste that may be discarded. */
    public static final RegistryEntry<Item> VITRIFIED_WASTE = item("vitrified_waste");
    /** Betavoltaic Cell fuel, centrifuged from fission residue. */
    public static final RegistryEntry<Item> BETA_CAPSULE = item("beta_capsule");
    /** What a Betavoltaic Cell hands back; vitrify it. */
    public static final RegistryEntry<Item> SPENT_BETA_CAPSULE = item("spent_beta_capsule");

    // --- block entities ------------------------------------------------------------------------------
    public static final RegistryEntry<BlockEntityType<SpentFuelPoolBlockEntity>> SPENT_FUEL_POOL_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("spent_fuel_pool",
                    key -> BlockEntityTypeCompat.create(SpentFuelPoolBlockEntity::new, SPENT_FUEL_POOL.get()));
    public static final RegistryEntry<BlockEntityType<VitrifierBlockEntity>> VITRIFIER_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("vitrifier",
                    key -> BlockEntityTypeCompat.create(VitrifierBlockEntity::new, VITRIFIER.get()));
    public static final RegistryEntry<BlockEntityType<DryCaskBlockEntity>> DRY_CASK_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("dry_cask",
                    key -> BlockEntityTypeCompat.create(DryCaskBlockEntity::new, DRY_CASK.get()));

    // --- menus ---------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<SpentFuelPoolMenu>> SPENT_FUEL_POOL_MENU =
            ModMenuTypes.MENUS.register("spent_fuel_pool",
                    key -> new MenuType<>(SpentFuelPoolMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<VitrifierMenu>> VITRIFIER_MENU =
            ModMenuTypes.MENUS.register("vitrifier",
                    key -> new MenuType<>(VitrifierMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<DryCaskMenu>> DRY_CASK_MENU =
            ModMenuTypes.MENUS.register("dry_cask",
                    key -> new MenuType<>(DryCaskMenu::new, FeatureFlags.VANILLA_SET));

    private BackEndContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** Only the Vitrifier draws power. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(VITRIFIER_TYPE::get);
    }

    /** All three have slots pipes and hoppers reach. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(SPENT_FUEL_POOL_TYPE::get, VITRIFIER_TYPE::get, DRY_CASK_TYPE::get);
    }

    /** Back-end blocks in creative-tab order (family "Back end"). */
    public static List<RegistryEntry<? extends ItemLike>> creativeBlocks() {
        return List.of(SPENT_FUEL_POOL_ITEM, VITRIFIER_ITEM, DRY_CASK_ITEM);
    }

    /** Back-end items in creative-tab order (the "fuel and waste items" group). */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(FISSION_RESIDUE, VITRIFIED_WASTE, BETA_CAPSULE, SPENT_BETA_CAPSULE);
    }

    private static RegistryEntry<Item> item(String name) {
        return ModItems.ITEMS.register(name, key -> new Item(new Item.Properties().setId(key)));
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path));
    }
}

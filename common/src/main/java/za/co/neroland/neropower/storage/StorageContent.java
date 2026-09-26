package za.co.neroland.neropower.storage;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * Every registration of the Stage 5 storage tier — blocks, block items, block-entity types and menu
 * types — through NeroPower's shared providers, in the order the eager (Fabric) loader needs:
 * blocks, then items, then the types that reference the blocks.
 *
 * <p><b>Client registration</b> (loader client setup): the (MenuType, Screen) pairs are
 * <ul>
 *   <li>{@link #BATTERY_CELL_MENU} → {@code storage.client.BatteryCellScreen}</li>
 *   <li>{@link #BATTERY_BANK_CONTROLLER_MENU} → {@code storage.client.BankControllerScreen}</li>
 * </ul>
 * i.e. {@code MenuScreens.register(StorageContent.BATTERY_CELL_MENU.get(), BatteryCellScreen::new)} and
 * {@code MenuScreens.register(StorageContent.BATTERY_BANK_CONTROLLER_MENU.get(), BankControllerScreen::new)}.
 *
 * <p><b>Common wiring:</b> {@code NeroPowerCommon.init()} calls {@link StorageConfig#init()} before
 * {@code NeroPowerConfig.load()} and {@link #init()} after {@code ModRegistries.init()};
 * {@code ModBlockEntities.energyMachineTypes()} appends {@link #energyTypes()} (the storage blocks
 * have no item slots, so {@link #itemTypes()} is empty); {@code ModItems.creativeOrder()} appends
 * {@link #creativeItems()}.
 */
public final class StorageContent {

    // --- blocks ------------------------------------------------------------------------------------
    public static final RegistryEntry<BatteryCellBlock> BATTERY_CELL_BASIC =
            ModBlocks.register("battery_cell_basic", props -> new BatteryCellBlock(props, BatteryTier.BASIC));
    public static final RegistryEntry<BatteryCellBlock> BATTERY_CELL_ADVANCED =
            ModBlocks.register("battery_cell_advanced", props -> new BatteryCellBlock(props, BatteryTier.ADVANCED));
    public static final RegistryEntry<BatteryCellBlock> BATTERY_CELL_ELITE =
            ModBlocks.register("battery_cell_elite", props -> new BatteryCellBlock(props, BatteryTier.ELITE));
    public static final RegistryEntry<BankControllerBlock> BATTERY_BANK_CONTROLLER_BLOCK =
            ModBlocks.register("battery_bank_controller", BankControllerBlock::new);

    // --- block items ---------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> BATTERY_CELL_BASIC_ITEM =
            blockItem("battery_cell_basic", BATTERY_CELL_BASIC);
    public static final RegistryEntry<BlockItem> BATTERY_CELL_ADVANCED_ITEM =
            blockItem("battery_cell_advanced", BATTERY_CELL_ADVANCED);
    public static final RegistryEntry<BlockItem> BATTERY_CELL_ELITE_ITEM =
            blockItem("battery_cell_elite", BATTERY_CELL_ELITE);
    public static final RegistryEntry<BlockItem> BATTERY_BANK_CONTROLLER_ITEM =
            blockItem("battery_bank_controller", BATTERY_BANK_CONTROLLER_BLOCK);

    // --- block entities (one cell type serves all three tiers; the BE reads its tier from the block) ---
    public static final RegistryEntry<BlockEntityType<BatteryCellBlockEntity>> BATTERY_CELL =
            ModBlockEntities.BLOCK_ENTITIES.register("battery_cell",
                    key -> new BlockEntityType<>(BatteryCellBlockEntity::new, Set.of(
                            BATTERY_CELL_BASIC.get(), BATTERY_CELL_ADVANCED.get(), BATTERY_CELL_ELITE.get())));
    public static final RegistryEntry<BlockEntityType<BankControllerBlockEntity>> BATTERY_BANK_CONTROLLER =
            ModBlockEntities.BLOCK_ENTITIES.register("battery_bank_controller",
                    key -> new BlockEntityType<>(BankControllerBlockEntity::new,
                            Set.of(BATTERY_BANK_CONTROLLER_BLOCK.get())));

    // --- menus -----------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<BatteryCellMenu>> BATTERY_CELL_MENU =
            ModMenuTypes.MENUS.register("battery_cell",
                    key -> new MenuType<>(BatteryCellMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<BankControllerMenu>> BATTERY_BANK_CONTROLLER_MENU =
            ModMenuTypes.MENUS.register("battery_bank_controller",
                    key -> new MenuType<>(BankControllerMenu::new, FeatureFlags.VANILLA_SET));

    private StorageContent() {
    }

    /** NeroTech's block-item recipe: {@code new BlockItem(block, new Item.Properties().setId(key))}. */
    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** The storage block-entity types for NeroTech's energy capability surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(BATTERY_CELL::get, BATTERY_BANK_CONTROLLER::get);
    }

    /** Storage blocks have no item slots: nothing on the item surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of();
    }

    /** Creative-tab order: the three cells by tier, then the controller. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(BATTERY_CELL_BASIC_ITEM, BATTERY_CELL_ADVANCED_ITEM, BATTERY_CELL_ELITE_ITEM,
                BATTERY_BANK_CONTROLLER_ITEM);
    }
}

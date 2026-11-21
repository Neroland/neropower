package za.co.neroland.neropower.registry.content;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.generation.BetavoltaicCellBlock;
import za.co.neroland.neropower.generation.BetavoltaicCellBlockEntity;
import za.co.neroland.neropower.generation.BetavoltaicCellMenu;
import za.co.neroland.neropower.generation.FuelCellBlock;
import za.co.neroland.neropower.generation.FuelCellBlockEntity;
import za.co.neroland.neropower.generation.FuelCellMenu;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * The small generators — Hydrogen Fuel Cell ({@code fuel_cell}) and Betavoltaic Cell
 * ({@code betavoltaic_cell}) — their block items, block-entity types and menus, registered through
 * NeroPower's shared providers. The capsules themselves are registered by {@link BackEndContent}.
 * {@code NeroPowerCommon.init()} calls {@link #init()}.
 *
 * <p>Client wiring (each loader's client setup):
 * <ul>
 *   <li>{@link #FUEL_CELL_MENU} → {@code generation.client.FuelCellScreen}</li>
 *   <li>{@link #BETAVOLTAIC_CELL_MENU} → {@code generation.client.BetavoltaicCellScreen}</li>
 * </ul>
 *
 * <p>Capability wiring: {@link #energyTypes()} (both), {@link #gasTypes()} and {@link #fluidTypes()}
 * (the fuel cell), {@link #itemTypes()} (the betavoltaic cell).
 */
public final class GenerationContent {

    // --- blocks --------------------------------------------------------------------------------------
    public static final RegistryEntry<FuelCellBlock> FUEL_CELL = ModBlocks.register("fuel_cell", FuelCellBlock::new);
    public static final RegistryEntry<BetavoltaicCellBlock> BETAVOLTAIC_CELL =
            ModBlocks.register("betavoltaic_cell", BetavoltaicCellBlock::new);

    // --- block items ---------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> FUEL_CELL_ITEM = ModItems.ITEMS.register("fuel_cell",
            key -> new BlockItem(FUEL_CELL.get(), new Item.Properties().setId(key)));
    public static final RegistryEntry<BlockItem> BETAVOLTAIC_CELL_ITEM = ModItems.ITEMS.register("betavoltaic_cell",
            key -> new BlockItem(BETAVOLTAIC_CELL.get(), new Item.Properties().setId(key)));

    // --- block entities ------------------------------------------------------------------------------
    public static final RegistryEntry<BlockEntityType<FuelCellBlockEntity>> FUEL_CELL_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("fuel_cell",
                    key -> BlockEntityTypeCompat.create(FuelCellBlockEntity::new, FUEL_CELL.get()));
    public static final RegistryEntry<BlockEntityType<BetavoltaicCellBlockEntity>> BETAVOLTAIC_CELL_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("betavoltaic_cell",
                    key -> BlockEntityTypeCompat.create(BetavoltaicCellBlockEntity::new, BETAVOLTAIC_CELL.get()));

    // --- menus ---------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<FuelCellMenu>> FUEL_CELL_MENU =
            ModMenuTypes.MENUS.register("fuel_cell",
                    key -> new MenuType<>(FuelCellMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<BetavoltaicCellMenu>> BETAVOLTAIC_CELL_MENU =
            ModMenuTypes.MENUS.register("betavoltaic_cell",
                    key -> new MenuType<>(BetavoltaicCellMenu::new, FeatureFlags.VANILLA_SET));

    private GenerationContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** Both make power. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(FUEL_CELL_TYPE::get, BETAVOLTAIC_CELL_TYPE::get);
    }

    /** Hydrogen and oxygen in through Core's gas capability. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> gasTypes() {
        return List.of(FUEL_CELL_TYPE::get);
    }

    /** Water out (and hydrogen/oxygen as transport fluids in) on the fluid surfaces. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> fluidTypes() {
        return List.of(FUEL_CELL_TYPE::get);
    }

    /** The capsule slot pipes and hoppers reach. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(BETAVOLTAIC_CELL_TYPE::get);
    }

    /** Generation blocks in creative-tab order (family "Generation"). */
    public static List<RegistryEntry<? extends ItemLike>> creativeBlocks() {
        return List.of(FUEL_CELL_ITEM, BETAVOLTAIC_CELL_ITEM);
    }
}

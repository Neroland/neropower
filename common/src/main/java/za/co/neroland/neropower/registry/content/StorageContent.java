package za.co.neroland.neropower.registry.content;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
//?} else {
/*import za.co.neroland.nerotech.registry.LegacyItemComponent;
*///?}
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerotech.api.WirelessCharging;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.energy.PortableEnergy;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.neropower.storage.MomentumWheelBlock;
import za.co.neroland.neropower.storage.MomentumWheelBlockEntity;
import za.co.neroland.neropower.storage.MomentumWheelMenu;
import za.co.neroland.neropower.storage.PowerCellItem;
import za.co.neroland.neropower.storage.PowerCellTier;
import za.co.neroland.neropower.storage.SmesCoilBlock;
import za.co.neroland.neropower.storage.SmesControllerBlock;
import za.co.neroland.neropower.storage.SmesControllerBlockEntity;
import za.co.neroland.neropower.storage.SmesMenu;
import za.co.neroland.neropower.storage.SurgeBankBlock;
import za.co.neroland.neropower.storage.SurgeBankBlockEntity;
import za.co.neroland.neropower.storage.SurgeBankMenu;

/**
 * Registrations of the 0.4.0 storage package: Surge Bank, Momentum Wheel, Storage Ring
 * Controller and Coil, the three portable Power Cells and the {@code neropower:cell_energy} component, in
 * the order the eager (Fabric) loader needs: component, blocks, items, block-entity types, menus.
 *
 * <p><b>Wiring (integration):</b> call {@link #init()} from {@code NeroPowerCommon.init()} after
 * {@code ModRegistries.init()} and before {@code ModBlockEntities.registerMachineTypes()}; it also
 * registers the Induction Charger adapter once. Append {@link #energyTypes()} to the energy surface,
 * {@link #itemTypes()} to the item surface, {@link #fluidTypes()} to NeroTech's FLUID surface
 * ({@code MachineTypeRegistry.registerFluid}) and {@link #creativeItems()} to the creative order. Screens:
 * {@code SURGE_BANK_MENU -> storage.client.SurgeBankScreen}, {@code MOMENTUM_WHEEL_MENU ->
 * storage.client.MomentumWheelScreen}, {@code SMES_CONTROLLER_MENU -> storage.client.SmesScreen}.
 * The existing battery cells and bank controller stay in {@code storage.StorageContent}.
 */
public final class StorageContent {

    // --- data component -----------------------------------------------------------------------------
    //? if >=1.20.5 {
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroPowerCommon.MOD_ID);

    /** NE stored on a portable energy item (power cells; any NeroPower tool that opts in). No player data. */
    public static final RegistryEntry<DataComponentType<Long>> CELL_ENERGY =
            COMPONENTS.register("cell_energy", key -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .build());
    //?} else {
    /*public static final LegacyItemComponent<Long> CELL_ENERGY =
            new LegacyItemComponent<>("neropower:cell_energy", Codec.LONG);
    *///?}


    // --- blocks ----------------------------------------------------------------------------------------
    public static final RegistryEntry<SurgeBankBlock> SURGE_BANK_BLOCK =
            ModBlocks.register("surge_bank", SurgeBankBlock::new);
    public static final RegistryEntry<MomentumWheelBlock> MOMENTUM_WHEEL_BLOCK =
            ModBlocks.register("momentum_wheel", MomentumWheelBlock::new);
    public static final RegistryEntry<SmesControllerBlock> SMES_CONTROLLER_BLOCK =
            ModBlocks.register("smes_controller", SmesControllerBlock::new);
    public static final RegistryEntry<SmesCoilBlock> SMES_COIL_BLOCK =
            ModBlocks.register("smes_coil", SmesCoilBlock::new);

    // --- items -------------------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> SURGE_BANK_ITEM = blockItem("surge_bank", SURGE_BANK_BLOCK);
    public static final RegistryEntry<BlockItem> MOMENTUM_WHEEL_ITEM = blockItem("momentum_wheel", MOMENTUM_WHEEL_BLOCK);
    public static final RegistryEntry<BlockItem> SMES_CONTROLLER_ITEM = blockItem("smes_controller", SMES_CONTROLLER_BLOCK);
    public static final RegistryEntry<BlockItem> SMES_COIL_ITEM = hintItem("smes_coil", SMES_COIL_BLOCK);

    public static final RegistryEntry<PowerCellItem> POWER_CELL_BASIC = powerCell(PowerCellTier.BASIC);
    public static final RegistryEntry<PowerCellItem> POWER_CELL_ADVANCED = powerCell(PowerCellTier.ADVANCED);
    public static final RegistryEntry<PowerCellItem> POWER_CELL_ELITE = powerCell(PowerCellTier.ELITE);

    // --- block entities -----------------------------------------------------------------------------------
    public static final RegistryEntry<BlockEntityType<SurgeBankBlockEntity>> SURGE_BANK =
            ModBlockEntities.BLOCK_ENTITIES.register("surge_bank",
                    key -> BlockEntityTypeCompat.create(SurgeBankBlockEntity::new, SURGE_BANK_BLOCK.get()));
    public static final RegistryEntry<BlockEntityType<MomentumWheelBlockEntity>> MOMENTUM_WHEEL =
            ModBlockEntities.BLOCK_ENTITIES.register("momentum_wheel",
                    key -> BlockEntityTypeCompat.create(MomentumWheelBlockEntity::new, MOMENTUM_WHEEL_BLOCK.get()));
    public static final RegistryEntry<BlockEntityType<SmesControllerBlockEntity>> SMES_CONTROLLER =
            ModBlockEntities.BLOCK_ENTITIES.register("smes_controller",
                    key -> BlockEntityTypeCompat.create(SmesControllerBlockEntity::new, SMES_CONTROLLER_BLOCK.get()));

    // --- menus ------------------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<SurgeBankMenu>> SURGE_BANK_MENU =
            ModMenuTypes.MENUS.register("surge_bank", key -> new MenuType<>(SurgeBankMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<MomentumWheelMenu>> MOMENTUM_WHEEL_MENU =
            ModMenuTypes.MENUS.register("momentum_wheel",
                    key -> new MenuType<>(MomentumWheelMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<SmesMenu>> SMES_CONTROLLER_MENU =
            ModMenuTypes.MENUS.register("smes_controller", key -> new MenuType<>(SmesMenu::new, FeatureFlags.VANILLA_SET));

    private static boolean chargerRegistered;

    private StorageContent() {
    }

    private static RegistryEntry<BlockItem> hintItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new za.co.neroland.neropower.machine.HintBlockItem(block.get(),
                new Item.Properties().setId(key), za.co.neroland.neropower.machine.HintBlockItem.keyFor(name)));
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }

    private static RegistryEntry<PowerCellItem> powerCell(PowerCellTier tier) {
        return ModItems.ITEMS.register("power_cell_" + tier.key(),
                key -> new PowerCellItem(new Item.Properties().setId(key).stacksTo(1), tier));
    }

    /**
     * Force class-load so the static registrations run (eager on Fabric), and register the Induction
     * Charger adapter with NeroTech's {@code api.WirelessCharging} once: it fills any portable energy item
     * (power cells, opted-in NeroPower tools) through {@link PortableEnergy#chargeFe}.
     */
    public static void init() {
        if (!chargerRegistered) {
            chargerRegistered = true;
            WirelessCharging.registerItemCharger(PortableEnergy::chargeFe);
        }
    }

    /** Block-entity types on the energy surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(SURGE_BANK::get, MOMENTUM_WHEEL::get, SMES_CONTROLLER::get);
    }

    /** Block-entity types with slots pipes / hoppers reach: the Surge Bank's charge slot. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(SURGE_BANK::get);
    }

    /** Block-entity types on NeroTech's FLUID surface: the ring controller's cryo coolant tank. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> fluidTypes() {
        return List.of(SMES_CONTROLLER::get);
    }

    /** Creative-tab order (family order: Storage, then cells with the other cells and blades). */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(SURGE_BANK_ITEM, MOMENTUM_WHEEL_ITEM, SMES_CONTROLLER_ITEM, SMES_COIL_ITEM);
    }

    /** The three power cells, for the "cells and blades" group of the creative order. */
    public static List<RegistryEntry<? extends ItemLike>> powerCellItems() {
        return List.of(POWER_CELL_BASIC, POWER_CELL_ADVANCED, POWER_CELL_ELITE);
    }
}

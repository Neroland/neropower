package za.co.neroland.neropower.registry.content;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.multiblock.MultiblockPartBlock;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.neropower.turbine.CondenserBlock;
import za.co.neroland.neropower.turbine.CondenserBlockEntity;
import za.co.neroland.neropower.turbine.CondenserMenu;
import za.co.neroland.neropower.turbine.GovernorModuleItem;
import za.co.neroland.neropower.turbine.TurbineHelmBlock;
import za.co.neroland.neropower.turbine.TurbineHelmBlockEntity;
import za.co.neroland.neropower.turbine.TurbineHelmMenu;
import za.co.neroland.neropower.turbine.TurbineSpindleBlock;
import za.co.neroland.neropower.turbine.TurbineStatorBlock;
import za.co.neroland.neropower.turbine.TurbineSteamPortBlock;
import za.co.neroland.neropower.turbine.TurbineSteamPortBlockEntity;
import za.co.neroland.neropower.turbine.VaneSetItem;
import za.co.neroland.neropower.turbine.VaneTier;

/**
 * The steam family: the five turbine blocks (helm, shroud, spindle, stator, steam port), the Steam
 * Condenser, the three Vane Sets and the Governor Module, their block-entity and menu types. Registered
 * through NeroPower's shared providers; {@link #init()} is called by the integration step (never from
 * here). Declaration order is registration order on Fabric: blocks, block items, items, BE types, menus.
 *
 * <p>Client: menus ({@link #TURBINE_HELM_MENU}, {@code turbine.client.TurbineHelmScreen::new}) and
 * ({@link #CONDENSER_MENU}, {@code turbine.client.CondenserScreen::new}).
 */
public final class TurbineContent {

    // --- blocks ------------------------------------------------------------------------------------

    public static final RegistryEntry<TurbineHelmBlock> TURBINE_HELM =
            ModBlocks.register("turbine_helm", TurbineHelmBlock::new);

    /** Louvred shell: non-occluding (cutout layer) so the rotor shows through the windowed face. */
    public static final RegistryEntry<Block> TURBINE_SHROUD =
            ModBlocks.BLOCKS.register("turbine_shroud", key -> new MultiblockPartBlock(partProperties()
                    .setId(key)
                    .mapColor(MapColor.METAL)));

    public static final RegistryEntry<TurbineSpindleBlock> TURBINE_SPINDLE =
            ModBlocks.BLOCKS.register("turbine_spindle", key -> new TurbineSpindleBlock(partProperties()
                    .setId(key)
                    .mapColor(MapColor.METAL)));

    public static final RegistryEntry<TurbineStatorBlock> TURBINE_STATOR =
            ModBlocks.BLOCKS.register("turbine_stator", key -> new TurbineStatorBlock(partProperties()
                    .setId(key)
                    .mapColor(MapColor.COLOR_ORANGE)));

    public static final RegistryEntry<TurbineSteamPortBlock> TURBINE_STEAM_PORT =
            ModBlocks.register("turbine_steam_port", TurbineSteamPortBlock::new);

    public static final RegistryEntry<CondenserBlock> CONDENSER =
            ModBlocks.register("condenser", CondenserBlock::new);

    // --- block items --------------------------------------------------------------------------------

    public static final RegistryEntry<BlockItem> TURBINE_HELM_ITEM = blockItem("turbine_helm", TURBINE_HELM);
    public static final RegistryEntry<BlockItem> TURBINE_SHROUD_ITEM = hintItem("turbine_shroud", TURBINE_SHROUD);
    public static final RegistryEntry<BlockItem> TURBINE_SPINDLE_ITEM = hintItem("turbine_spindle", TURBINE_SPINDLE);
    public static final RegistryEntry<BlockItem> TURBINE_STATOR_ITEM = hintItem("turbine_stator", TURBINE_STATOR);
    public static final RegistryEntry<BlockItem> TURBINE_STEAM_PORT_ITEM =
            hintItem("turbine_steam_port", TURBINE_STEAM_PORT);
    public static final RegistryEntry<BlockItem> CONDENSER_ITEM = blockItem("condenser", CONDENSER);

    // --- items -------------------------------------------------------------------------------------

    public static final RegistryEntry<VaneSetItem> STEEL_VANE_SET = vaneSet("steel_vane_set", VaneTier.STEEL);
    public static final RegistryEntry<VaneSetItem> CINDERSTEEL_VANE_SET =
            vaneSet("cindersteel_vane_set", VaneTier.CINDERSTEEL);
    public static final RegistryEntry<VaneSetItem> SUPERCONDUCTING_VANE_SET =
            vaneSet("superconducting_vane_set", VaneTier.SUPERCONDUCTING);

    public static final RegistryEntry<GovernorModuleItem> GOVERNOR_MODULE =
            ModItems.ITEMS.register("governor_module", key -> new GovernorModuleItem(new Item.Properties().setId(key)));

    // --- block entities + menus --------------------------------------------------------------------

    public static final RegistryEntry<BlockEntityType<TurbineHelmBlockEntity>> TURBINE_HELM_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("turbine_helm",
                    key -> BlockEntityTypeCompat.create(TurbineHelmBlockEntity::new, TURBINE_HELM.get()));

    public static final RegistryEntry<BlockEntityType<TurbineSteamPortBlockEntity>> TURBINE_STEAM_PORT_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("turbine_steam_port",
                    key -> BlockEntityTypeCompat.create(TurbineSteamPortBlockEntity::new, TURBINE_STEAM_PORT.get()));

    public static final RegistryEntry<BlockEntityType<CondenserBlockEntity>> CONDENSER_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("condenser",
                    key -> BlockEntityTypeCompat.create(CondenserBlockEntity::new, CONDENSER.get()));

    public static final RegistryEntry<MenuType<TurbineHelmMenu>> TURBINE_HELM_MENU =
            ModMenuTypes.MENUS.register("turbine_helm",
                    key -> new MenuType<>(TurbineHelmMenu::new, FeatureFlags.VANILLA_SET));

    public static final RegistryEntry<MenuType<CondenserMenu>> CONDENSER_MENU =
            ModMenuTypes.MENUS.register("condenser",
                    key -> new MenuType<>(CondenserMenu::new, FeatureFlags.VANILLA_SET));

    private TurbineContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). Called by integration. */
    public static void init() {
    }

    /** The helm: on NeroTech's energy surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(TURBINE_HELM_TYPE::get);
    }

    /** Steam ports and the condenser: on NeroTech's fluid surface (Core's capability and the loaders'). */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> fluidTypes() {
        return List.of(TURBINE_STEAM_PORT_TYPE::get, CONDENSER_TYPE::get);
    }

    /** Steam family in creative-tab order. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(TURBINE_HELM_ITEM, TURBINE_SHROUD_ITEM, TURBINE_SPINDLE_ITEM, TURBINE_STATOR_ITEM,
                TURBINE_STEAM_PORT_ITEM, CONDENSER_ITEM);
    }

    /** Vane sets and the Governor Module, for the tab's "cells and blades" / "modules" groups. */
    public static List<RegistryEntry<? extends ItemLike>> creativeVaneSets() {
        return List.of(STEEL_VANE_SET, CINDERSTEEL_VANE_SET, SUPERCONDUCTING_VANE_SET);
    }

    public static List<RegistryEntry<? extends ItemLike>> creativeModules() {
        return List.of(GOVERNOR_MODULE);
    }

    private static BlockBehaviour.Properties partProperties() {
        return BlockBehaviour.Properties.of()
                .strength(3.5F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .noOcclusion();
    }

    private static RegistryEntry<VaneSetItem> vaneSet(String name, VaneTier tier) {
        return ModItems.ITEMS.register(name, key -> new VaneSetItem(new Item.Properties().setId(key), tier));
    }

    private static RegistryEntry<BlockItem> hintItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new za.co.neroland.neropower.machine.HintBlockItem(block.get(),
                new Item.Properties().setId(key), za.co.neroland.neropower.machine.HintBlockItem.keyFor(name)));
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }
}

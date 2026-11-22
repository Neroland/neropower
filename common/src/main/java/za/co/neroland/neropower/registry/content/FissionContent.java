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

import za.co.neroland.neropower.fission.FissionTooltipItem;
import za.co.neroland.neropower.fission.ReactorControlComputerBlock;
import za.co.neroland.neropower.fission.ReactorControlComputerBlockEntity;
import za.co.neroland.neropower.fission.ReactorControlComputerMenu;
import za.co.neroland.neropower.fission.ReactorSteamPortBlock;
import za.co.neroland.neropower.fission.ReactorSteamPortBlockEntity;
import za.co.neroland.neropower.multiblock.MultiblockPartBlock;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower 0.4.0 fission additions (the 0.3.x core, casing, rod assembly and fuel items stay in
 * {@code fission.FissionContent}): the reactor interior blocks (Graphite Moderator, Moderator Tank,
 * Breeder Blanket, Europium Control Rod Assembly), the two wall machines (Reactor Steam Port, Reactor
 * Control Computer) with their block-entity types and the computer's menu, and the second fuel line's
 * items. {@link #init()} forces class-load; the integration step calls it.
 *
 * <p>Order of declaration is registration order (eager on Fabric): blocks, block items, items, block
 * entities, menu.
 */
public final class FissionContent {

    // --- interior parts (BE-less; placing or removing one marks the reactor dirty) -----------------

    /** Full-cube graphite: moderates every interior air block it touches. */
    public static final RegistryEntry<Block> GRAPHITE_MODERATOR = ModBlocks.BLOCKS.register("graphite_moderator",
            key -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    /** Sealed heavy-water tank, crafted full; twice graphite's weight per contact. Windowed: no occlusion. */
    public static final RegistryEntry<Block> MODERATOR_TANK = ModBlocks.BLOCKS.register("moderator_tank",
            key -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    /** Breeds Pallurite into Kindled Pellets (slots on the core) while the reactor runs; costs reactivity. */
    public static final RegistryEntry<Block> BREEDER_BLANKET = ModBlocks.BLOCKS.register("breeder_blanket",
            key -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.TERRACOTTA_ORANGE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /** Control rods tier 2: a stronger throttle. Thin rods in a frame, so non-occluding. */
    public static final RegistryEntry<Block> EUROPIUM_CONTROL_ROD_ASSEMBLY = ModBlocks.BLOCKS.register(
            "europium_control_rod_assembly",
            key -> new MultiblockPartBlock(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()));

    // --- wall machines ----------------------------------------------------------------------------

    public static final RegistryEntry<ReactorSteamPortBlock> REACTOR_STEAM_PORT =
            ModBlocks.register("reactor_steam_port", ReactorSteamPortBlock::new);
    public static final RegistryEntry<ReactorControlComputerBlock> REACTOR_CONTROL_COMPUTER =
            ModBlocks.register("reactor_control_computer", ReactorControlComputerBlock::new);

    // --- block items ------------------------------------------------------------------------------

    public static final RegistryEntry<BlockItem> GRAPHITE_MODERATOR_ITEM = hintItem("graphite_moderator", GRAPHITE_MODERATOR);
    public static final RegistryEntry<BlockItem> MODERATOR_TANK_ITEM = hintItem("moderator_tank", MODERATOR_TANK);
    public static final RegistryEntry<BlockItem> BREEDER_BLANKET_ITEM = hintItem("breeder_blanket", BREEDER_BLANKET);
    public static final RegistryEntry<BlockItem> EUROPIUM_CONTROL_ROD_ASSEMBLY_ITEM =
            hintItem("europium_control_rod_assembly", EUROPIUM_CONTROL_ROD_ASSEMBLY);
    public static final RegistryEntry<BlockItem> REACTOR_STEAM_PORT_ITEM = hintItem("reactor_steam_port", REACTOR_STEAM_PORT);
    public static final RegistryEntry<BlockItem> REACTOR_CONTROL_COMPUTER_ITEM =
            blockItem("reactor_control_computer", REACTOR_CONTROL_COMPUTER);

    // --- items ------------------------------------------------------------------------------------

    /** What a burnt-out rod becomes; must cool in a Spent Fuel Rack before reprocessing. */
    public static final RegistryEntry<Item> HOT_SPENT_FUEL_ROD = ModItems.ITEMS.register("hot_spent_fuel_rod",
            key -> new FissionTooltipItem(new Item.Properties().setId(key).stacksTo(1),
                    "item.neropower.hot_spent_fuel_rod.tooltip"));
    public static final RegistryEntry<Item> PALLURITE_DUST = item("pallurite_dust");
    public static final RegistryEntry<Item> PALLURITE_PELLET = item("pallurite_pellet");
    public static final RegistryEntry<Item> KINDLED_PELLET = item("kindled_pellet");
    /** Mixed rod; carries {@code neropower:burnup}, so unstackable. Fissions only with enough heavy water. */
    public static final RegistryEntry<Item> BRAIDED_FUEL_ROD = ModItems.ITEMS.register("braided_fuel_rod",
            key -> new FissionTooltipItem.Braided(new Item.Properties().setId(key).stacksTo(1),
                    "item.neropower.braided_fuel_rod.tooltip"));
    public static final RegistryEntry<Item> EUROPIUM_CONTROL_ROD = item("europium_control_rod");
    /** Upgrade module for the Fission Core only (read by the core itself; Core never counts it). */
    public static final RegistryEntry<Item> FUEL_ECONOMIZER = ModItems.ITEMS.register("fuel_economizer",
            key -> new FissionTooltipItem(new Item.Properties().setId(key).stacksTo(1),
                    "item.neropower.fuel_economizer.tooltip"));

    // --- block entities + menu --------------------------------------------------------------------

    public static final RegistryEntry<BlockEntityType<ReactorSteamPortBlockEntity>> REACTOR_STEAM_PORT_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("reactor_steam_port",
                    key -> BlockEntityTypeCompat.create(ReactorSteamPortBlockEntity::new, REACTOR_STEAM_PORT.get()));
    public static final RegistryEntry<BlockEntityType<ReactorControlComputerBlockEntity>> REACTOR_CONTROL_COMPUTER_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("reactor_control_computer",
                    key -> BlockEntityTypeCompat.create(ReactorControlComputerBlockEntity::new,
                            REACTOR_CONTROL_COMPUTER.get()));

    public static final RegistryEntry<MenuType<ReactorControlComputerMenu>> REACTOR_CONTROL_COMPUTER_MENU =
            ModMenuTypes.MENUS.register("reactor_control_computer",
                    key -> new MenuType<>(ReactorControlComputerMenu::new, FeatureFlags.VANILLA_SET));

    private FissionContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). Called by the integration step. */
    public static void init() {
    }

    /** The steam port on the fluid surface (coolant in, steam out). */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> fluidTypes() {
        return List.of(REACTOR_STEAM_PORT_TYPE::get);
    }

    /** Fission 0.4.0 content in creative-tab order (after the 0.3.x fission entries). */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(GRAPHITE_MODERATOR_ITEM, MODERATOR_TANK_ITEM, BREEDER_BLANKET_ITEM,
                EUROPIUM_CONTROL_ROD_ASSEMBLY_ITEM, REACTOR_STEAM_PORT_ITEM, REACTOR_CONTROL_COMPUTER_ITEM,
                EUROPIUM_CONTROL_ROD, PALLURITE_DUST, PALLURITE_PELLET, KINDLED_PELLET, BRAIDED_FUEL_ROD,
                HOT_SPENT_FUEL_ROD, FUEL_ECONOMIZER);
    }

    private static RegistryEntry<Item> item(String name) {
        return ModItems.ITEMS.register(name, key -> new Item(new Item.Properties().setId(key)));
    }

    private static RegistryEntry<BlockItem> hintItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new za.co.neroland.neropower.machine.HintBlockItem(block.get(),
                new Item.Properties().setId(key), za.co.neroland.neropower.machine.HintBlockItem.keyFor(name)));
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }
}

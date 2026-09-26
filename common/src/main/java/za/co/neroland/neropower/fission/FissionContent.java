package za.co.neroland.neropower.fission;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
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

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * Everything the Stage 4 fission reactor registers, in one place, through NeroPower's shared
 * cross-loader providers: three blocks (core, casing, control rod assembly) and their block items,
 * the four fuel-cycle items, the {@code neropower:burnup} data component, the core's block-entity
 * type and its menu type.
 *
 * <p><b>Client registration (loader client setup):</b> the one (MenuType, Screen) pair is
 * ({@link #FISSION_CORE_MENU}, {@code za.co.neroland.neropower.fission.client.FissionCoreScreen}) —
 * {@code FissionCoreScreen::new}.
 *
 * <p><b>Server tick:</b> {@link ScorchTicker#tick} must be called from each loader's
 * end-of-server-tick event (see its javadoc).
 *
 * <p>Registration order (eager on Fabric): blocks, then block items (which need the blocks), then
 * the block-entity type (needs the block) — all static fields of this one class, so the order of
 * declaration below is the order that matters.
 */
public final class FissionContent {

    // --- blocks ------------------------------------------------------------------------

    /** The multiblock controller: a NeroPower machine block with the alarm state. */
    public static final RegistryEntry<FissionCoreBlock> FISSION_CORE =
            ModBlocks.register("fission_core", FissionCoreBlock::new);

    /**
     * Structural shell plate for the fission multiblock — a plain full cube that, like NeroTech's
     * fusion casing, KEEPS full-cube occlusion so shell walls cull their neighbours normally.
     */
    public static final RegistryEntry<Block> FISSION_CASING =
            ModBlocks.BLOCKS.register("fission_casing", key -> new Block(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    /** The neutron-absorbing insert block; the core counts every one inside its shell. */
    public static final RegistryEntry<Block> CONTROL_ROD_ASSEMBLY =
            ModBlocks.BLOCKS.register("control_rod_assembly", key -> new Block(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(3.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    // --- data components -----------------------------------------------------------------

    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroPowerCommon.MOD_ID);

    /**
     * A Fuel Rod's burn-up, permille (0 = fresh, 1000 = spent; readers clamp through
     * {@link FissionMath#clampBurnup}). Machine state only — no player data.
     */
    public static final RegistryEntry<DataComponentType<Integer>> BURNUP =
            COMPONENTS.register("burnup", key -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .build());

    // --- items ---------------------------------------------------------------------------

    public static final RegistryEntry<BlockItem> FISSION_CORE_ITEM = blockItem("fission_core", FISSION_CORE);
    public static final RegistryEntry<BlockItem> FISSION_CASING_ITEM = blockItem("fission_casing", FISSION_CASING);
    public static final RegistryEntry<BlockItem> CONTROL_ROD_ASSEMBLY_ITEM =
            blockItem("control_rod_assembly", CONTROL_ROD_ASSEMBLY);

    /** Refined fissile pellet: three make a Fuel Rod. */
    public static final RegistryEntry<Item> URANIUM_PELLET = item("uranium_pellet");
    /** The reactor's fuel; unstackable because each rod carries its own {@link #BURNUP}. */
    public static final RegistryEntry<Item> FUEL_ROD = ModItems.ITEMS.register("fuel_rod",
            key -> new Item(new Item.Properties().setId(key).stacksTo(1)));
    /** What a Fuel Rod becomes at 1000‰ — reprocess it in a Chemical Processor. */
    public static final RegistryEntry<Item> SPENT_FUEL_ROD = item("spent_fuel_rod");
    /** Reprocessing output: two make a fresh Uranium Pellet. */
    public static final RegistryEntry<Item> REPROCESSED_PELLET = item("reprocessed_pellet");

    // --- block entity + menu -------------------------------------------------------------

    public static final RegistryEntry<BlockEntityType<FissionCoreBlockEntity>> FISSION_CORE_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("fission_core",
                    key -> new BlockEntityType<>(FissionCoreBlockEntity::new, Set.of(FISSION_CORE.get())));

    public static final RegistryEntry<MenuType<FissionCoreMenu>> FISSION_CORE_MENU =
            ModMenuTypes.MENUS.register("fission_core",
                    key -> new MenuType<>(FissionCoreMenu::new, FeatureFlags.VANILLA_SET));

    private FissionContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** The fission core: on NeroTech's energy surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(FISSION_CORE_TYPE::get);
    }

    /** The fission core: rod slots reachable by pipes / hoppers. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(FISSION_CORE_TYPE::get);
    }

    /** Fission content in creative-tab display order. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(FISSION_CORE_ITEM, FISSION_CASING_ITEM, CONTROL_ROD_ASSEMBLY_ITEM,
                URANIUM_PELLET, FUEL_ROD, SPENT_FUEL_ROD, REPROCESSED_PELLET);
    }

    private static RegistryEntry<Item> item(String name) {
        return ModItems.ITEMS.register(name, key -> new Item(new Item.Properties().setId(key)));
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }
}

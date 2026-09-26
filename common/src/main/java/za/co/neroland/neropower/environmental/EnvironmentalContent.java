package za.co.neroland.neropower.environmental;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * Stage 7 content — the environmental generators — registered through NeroPower's shared providers:
 * the Radioisotope Generator and Stirling Generator blocks, their block items and block-entity types,
 * the two pellet items and the two menus.
 *
 * <p>Client wiring (each loader's client setup registers these screens):
 * <ul>
 *   <li>{@link #RTG_MENU} → {@code za.co.neroland.neropower.environmental.client.RtgScreen}</li>
 *   <li>{@link #STIRLING_MENU} → {@code za.co.neroland.neropower.environmental.client.StirlingScreen}</li>
 * </ul>
 *
 * <p>Cross-loader capability wiring: {@link #energyTypes()} feeds {@code ModBlockEntities.energyMachineTypes()}
 * (both generators) and {@link #itemTypes()} feeds {@code itemMachineTypes()} (the RTG's pellet slots).
 */
public final class EnvironmentalContent {

    // --- blocks ------------------------------------------------------------------------------------
    public static final RegistryEntry<RadioisotopeGeneratorBlock> RTG_BLOCK =
            ModBlocks.register("radioisotope_generator", RadioisotopeGeneratorBlock::new);
    public static final RegistryEntry<StirlingGeneratorBlock> STIRLING_BLOCK =
            ModBlocks.register("stirling_generator", StirlingGeneratorBlock::new);

    // --- items ---------------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> RTG_ITEM = ModItems.ITEMS.register("radioisotope_generator",
            key -> new BlockItem(RTG_BLOCK.get(), new Item.Properties().setId(key)));
    public static final RegistryEntry<BlockItem> STIRLING_ITEM = ModItems.ITEMS.register("stirling_generator",
            key -> new BlockItem(STIRLING_BLOCK.get(), new Item.Properties().setId(key)));
    /** Fresh RTG fuel: one pellet decays over roughly four half-lives before it is spent. */
    public static final RegistryEntry<Item> ISOTOPE_PELLET = ModItems.ITEMS.register("isotope_pellet",
            key -> new Item(new Item.Properties().setId(key)));
    /** What the RTG hands back once a pellet's output drops below the cutoff; pollutes if destroyed as a drop. */
    public static final RegistryEntry<SpentIsotopePelletItem> SPENT_ISOTOPE_PELLET =
            ModItems.ITEMS.register("spent_isotope_pellet",
                    key -> new SpentIsotopePelletItem(new Item.Properties().setId(key)));

    // --- block entities ----------------------------------------------------------------------------
    public static final RegistryEntry<BlockEntityType<RadioisotopeGeneratorBlockEntity>> RTG_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("radioisotope_generator",
                    key -> new BlockEntityType<>(RadioisotopeGeneratorBlockEntity::new, Set.of(RTG_BLOCK.get())));
    public static final RegistryEntry<BlockEntityType<StirlingGeneratorBlockEntity>> STIRLING_TYPE =
            ModBlockEntities.BLOCK_ENTITIES.register("stirling_generator",
                    key -> new BlockEntityType<>(StirlingGeneratorBlockEntity::new, Set.of(STIRLING_BLOCK.get())));

    // --- menus ---------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<RtgMenu>> RTG_MENU =
            ModMenuTypes.MENUS.register("radioisotope_generator",
                    key -> new MenuType<>(RtgMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<StirlingMenu>> STIRLING_MENU =
            ModMenuTypes.MENUS.register("stirling_generator",
                    key -> new MenuType<>(StirlingMenu::new, FeatureFlags.VANILLA_SET));

    private EnvironmentalContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** Both generators sit on the energy surface. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(RTG_TYPE::get, STIRLING_TYPE::get);
    }

    /** Only the RTG has slots pipes / hoppers must reach. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(RTG_TYPE::get);
    }

    /** Stage 7 creative-tab entries, in display order. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(RTG_ITEM, STIRLING_ITEM, ISOTOPE_PELLET, SPENT_ISOTOPE_PELLET);
    }
}

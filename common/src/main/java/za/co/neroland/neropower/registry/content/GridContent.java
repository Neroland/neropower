package za.co.neroland.neropower.registry.content;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.nerolandcore.registry.BlockEntityTypeCompat;
import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

import za.co.neroland.neropower.beam.BeamSplitterBlock;
import za.co.neroland.neropower.beam.BeamSplitterBlockEntity;
import za.co.neroland.neropower.beam.BeamSplitterMenu;
import za.co.neroland.neropower.beam.PhasedArrayEmitterBlock;
import za.co.neroland.neropower.beam.PhasedArrayEmitterBlockEntity;
import za.co.neroland.neropower.beam.PhasedArrayEmitterMenu;
import za.co.neroland.neropower.grid.CircuitBreakerBlock;
import za.co.neroland.neropower.grid.CircuitBreakerBlockEntity;
import za.co.neroland.neropower.grid.CircuitBreakerMenu;
import za.co.neroland.neropower.grid.LoadBankBlock;
import za.co.neroland.neropower.grid.LoadBankBlockEntity;
import za.co.neroland.neropower.grid.LoadBankMenu;
import za.co.neroland.neropower.grid.PowerMonitorBlock;
import za.co.neroland.neropower.grid.PowerMonitorBlockEntity;
import za.co.neroland.neropower.grid.PowerMonitorMenu;
import za.co.neroland.neropower.registry.ModBlockEntities;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.ModMenuTypes;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * Registrations of the 0.4.0 beam and grid package (WP-Grid): Beam Splitter, Phased Array Emitter,
 * Circuit Breaker, Load Bank and Power Monitor — blocks, block items, block-entity types and menus, in the
 * order the eager (Fabric) loader needs.
 *
 * <p><b>Wiring (integration):</b> call {@code grid.GridConfig.init()} before {@code NeroPowerConfig.load()}
 * (the beam keys live in the already-wired {@code BeamConfig}); call {@link #init()} after
 * {@code ModRegistries.init()} and before {@code ModBlockEntities.registerMachineTypes()}; append
 * {@link #energyTypes()} to the energy surface, {@link #itemTypes()} to the item surface and
 * {@link #creativeItems()} / {@link #gridCreativeItems()} to the creative order (Beam, then Grid). Screens:
 * {@code BEAM_SPLITTER_MENU -> beam.client.BeamSplitterScreen}, {@code PHASED_ARRAY_EMITTER_MENU ->
 * beam.client.PhasedArrayEmitterScreen}, {@code CIRCUIT_BREAKER_MENU -> grid.client.CircuitBreakerScreen},
 * {@code LOAD_BANK_MENU -> grid.client.LoadBankScreen}, {@code POWER_MONITOR_MENU ->
 * grid.client.PowerMonitorScreen}.
 */
public final class GridContent {

    // --- blocks ----------------------------------------------------------------------------------------
    public static final RegistryEntry<BeamSplitterBlock> BEAM_SPLITTER =
            ModBlocks.register("beam_splitter", BeamSplitterBlock::new);
    public static final RegistryEntry<PhasedArrayEmitterBlock> PHASED_ARRAY_EMITTER =
            ModBlocks.register("phased_array_emitter", PhasedArrayEmitterBlock::new);
    public static final RegistryEntry<CircuitBreakerBlock> CIRCUIT_BREAKER =
            ModBlocks.register("circuit_breaker", CircuitBreakerBlock::new);
    public static final RegistryEntry<LoadBankBlock> LOAD_BANK =
            ModBlocks.register("load_bank", LoadBankBlock::new);
    public static final RegistryEntry<PowerMonitorBlock> POWER_MONITOR =
            ModBlocks.register("power_monitor", PowerMonitorBlock::new);

    // --- items -------------------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> BEAM_SPLITTER_ITEM = blockItem("beam_splitter", BEAM_SPLITTER);
    public static final RegistryEntry<BlockItem> PHASED_ARRAY_EMITTER_ITEM =
            blockItem("phased_array_emitter", PHASED_ARRAY_EMITTER);
    public static final RegistryEntry<BlockItem> CIRCUIT_BREAKER_ITEM = blockItem("circuit_breaker", CIRCUIT_BREAKER);
    public static final RegistryEntry<BlockItem> LOAD_BANK_ITEM = blockItem("load_bank", LOAD_BANK);
    public static final RegistryEntry<BlockItem> POWER_MONITOR_ITEM = blockItem("power_monitor", POWER_MONITOR);

    // --- block entities -----------------------------------------------------------------------------------
    public static final RegistryEntry<BlockEntityType<BeamSplitterBlockEntity>> BEAM_SPLITTER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("beam_splitter",
                    key -> BlockEntityTypeCompat.create(BeamSplitterBlockEntity::new, BEAM_SPLITTER.get()));
    public static final RegistryEntry<BlockEntityType<PhasedArrayEmitterBlockEntity>> PHASED_ARRAY_EMITTER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("phased_array_emitter",
                    key -> BlockEntityTypeCompat.create(PhasedArrayEmitterBlockEntity::new, PHASED_ARRAY_EMITTER.get()));
    public static final RegistryEntry<BlockEntityType<CircuitBreakerBlockEntity>> CIRCUIT_BREAKER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("circuit_breaker",
                    key -> BlockEntityTypeCompat.create(CircuitBreakerBlockEntity::new, CIRCUIT_BREAKER.get()));
    public static final RegistryEntry<BlockEntityType<LoadBankBlockEntity>> LOAD_BANK_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("load_bank",
                    key -> BlockEntityTypeCompat.create(LoadBankBlockEntity::new, LOAD_BANK.get()));
    public static final RegistryEntry<BlockEntityType<PowerMonitorBlockEntity>> POWER_MONITOR_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("power_monitor",
                    key -> BlockEntityTypeCompat.create(PowerMonitorBlockEntity::new, POWER_MONITOR.get()));

    // --- menus ------------------------------------------------------------------------------------------------
    public static final RegistryEntry<MenuType<BeamSplitterMenu>> BEAM_SPLITTER_MENU =
            ModMenuTypes.MENUS.register("beam_splitter", key -> new MenuType<>(BeamSplitterMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<PhasedArrayEmitterMenu>> PHASED_ARRAY_EMITTER_MENU =
            ModMenuTypes.MENUS.register("phased_array_emitter",
                    key -> new MenuType<>(PhasedArrayEmitterMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<CircuitBreakerMenu>> CIRCUIT_BREAKER_MENU =
            ModMenuTypes.MENUS.register("circuit_breaker",
                    key -> new MenuType<>(CircuitBreakerMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<LoadBankMenu>> LOAD_BANK_MENU =
            ModMenuTypes.MENUS.register("load_bank", key -> new MenuType<>(LoadBankMenu::new, FeatureFlags.VANILLA_SET));
    public static final RegistryEntry<MenuType<PowerMonitorMenu>> POWER_MONITOR_MENU =
            ModMenuTypes.MENUS.register("power_monitor", key -> new MenuType<>(PowerMonitorMenu::new, FeatureFlags.VANILLA_SET));

    private GridContent() {
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** Every new block entity is on the energy surface (the monitor's faces start disabled). */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        return List.of(BEAM_SPLITTER_BE::get, PHASED_ARRAY_EMITTER_BE::get, CIRCUIT_BREAKER_BE::get,
                LOAD_BANK_BE::get, POWER_MONITOR_BE::get);
    }

    /** Block-entity types with slots pipes / hoppers reach: the splitter's lens slot. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of(BEAM_SPLITTER_BE::get);
    }

    /** Creative order, Beam family: splitter, phased array. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(BEAM_SPLITTER_ITEM, PHASED_ARRAY_EMITTER_ITEM);
    }

    /** Creative order, Grid family: breaker, load bank, monitor. */
    public static List<RegistryEntry<? extends ItemLike>> gridCreativeItems() {
        return List.of(CIRCUIT_BREAKER_ITEM, LOAD_BANK_ITEM, POWER_MONITOR_ITEM);
    }
}

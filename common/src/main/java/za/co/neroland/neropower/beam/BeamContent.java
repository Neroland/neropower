package za.co.neroland.neropower.beam;

import java.util.ArrayList;
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
 * Everything Stage 6 (beamed power) registers, through NeroPower's shared providers: four machine
 * blocks and their block items, four block-entity types and the one shared menu type.
 *
 * <p><b>Client registration</b> (each loader's client setup, exactly as NeroTech's
 * {@code MenuScreens.register} calls): the single pair
 * ({@link #BEAM_MENU}, {@code za.co.neroland.neropower.beam.client.BeamScreen}) —
 * {@code MenuScreens.register(BeamContent.BEAM_MENU.get(), BeamScreen::new)}.
 *
 * <p><b>Wiring</b> (owner of the registries): {@code BeamContent.init()} from
 * {@code NeroPowerCommon.init()} (after {@code ModRegistries.init()}, before
 * {@code ModBlockEntities.registerMachineTypes()}); {@link #energyTypes()} appended to
 * {@code ModBlockEntities.energyMachineTypes()}; {@link #creativeItems()} appended to
 * {@code ModItems.creativeOrder()}; {@code BeamConfig.init()} before {@code NeroPowerConfig.load()}.
 */
public final class BeamContent {

    // --- blocks ------------------------------------------------------------------------------------

    public static final RegistryEntry<BeamTransmitterBlock> BEAM_TRANSMITTER =
            ModBlocks.register("beam_transmitter", BeamTransmitterBlock::new);
    public static final RegistryEntry<BeamReceiverBlock> BEAM_RECEIVER =
            ModBlocks.register("beam_receiver", BeamReceiverBlock::new);
    public static final RegistryEntry<BeamRelayBlock> BEAM_RELAY =
            ModBlocks.register("beam_relay", BeamRelayBlock::new);
    public static final RegistryEntry<OrbitalReceiverBlock> ORBITAL_RECEIVER =
            ModBlocks.register("orbital_receiver", OrbitalReceiverBlock::new);

    // --- block items -------------------------------------------------------------------------------

    public static final RegistryEntry<BlockItem> BEAM_TRANSMITTER_ITEM = blockItem("beam_transmitter", BEAM_TRANSMITTER);
    public static final RegistryEntry<BlockItem> BEAM_RECEIVER_ITEM = blockItem("beam_receiver", BEAM_RECEIVER);
    public static final RegistryEntry<BlockItem> BEAM_RELAY_ITEM = blockItem("beam_relay", BEAM_RELAY);
    public static final RegistryEntry<BlockItem> ORBITAL_RECEIVER_ITEM = blockItem("orbital_receiver", ORBITAL_RECEIVER);

    // --- block entities ----------------------------------------------------------------------------

    public static final RegistryEntry<BlockEntityType<BeamTransmitterBlockEntity>> BEAM_TRANSMITTER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("beam_transmitter",
                    key -> new BlockEntityType<>(BeamTransmitterBlockEntity::new, Set.of(BEAM_TRANSMITTER.get())));
    public static final RegistryEntry<BlockEntityType<BeamReceiverBlockEntity>> BEAM_RECEIVER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("beam_receiver",
                    key -> new BlockEntityType<>(BeamReceiverBlockEntity::new, Set.of(BEAM_RECEIVER.get())));
    public static final RegistryEntry<BlockEntityType<BeamRelayBlockEntity>> BEAM_RELAY_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("beam_relay",
                    key -> new BlockEntityType<>(BeamRelayBlockEntity::new, Set.of(BEAM_RELAY.get())));
    public static final RegistryEntry<BlockEntityType<OrbitalReceiverBlockEntity>> ORBITAL_RECEIVER_BE =
            ModBlockEntities.BLOCK_ENTITIES.register("orbital_receiver",
                    key -> new BlockEntityType<>(OrbitalReceiverBlockEntity::new, Set.of(ORBITAL_RECEIVER.get())));

    // --- menu --------------------------------------------------------------------------------------

    /** One menu for all four endpoints; screen: {@code beam.client.BeamScreen}. */
    public static final RegistryEntry<MenuType<BeamMenu>> BEAM_MENU =
            ModMenuTypes.MENUS.register("beam", key -> new MenuType<>(BeamMenu::new, FeatureFlags.VANILLA_SET));

    private BeamContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** All four endpoints sit on the energy surface (transmitter in, receivers out, relay through). */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> energyTypes() {
        List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> types = new ArrayList<>();
        types.add(BEAM_TRANSMITTER_BE::get);
        types.add(BEAM_RECEIVER_BE::get);
        types.add(BEAM_RELAY_BE::get);
        types.add(ORBITAL_RECEIVER_BE::get);
        return List.copyOf(types);
    }

    /** No beam endpoint has machine slots. */
    public static List<Supplier<BlockEntityType<? extends NeroTechMachineBlockEntity>>> itemTypes() {
        return List.of();
    }

    /** Creative tab order: transmitter, receiver, relay, orbital receiver. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(BEAM_TRANSMITTER_ITEM, BEAM_RECEIVER_ITEM, BEAM_RELAY_ITEM, ORBITAL_RECEIVER_ITEM);
    }

    private static RegistryEntry<BlockItem> blockItem(String name, RegistryEntry<? extends Block> block) {
        return ModItems.ITEMS.register(name, key -> new BlockItem(block.get(), new Item.Properties().setId(key)));
    }
}

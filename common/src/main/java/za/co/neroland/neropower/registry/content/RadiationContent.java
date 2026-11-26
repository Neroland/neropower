package za.co.neroland.neropower.registry.content;

import java.util.List;

import com.mojang.serialization.Codec;

//? if >=1.20.5 {
import net.minecraft.core.component.DataComponentType;
//?}
import net.minecraft.core.registries.Registries;
//? if >=1.20.5 {
import net.minecraft.network.codec.ByteBufCodecs;
//?}
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.radiation.DecontaminantItem;
import za.co.neroland.neropower.radiation.DosimeterBadgeItem;
import za.co.neroland.neropower.radiation.GeigerCounterItem;
import za.co.neroland.neropower.radiation.GeigerReading;
import za.co.neroland.neropower.radiation.RadiationSources;
import za.co.neroland.neropower.radiation.RadiationTicker;
import za.co.neroland.neropower.registry.ModBlocks;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.RegistrationProvider;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * Radiation: the two Shielding blocks, the Geiger Counter, Dosimeter
 * Badge and Decontaminant, the {@code neropower:dosimeter_reading} and {@code neropower:geiger_reading}
 * components and the three radiation sound events. Order for the eager (Fabric) loader: components,
 * sounds, blocks, items.
 *
 * <p><b>Wiring (integration):</b> call {@link #init()} from {@code NeroPowerCommon.init()} with the other
 * content inits, after {@code RadiationConfig.init()}. It also installs the back-end emitter registry and
 * burst sink ({@link RadiationSources#install()}) and switches on the radiation tick. No block entity,
 * menu or capability.
 */
public final class RadiationContent {

    //? if >=1.20.5 {
    // --- data components ---------------------------------------------------------------------------
    public static final RegistrationProvider<DataComponentType<?>> COMPONENTS =
            RegistrationProvider.get(Registries.DATA_COMPONENT_TYPE, NeroPowerCommon.MOD_ID);

    /** The badge's running total in pall; on the item only, never a UUID or name. */
    public static final RegistryEntry<DataComponentType<Integer>> DOSIMETER_READING =
            COMPONENTS.register("dosimeter_reading", key -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** The Geiger's latest world reading while in a hand (stripped once it leaves the hand). */
    public static final RegistryEntry<DataComponentType<GeigerReading>> GEIGER_READING =
            COMPONENTS.register("geiger_reading", key -> DataComponentType.<GeigerReading>builder()
                    .persistent(GeigerReading.CODEC)
                    .networkSynchronized(GeigerReading.STREAM_CODEC)
                    .build());

    //?}

    public static int dosimeterReading(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.getOrDefault(DOSIMETER_READING.get(), 0);
        //?} else {
        /*return stack.hasTag() ? stack.getTag().getInt("neropower:dosimeter_reading") : 0;
        *///?}
    }

    public static void setDosimeterReading(ItemStack stack, int reading) {
        //? if >=1.20.5 {
        stack.set(DOSIMETER_READING.get(), reading);
        //?} else {
        /*stack.getOrCreateTag().putInt("neropower:dosimeter_reading", reading);
        *///?}
    }

    public static GeigerReading geigerReading(ItemStack stack) {
        //? if >=1.20.5 {
        return stack.get(GEIGER_READING.get());
        //?} else {
        /*var tag = stack.getTagElement("neropower:geiger_reading");
        if (tag == null) {
            return null;
        }
        int[] levels = tag.getIntArray("levels");
        if (levels.length != 0 && levels.length != 9) {
            return null;
        }
        return new GeigerReading(java.util.Arrays.stream(levels).boxed().toList(),
                tag.getInt("sources"), tag.getBoolean("enabled"));
        *///?}
    }

    public static void setGeigerReading(ItemStack stack, GeigerReading reading) {
        //? if >=1.20.5 {
        stack.set(GEIGER_READING.get(), reading);
        //?} else {
        /*var tag = new net.minecraft.nbt.CompoundTag();
        tag.putIntArray("levels", reading.levels());
        tag.putInt("sources", reading.sources());
        tag.putBoolean("enabled", reading.enabled());
        stack.addTagElement("neropower:geiger_reading", tag);
        *///?}
    }

    public static void clearGeigerReading(ItemStack stack) {
        //? if >=1.20.5 {
        stack.remove(GEIGER_READING.get());
        //?} else {
        /*stack.removeTagKey("neropower:geiger_reading");
        *///?}
    }

    // --- sounds --------------------------------------------------------------------------------------
    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroPowerCommon.MOD_ID);

    public static final RegistryEntry<SoundEvent> GEIGER_CLICK = sound("geiger.click");
    public static final RegistryEntry<SoundEvent> GEIGER_CHATTER = sound("geiger.chatter");
    public static final RegistryEntry<SoundEvent> DECONTAMINANT_SPRAY = sound("decontaminant.spray");

    // --- blocks --------------------------------------------------------------------------------------
    /** Dense, opaque shielding; in {@code #neropower:radiation_barrier}. */
    public static final RegistryEntry<Block> SHIELDING_BLOCK = ModBlocks.BLOCKS.register("shielding_block",
            key -> new Block(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(5.0F, 9.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)));

    /** See-through shielding: non-occluding, translucent; in {@code #neropower:radiation_barrier}. */
    public static final RegistryEntry<TransparentBlock> SHIELDING_GLASS = ModBlocks.BLOCKS.register("shielding_glass",
            key -> shieldingGlass(BlockBehaviour.Properties.of()
                    .setId(key)
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(3.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)));

    private static TransparentBlock shieldingGlass(BlockBehaviour.Properties properties) {
        //? if >=1.21 {
        return new TransparentBlock(properties);
        //?} else {
        /*return new TransparentBlock(properties) { };
        *///?}
    }

    // --- items ---------------------------------------------------------------------------------------
    public static final RegistryEntry<BlockItem> SHIELDING_BLOCK_ITEM = ModItems.ITEMS.register("shielding_block",
            key -> new za.co.neroland.neropower.machine.HintBlockItem(SHIELDING_BLOCK.get(), new Item.Properties().setId(key),
                    za.co.neroland.neropower.machine.HintBlockItem.keyFor("shielding_block")));
    public static final RegistryEntry<BlockItem> SHIELDING_GLASS_ITEM = ModItems.ITEMS.register("shielding_glass",
            key -> new za.co.neroland.neropower.machine.HintBlockItem(SHIELDING_GLASS.get(), new Item.Properties().setId(key),
                    za.co.neroland.neropower.machine.HintBlockItem.keyFor("shielding_glass")));

    public static final RegistryEntry<GeigerCounterItem> GEIGER_COUNTER = ModItems.ITEMS.register("geiger_counter",
            key -> new GeigerCounterItem(new Item.Properties().setId(key).stacksTo(1)));
    public static final RegistryEntry<DosimeterBadgeItem> DOSIMETER_BADGE = ModItems.ITEMS.register("dosimeter_badge",
            key -> new DosimeterBadgeItem(new Item.Properties().setId(key).stacksTo(1)));
    public static final RegistryEntry<DecontaminantItem> DECONTAMINANT = ModItems.ITEMS.register("decontaminant",
            key -> new DecontaminantItem(new Item.Properties().setId(key).stacksTo(16)));

    private static boolean installed;

    private RadiationContent() {
    }

    /** Force class-load so the static registrations run; install the back-end hooks and the tick once. */
    public static void init() {
        if (installed) {
            return;
        }
        installed = true;
        RadiationSources.install();
        RadiationTicker.activate();
    }

    /** Shielding blocks in creative-tab order (family "Radiation"). */
    public static List<RegistryEntry<? extends ItemLike>> creativeBlocks() {
        return List.of(SHIELDING_BLOCK_ITEM, SHIELDING_GLASS_ITEM);
    }

    /** Radiation tools and the consumable, in creative-tab order. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(GEIGER_COUNTER, DOSIMETER_BADGE, DECONTAMINANT);
    }

    private static RegistryEntry<SoundEvent> sound(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path);
        return SOUNDS.register(path, key -> SoundEvent.createVariableRangeEvent(id));
    }
}

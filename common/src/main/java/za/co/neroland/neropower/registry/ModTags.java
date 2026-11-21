package za.co.neroland.neropower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * NeroPower's own tag keys (data in {@code data/neropower/tags/} and {@code data/c/tags/}). Tags are how
 * NeroPower names other mods' blocks without a class dependency: NeroTech's Battery Bank joins
 * {@link #ENERGY_STORAGES} as an optional entry, so the slosh guard never imports NeroTech's internals.
 */
public final class ModTags {

    // --- blocks ------------------------------------------------------------------------------------

    /** Storage blocks a storage push never feeds (the slosh guard); NeroTech's Battery Bank is optional. */
    public static final TagKey<Block> ENERGY_STORAGES = block("energy_storages");
    /** Every block a fission shell may hold inside. */
    public static final TagKey<Block> REACTOR_INTERIOR = block("reactor_interior");
    /** Control-rod assemblies (tier 1 and 2), counted by the core. */
    public static final TagKey<Block> CONTROL_ROD_ASSEMBLIES = block("control_rod_assemblies");
    /** Fission moderators. */
    public static final TagKey<Block> MODERATORS = block("moderators");
    /** Blocks that stop radiation leaving a source face. */
    public static final TagKey<Block> RADIATION_BARRIER = block("radiation_barrier");
    /** BE-less multiblock members whose placement and removal mark their structure dirty. */
    public static final TagKey<Block> MULTIBLOCK_PARTS = block("multiblock_parts");

    // --- items -------------------------------------------------------------------------------------

    /** Armour that reduces radiation effects (any mod may add to it). */
    public static final TagKey<Item> RADIATION_SHIELDING = item("radiation_shielding");
    /** Armour that counts only while it carries the lining component (Nerospace rad lining). */
    public static final TagKey<Item> RADIATION_SHIELDING_LINED = item("radiation_shielding_lined");

    // --- fluids ------------------------------------------------------------------------------------

    /** {@code c:steam}: live steam any mod may burn. */
    public static final TagKey<Fluid> C_STEAM = fluid("c", "steam");
    /** {@code c:heavy_water}. */
    public static final TagKey<Fluid> C_HEAVY_WATER = fluid("c", "heavy_water");
    /** {@code neropower:exhaust_steam}: spent steam, deliberately never in {@code c:steam}. */
    public static final TagKey<Fluid> EXHAUST_STEAM = fluid(NeroPowerCommon.MOD_ID, "exhaust_steam");

    private ModTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path));
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path));
    }

    private static TagKey<Fluid> fluid(String namespace, String path) {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(namespace, path));
    }
}

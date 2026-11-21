package za.co.neroland.neropower.registry;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Other mods' blocks looked up by registry id instead of by their registry classes, so NeroPower never
 * compiles against a sibling's internal {@code registry} package. A missing id resolves to {@code null}
 * and every caller skips it.
 */
public final class ForeignBlocks {

    /** NeroTech's Radiator (a cold sink, and a gallery prop). */
    public static final Identifier NEROTECH_RADIATOR = Identifier.fromNamespaceAndPath("nerotech", "radiator");
    /** NeroTech's Coolant Pump (gallery prop). */
    public static final Identifier NEROTECH_COOLANT_PUMP = Identifier.fromNamespaceAndPath("nerotech", "coolant_pump");
    /** NeroTech's Battery Bank (gallery prop; the slosh guard uses the {@code energy_storages} tag). */
    public static final Identifier NEROTECH_BATTERY_BANK = Identifier.fromNamespaceAndPath("nerotech", "battery_bank");
    /** NeroTech's Analytics Terminal (gallery prop). */
    public static final Identifier NEROTECH_ANALYTICS_TERMINAL =
            Identifier.fromNamespaceAndPath("nerotech", "analytics_terminal");

    private ForeignBlocks() {
    }

    /** The block registered under {@code id}, or null when no mod registers it. */
    @Nullable
    public static Block get(Identifier id) {
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block == null || block == Blocks.AIR ? null : block;
    }

    /** Whether {@code state} is the block registered under {@code id} (false when absent). */
    public static boolean is(@Nullable BlockState state, Identifier id) {
        Block block = get(id);
        return state != null && block != null && state.is(block);
    }
}

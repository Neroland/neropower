package za.co.neroland.neropower.registry.content;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

import za.co.neroland.neropower.fission.FissionTooltipItem;
import za.co.neroland.neropower.registry.ModItems;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;
import za.co.neroland.neropower.tool.ScramKeyItem;

/**
 * The two handheld tools that belong to no single family: the Beam Alignment Scope (held, the looked-at
 * emitter's renderer draws its beam's line of sight and the first block in the way through
 * {@code client.render.BeamScopeOverlay}, which finds it by registry id; nothing is sent to the client)
 * and the SCRAM Key (proximity SCRAM, {@link ScramKeyItem}).
 */
public final class ToolContent {

    public static final RegistryEntry<Item> BEAM_ALIGNMENT_SCOPE = ModItems.ITEMS.register("beam_alignment_scope",
            key -> new FissionTooltipItem(new Item.Properties().setId(key).stacksTo(1),
                    "item.neropower.beam_alignment_scope.tooltip"));

    public static final RegistryEntry<ScramKeyItem> SCRAM_KEY = ModItems.ITEMS.register("scram_key",
            key -> new ScramKeyItem(new Item.Properties().setId(key).stacksTo(1)));

    private ToolContent() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }

    /** The tools group of the creative order. */
    public static List<RegistryEntry<? extends ItemLike>> creativeItems() {
        return List.of(BEAM_ALIGNMENT_SCOPE, SCRAM_KEY);
    }
}

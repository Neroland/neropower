package za.co.neroland.neropower.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Geiger Counter. While it is in a hand the server writes a {@link GeigerReading} on it every
 * {@value RadiationMath#GEIGER_INTERVAL} ticks ({@link RadiationTicker}); the client draws the HUD and
 * plays the clicks from that ({@code radiation.client.GeigerReadModel}). Nothing about the holder is
 * kept: the reading is the world around it, and it is removed again once the counter leaves the hand.
 *
 * <p>The reading changes every second, so the re-equip bob is switched off for component-only changes
 * through each loader's item hook (NeoForge/Forge {@code shouldCauseReequipAnimation}, Fabric
 * {@code allowComponentsUpdateAnimation}); the methods are plain on the other loaders.
 */
public class GeigerCounterItem extends Item {

    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    /** NeoForge / Forge: bob only when the slot or item changes, not for a new reading. */
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** Fabric: no bob for a component-only change. */
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack,
            ItemStack newStack) {
        return false;
    }

    //? if >=1.21.5 {
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
    //?} else {
    /*@Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        java.util.function.Consumer<Component> tooltip = lines::add;
    *///?}
        tooltip.accept(Component.translatable("item.neropower.geiger_counter.tooltip").withStyle(ChatFormatting.GRAY));
    }
}

package za.co.neroland.neropower.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import za.co.neroland.neropower.registry.content.RadiationContent;

/**
 * The Dosimeter Badge: while it sits in a player's inventory in a contaminated chunk it adds level / 10
 * to its own {@code neropower:dosimeter_reading} every {@value RadiationMath#DOSIMETER_INTERVAL} ticks
 * (capped at {@value RadiationMath#DOSIMETER_CAP}). The reading lives on the item only: no player id, no
 * name, nothing on the player. Crafting the badge alone gives a fresh one (reset).
 */
public class DosimeterBadgeItem extends Item {

    public DosimeterBadgeItem(Properties properties) {
        super(properties);
    }

    /** The badge's reading (0 for a fresh badge). */
    public static int reading(ItemStack stack) {
        Integer value = stack.get(RadiationContent.DOSIMETER_READING.get());
        return value == null ? 0 : Math.max(0, value);
    }

    /** Add one interval in a chunk at {@code level}. */
    public static void expose(ItemStack stack, int level) {
        int before = reading(stack);
        int after = RadiationMath.dosimeterAdd(before, level);
        if (after != before) {
            stack.set(RadiationContent.DOSIMETER_READING.get(), after);
        }
    }

    /** NeoForge / Forge: no re-equip bob for a new reading. */
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    /** Fabric: no re-equip bob for a component-only change. */
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack,
            ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return reading(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return (int) Math.round(13.0D * reading(stack) / RadiationMath.DOSIMETER_CAP);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xB6F23A; // rad lime
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
        int reading = reading(stack);
        tooltip.accept(Component.translatable("item.neropower.dosimeter_badge.dose", reading)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.neropower.dosimeter_badge.step." + RadiationMath.dosimeterStep(reading))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.neropower.dosimeter_badge.reset").withStyle(ChatFormatting.DARK_GRAY));
    }
}

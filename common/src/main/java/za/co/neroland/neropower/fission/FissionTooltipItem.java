package za.co.neroland.neropower.fission;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A fission item with one grey hint line ({@code item.neropower.<id>.tooltip}): the Hot Spent Fuel Rod
 * ("cool it in a Spent Fuel Rack first"), the Fuel Economizer ("fits a Fission Core's upgrade slot"),
 * and the Braided Fuel Rod (which also shows its burn-up as an item bar).
 */
public class FissionTooltipItem extends Item {

    private final String tooltipKey;

    public FissionTooltipItem(Properties properties, String tooltipKey) {
        super(properties);
        this.tooltipKey = tooltipKey;
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
        tooltip.accept(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
    }

    /** The Braided Fuel Rod: a hint line plus a burn-up bar from {@code neropower:burnup}. */
    public static class Braided extends FissionTooltipItem {

        public Braided(Properties properties, String tooltipKey) {
            super(properties, tooltipKey);
        }

        @Override
        public boolean isBarVisible(ItemStack stack) {
            return FissionCoreBlockEntity.burnupOf(stack) > 0;
        }

        @Override
        public int getBarWidth(ItemStack stack) {
            int left = FissionMath.MAX_BURNUP - FissionCoreBlockEntity.burnupOf(stack);
            return Math.round(13.0F * left / FissionMath.MAX_BURNUP);
        }

        @Override
        public int getBarColor(ItemStack stack) {
            return 0x7FD6C8; // braided teal
        }
    }
}

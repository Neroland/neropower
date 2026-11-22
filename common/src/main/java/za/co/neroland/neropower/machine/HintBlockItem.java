package za.co.neroland.neropower.machine;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * A block item with one grey hint line ({@code item.neropower.<id>.tooltip}) for the parts that have no
 * screen of their own (moderators, blanket, shroud, spindle, stator, coils, shielding, the ports): what
 * the block does, or the one action it needs, at the moment of placing. Static text; no player data.
 */
public class HintBlockItem extends BlockItem {

    private final String tooltipKey;

    public HintBlockItem(Block block, Item.Properties properties, String tooltipKey) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
    }

    /** The hint's lang key for item id {@code name}. */
    public static String keyFor(String name) {
        return "item.neropower." + name + ".tooltip";
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
}

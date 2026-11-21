package za.co.neroland.neropower.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.registry.content.RadiationContent;

/**
 * Decontaminant: right-click any block to take {@code decontaminantStrength} off that chunk's
 * contamination, consuming one (not in creative), with a 20-tick cooldown. It changes no block. It
 * respects protection like every NeroPower interaction ({@link Protection}), and does nothing (and is
 * not used up) while radiation is off, because nothing may be written then.
 */
public class DecontaminantItem extends Item {

    /** Ticks between uses. */
    public static final int COOLDOWN = 20;

    public DecontaminantItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        ItemStack stack = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        if (!RadiationConfig.enabled(level.getServer().isDedicatedServer())) {
            player.sendSystemMessage(Component.translatable("gui.neropower.rad.off"), true);
            return InteractionResult.FAIL;
        }
        if (!Protection.get().mayInteract(player, level, pos)) {
            return InteractionResult.FAIL;
        }
        long chunk = RadiationMath.chunkKeyOfBlock(pos.getX(), pos.getZ());
        ContaminationState state = ContaminationState.get(level);
        int before = state.map().level(chunk);
        if (before > 0) {
            state.add(chunk, -RadiationConfig.decontaminantStrength(), RadiationConfig.maxChunksPerDimension());
        }
        int after = state.map().level(chunk);
        level.playSound(null, pos, RadiationContent.DECONTAMINANT_SPRAY.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendSystemMessage(Component.translatable("item.neropower.decontaminant.result", after), true);
        //? if >=1.21.2 {
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        //?} else {
        /*player.getCooldowns().addCooldown(this, COOLDOWN);
        *///?}
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
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
        tooltip.accept(Component.translatable("item.neropower.decontaminant.tooltip").withStyle(ChatFormatting.GRAY));
    }
}

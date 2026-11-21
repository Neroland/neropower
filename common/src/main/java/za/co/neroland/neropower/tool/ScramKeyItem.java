package za.co.neroland.neropower.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.fission.FissionTooltipItem;
import za.co.neroland.neropower.protection.Protection;
import za.co.neroland.neropower.registry.ModSounds;

/**
 * SCRAM Key: use it on a Fission Core, or on any block of a formed reactor's shell, to SCRAM that reactor.
 * Proximity only: it acts through the block the player is touching (vanilla interaction reach), never
 * at range, and respects protection like every NeroPower interaction. One turn a second.
 */
public class ScramKeyItem extends FissionTooltipItem {

    /** Ticks between turns. */
    public static final int COOLDOWN = 20;
    /** The largest reactor edge is 7, so a core is at most 6 blocks from any of its shell blocks. */
    static final int SEARCH = 6;

    public ScramKeyItem(Properties properties) {
        super(properties, "item.neropower.scram_key.tooltip");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return context.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        BlockPos clicked = context.getClickedPos();
        FissionCoreBlockEntity core = reactorAt(level, clicked);
        if (core == null) {
            return InteractionResult.PASS;
        }
        if (!Protection.get().mayInteract(player, level, core.getBlockPos())) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (core.scrammed()) {
            player.sendSystemMessage(Component.translatable("message.neropower.scram_key.already"), true);
        } else {
            core.requestScram();
            level.playSound(null, clicked, ModSounds.SCRAM_KEY_TURN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("message.neropower.scram_key.scrammed"), true);
        }
        //? if >=1.21.2 {
        player.getCooldowns().addCooldown(stack, COOLDOWN);
        //?} else {
        /*player.getCooldowns().addCooldown(this, COOLDOWN);
        *///?}
        return InteractionResult.SUCCESS;
    }

    /**
     * The reactor the clicked block belongs to: the core itself, or the nearest formed core whose shell
     * reaches the clicked block (Chebyshev distance under the shell edge). {@code null} if none.
     */
    static FissionCoreBlockEntity reactorAt(ServerLevel level, BlockPos clicked) {
        if (level.getBlockEntity(clicked) instanceof FissionCoreBlockEntity direct) {
            return direct;
        }
        FissionCoreBlockEntity best = null;
        int bestDist = Integer.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(clicked.offset(-SEARCH, -SEARCH, -SEARCH),
                clicked.offset(SEARCH, SEARCH, SEARCH))) {
            BlockEntity be = level.getBlockEntity(p);
            if (!(be instanceof FissionCoreBlockEntity core) || !core.formed()) {
                continue;
            }
            int d = Math.max(Math.abs(p.getX() - clicked.getX()),
                    Math.max(Math.abs(p.getY() - clicked.getY()), Math.abs(p.getZ() - clicked.getZ())));
            if (d <= core.shellSize() - 1 && d < bestDist) {
                best = core;
                bestDist = d;
            }
        }
        return best;
    }
}

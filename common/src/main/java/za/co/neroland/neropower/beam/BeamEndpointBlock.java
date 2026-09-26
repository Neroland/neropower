package za.co.neroland.neropower.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerotech.item.ConfiguratorItem;
import za.co.neroland.nerotech.item.ConfiguratorState;
import za.co.neroland.nerotech.registry.ModDataComponents;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;

/**
 * Shared base for the four beam endpoint blocks: NeroTech's Configurator, used on one of these in
 * its <i>configure</i> mode, links instead of wrenching a face — NeroPower cannot teach NeroTech's
 * item about beams, so the block takes the click before the item sees it ({@link #useItemOn}
 * consuming the interaction; NeroTech's base returns {@code PASS} for the wrench, which is what
 * lets a plain machine hand the click on to the item). Copy/paste mode is left to the item, so a
 * side-config paste onto a beam block still works; face cycling on a beam block is via the GUI's
 * Side Config tab.
 *
 * <p><b>Why not sneak-only:</b> vanilla's {@code ServerPlayerGameMode.useItemOn} skips the block's
 * use hooks entirely when the player is sneaking with an item in hand, so a sneak-gated block hook
 * would never fire on any loader — the plain click is the one the block reliably sees. The link
 * flow itself lives in {@link BeamLinking}; the block only routes.
 */
public abstract class BeamEndpointBlock extends NeroPowerMachineBlock {

    protected BeamEndpointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof ConfiguratorItem && !copyPasteMode(stack)) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof BeamEndpointBlockEntity endpoint) {
                BeamLinking.handle(serverLevel, serverPlayer, pos, endpoint);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Whether the wrench is in copy/paste mode (NeroTech's {@code configurator_state} component). */
    private static boolean copyPasteMode(ItemStack stack) {
        ConfiguratorState configuratorState = stack.get(ModDataComponents.CONFIGURATOR_STATE.get());
        return configuratorState != null && configuratorState.copyPaste();
    }
}

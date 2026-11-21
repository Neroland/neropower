package za.co.neroland.neropower.turbine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Vane Set (steel / cindersteel / superconducting): right-click a bare Turbine Spindle to mount it.
 * The set is consumed into the spindle's {@code vanes} state and comes back when the spindle is broken.
 */
public class VaneSetItem extends Item {

    private final VaneTier tier;

    public VaneSetItem(Properties properties, VaneTier tier) {
        super(properties);
        this.tier = tier;
    }

    /** The tier this set mounts as. */
    public VaneTier tier() {
        return this.tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TurbineSpindleBlock)
                || TurbineSpindleBlock.vanes(state) != VaneTier.NONE) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && TurbineSpindleBlock.mount(level, pos, state, this.tier)) {
            Player player = context.getPlayer();
            ItemStack stack = context.getItemInHand();
            if (player == null || !player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}

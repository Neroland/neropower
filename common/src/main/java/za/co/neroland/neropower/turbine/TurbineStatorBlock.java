package za.co.neroland.neropower.turbine;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.multiblock.MultiblockPartBlock;

/**
 * Turbine Stator: one of the eight segments of a stator slice. BE-less; right-click a bare stator with a
 * NeroTech coil ({@code #nerotech:coils/tier1..3}) to wind it, which sets {@code winding} and consumes the
 * coil. The winding sets the stator's conversion ceiling ({@code turbineStatorCeilingTier*}). Breaking a
 * wound stator drops the stator and one coil of its tier (loot conditions on the state).
 */
public class TurbineStatorBlock extends MultiblockPartBlock {

    public static final MapCodec<TurbineStatorBlock> CODEC = BlockCodecs.simple(TurbineStatorBlock::new);

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final EnumProperty<WindingTier> WINDING = EnumProperty.create("winding", WindingTier.class);

    /** NeroTech's coil tiers, by tag (no class dependency on NeroTech's items). */
    public static final TagKey<Item> COILS_TIER1 = coilTag("tier1");
    public static final TagKey<Item> COILS_TIER2 = coilTag("tier2");
    public static final TagKey<Item> COILS_TIER3 = coilTag("tier3");

    @SuppressWarnings("this-escape")
    public TurbineStatorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Z)
                .setValue(WINDING, WindingTier.NONE));
    }

    private static TagKey<Item> coilTag(String tier) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("nerotech", "coils/" + tier));
    }

    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, WINDING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(AXIS, rotateAxis(state.getValue(AXIS), rotation));
    }

    /** X and Z swap under a quarter turn; Y never changes. */
    static Direction.Axis rotateAxis(Direction.Axis axis, Rotation rotation) {
        if (axis == Direction.Axis.Y) {
            return axis;
        }
        return switch (rotation) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            default -> axis;
        };
    }

    /** The winding of a stator state ({@link WindingTier#NONE} for anything else). */
    public static WindingTier winding(BlockState state) {
        return state.hasProperty(WINDING) ? state.getValue(WINDING) : WindingTier.NONE;
    }

    /** The winding a coil stack provides, or {@link WindingTier#NONE}. */
    public static WindingTier windingOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return WindingTier.NONE;
        }
        if (stack.is(COILS_TIER3)) {
            return WindingTier.TIER3;
        }
        if (stack.is(COILS_TIER2)) {
            return WindingTier.TIER2;
        }
        return stack.is(COILS_TIER1) ? WindingTier.TIER1 : WindingTier.NONE;
    }

    //? if >=1.21.2 {
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
    //?} else {
    /*@Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    *///?}
        WindingTier tier = windingOf(stack);
        if (tier == WindingTier.NONE || winding(state) != WindingTier.NONE) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, state.setValue(WINDING, tier), Block.UPDATE_ALL);
            notifyChanged(level, pos);
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(1);
            }
        }
        //? if >=1.21.2 {
        return InteractionResult.SUCCESS;
        //?} else {
        /*return net.minecraft.world.ItemInteractionResult.SUCCESS;
        *///?}
    }
}

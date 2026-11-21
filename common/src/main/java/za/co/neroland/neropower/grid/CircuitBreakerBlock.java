package za.co.neroland.neropower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.GridContent;

/**
 * Circuit Breaker block: input at the back, output at the front, {@link #TRIPPED} (handle down, flag
 * window) and the alarm state. A right-click on the <b>front</b> face of a tripped breaker resets it;
 * any other right-click opens its screen. Comparator: 15 tripped, else load / 67.
 */
public class CircuitBreakerBlock extends NeroPowerMachineBlock {

    public static final BooleanProperty TRIPPED = BooleanProperty.create("tripped");

    public static final MapCodec<CircuitBreakerBlock> CODEC = BlockCodecs.simple(CircuitBreakerBlock::new);

    @SuppressWarnings("this-escape")
    public CircuitBreakerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ALARM, false)
                .setValue(TRIPPED, false));
    }

    protected MapCodec<CircuitBreakerBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAlarm() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(TRIPPED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (state.getValue(TRIPPED) && hit.getDirection() == state.getValue(FACING)) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CircuitBreakerBlockEntity breaker
                    && breaker.reset() && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("neropower.circuit_breaker.reset"), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    //? if >=1.21.9 {
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
    //?} else {
    /*protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
    *///?}
        return level.getBlockEntity(pos) instanceof CircuitBreakerBlockEntity breaker ? breaker.comparatorSignal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CircuitBreakerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GridContent.CIRCUIT_BREAKER_BE.get();
    }
}

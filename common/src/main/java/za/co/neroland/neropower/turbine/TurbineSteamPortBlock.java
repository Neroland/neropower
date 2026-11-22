package za.co.neroland.neropower.turbine;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.multiblock.MultiblockPartBlock;
import za.co.neroland.neropower.registry.content.TurbineContent;

/**
 * Turbine Steam Port: a shroud face block (side faces only) carrying the turbine's steam. Right-click
 * with an empty hand cycles {@code port_mode}: {@code inlet} (live {@code c:steam} in), {@code exhaust}
 * (exhaust steam out to a pipe or Steam Condenser) or {@code vent} (exhaust lost to the air at reduced
 * yield). Placing or removing a port marks its turbine dirty, like any shroud part.
 */
public class TurbineSteamPortBlock extends NeroPowerMachineBlock {

    public static final MapCodec<TurbineSteamPortBlock> CODEC = BlockCodecs.simple(TurbineSteamPortBlock::new);

    public static final EnumProperty<PortMode> PORT_MODE = EnumProperty.create("port_mode", PortMode.class);

    public TurbineSteamPortBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<TurbineSteamPortBlock> codec() {
        return CODEC;
    }

    /** In-world flow state ({@code status}: idle / working / starved / tripped), set by the port on change. */
    @Override
    protected boolean hasStatus() {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PORT_MODE);
    }

    /** The mode of a port state ({@link PortMode#INLET} for anything else). */
    public static PortMode mode(BlockState state) {
        return state.hasProperty(PORT_MODE) ? state.getValue(PORT_MODE) : PortMode.INLET;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        noteOperator(level, pos, player);
        if (!level.isClientSide()) {
            PortMode next = mode(state).next();
            level.setBlock(pos, state.setValue(PORT_MODE, next), Block.UPDATE_ALL);
            Component message = Component.translatable("message.neropower.turbine_steam_port.mode",
                    Component.translatable("gui.neropower.turbine.port_mode." + next.getSerializedName()));
            //? if >=26.1 {
            player.sendSystemMessage(message);
            //?} else {
            /*player.displayClientMessage(message, true);
            *///?}
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            MultiblockPartBlock.notifyChanged(level, pos);
        }
    }

    //? if >=1.21.5 {
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MultiblockPartBlock.notifyChanged(level, pos);
    }
    //?} else {
    /*@Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            MultiblockPartBlock.notifyChanged(level, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
    *///?}

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineSteamPortBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return TurbineContent.TURBINE_STEAM_PORT_TYPE.get();
    }
}

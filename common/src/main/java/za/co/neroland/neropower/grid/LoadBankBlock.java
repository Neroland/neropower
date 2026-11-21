package za.co.neroland.neropower.grid;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.registry.content.GridContent;

/** Load Bank block: {@link #HEAT} 0..4 (element glow step) set by its block entity only. */
public class LoadBankBlock extends NeroPowerMachineBlock {

    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 4);

    public static final MapCodec<LoadBankBlock> CODEC = BlockCodecs.simple(LoadBankBlock::new);

    @SuppressWarnings("this-escape")
    public LoadBankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HEAT, 0));
    }

    protected MapCodec<LoadBankBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoadBankBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return GridContent.LOAD_BANK_BE.get();
    }
}

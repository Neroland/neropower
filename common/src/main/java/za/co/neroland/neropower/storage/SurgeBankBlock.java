package za.co.neroland.neropower.storage;

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
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Surge Bank block (0.4.0): small, fast store. Carries {@link #CANS} (0..6 capacitor cans lit) which its
 * block entity keeps in step with the stored charge, so the model shows the fill with no renderer.
 */
public class SurgeBankBlock extends NeroPowerMachineBlock {

    /** Lit cans: 0 empty .. 6 full. Set by the block entity only. */
    public static final int CAN_LEVELS = 6;
    public static final IntegerProperty CANS = IntegerProperty.create("cans", 0, CAN_LEVELS);

    public static final MapCodec<SurgeBankBlock> CODEC = BlockCodecs.simple(SurgeBankBlock::new);

    @SuppressWarnings("this-escape")
    public SurgeBankBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CANS, 0));
    }

    protected MapCodec<SurgeBankBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CANS);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SurgeBankBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return StorageContent.SURGE_BANK.get();
    }
}

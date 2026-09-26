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

/**
 * Battery Cell block (Stage 5) — one instance per {@link BatteryTier}, directional, ticking its
 * {@link BatteryCellBlockEntity}. Carries the {@link #CHARGE} property (0..4) the block-entity keeps
 * in step with the stored charge, so the model overlay shows the fill with no renderer.
 */
public class BatteryCellBlock extends NeroPowerMachineBlock {

    /** Stored charge in {@link PoolMath#CHARGE_LEVELS} buckets: 0 empty .. 4 full. Set by the block-entity only. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, PoolMath.CHARGE_LEVELS);

    private final BatteryTier tier;
    private final MapCodec<BatteryCellBlock> codec;

    @SuppressWarnings("this-escape")
    public BatteryCellBlock(Properties properties, BatteryTier tier) {
        super(properties);
        this.tier = tier;
        this.codec = BlockCodecs.simple(props -> new BatteryCellBlock(props, tier));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CHARGE, 0));
    }

    public BatteryTier tier() {
        return this.tier;
    }

    protected MapCodec<BatteryCellBlock> codec() {
        return this.codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CHARGE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryCellBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return StorageContent.BATTERY_CELL.get();
    }
}

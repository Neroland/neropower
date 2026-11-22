package za.co.neroland.neropower.fission;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.registry.content.FissionContent;

/**
 * Reactor Steam Port: a reactor wall block (faces only, up to four per shell) that lets the core run in
 * steam mode: water (or cryo coolant) in, steam out, no direct power. It has no screen of its own; its
 * tanks are summed in the Fission Core screen.
 */
public class ReactorSteamPortBlock extends ReactorShellPortBlock {

    public static final MapCodec<ReactorSteamPortBlock> CODEC = BlockCodecs.simple(ReactorSteamPortBlock::new);

    public ReactorSteamPortBlock(Properties properties) {
        super(properties);
    }

    /** In-world status for the painted state models (decision 012 addendum). */
    @Override
    protected boolean hasStatus() {
        return true;
    }

    protected MapCodec<ReactorSteamPortBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorSteamPortBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return FissionContent.REACTOR_STEAM_PORT_TYPE.get();
    }
}

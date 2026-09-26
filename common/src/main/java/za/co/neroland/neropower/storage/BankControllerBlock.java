package za.co.neroland.neropower.storage;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.machine.AbstractMachineBlockEntity;
import za.co.neroland.nerolandcore.registry.BlockCodecs;

import za.co.neroland.neropower.machine.NeroPowerMachineBlock;

/**
 * Battery Bank Controller block (Stage 5) — directional, ticks its {@link BankControllerBlockEntity}.
 * A plain right-click opens the bank GUI (the machine base); a <b>sneak</b> right-click with an
 * empty hand cycles the {@link BankMode} instead and tells the player the new mode on the action bar.
 * (Vanilla still routes an empty-handed sneak use to the block, so no packet is needed.)
 */
public class BankControllerBlock extends NeroPowerMachineBlock {

    public static final MapCodec<BankControllerBlock> CODEC = BlockCodecs.simple(BankControllerBlock::new);

    public BankControllerBlock(Properties properties) {
        super(properties);
    }

    protected MapCodec<BankControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof BankControllerBlockEntity controller) {
                BankMode mode = controller.cycleMode();
                // Action-bar feedback only (the Singularity Vault's report recipe); no chat spam.
                serverPlayer.sendSystemMessage(Component.translatable("neropower.bank_mode.set",
                        Component.translatable(mode.translationKey())), true);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankControllerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends AbstractMachineBlockEntity> machineType() {
        return StorageContent.BATTERY_BANK_CONTROLLER.get();
    }
}

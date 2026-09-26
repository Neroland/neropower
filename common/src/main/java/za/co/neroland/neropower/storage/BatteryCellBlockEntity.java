package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.sideconfig.Channel;
import za.co.neroland.nerolandcore.sideconfig.SideConfig;
import za.co.neroland.nerolandcore.sideconfig.SidePreset;

import za.co.neroland.nerotech.machine.MachineStatus;

import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Battery Cell (Stage 5) — one tier of NeroPower storage. A single block with a
 * {@link BatteryTier tier}-sized buffer whose external I/O is the tier's limit; it generates and
 * consumes nothing. Standing alone it behaves like NeroTech's Battery Bank; next to a
 * {@link BankControllerBlockEntity} it also joins that bank's pool.
 *
 * <p><b>Side posture:</b> Core's {@link SidePreset#STORAGE} — every face I/O — but with Core's
 * auto-eject <i>off</i>: the cell pushes through {@link StorageEnergy} instead, which never feeds
 * another storage block (the slosh guard), so a wall of cells does not shuffle charge around all day.
 * A player who wants a strict layout reconfigures faces with the Configurator as usual.
 *
 * <p>The tier is read from the block state's block (one block-entity type serves all three cells);
 * the {@link BatteryCellBlock#CHARGE} property follows the stored charge in 0..4 buckets.
 */
public class BatteryCellBlockEntity extends NeroPowerMachineBlockEntity {

    private final BatteryTier tier;

    public BatteryCellBlockEntity(BlockPos pos, BlockState state) {
        super(StorageContent.BATTERY_CELL.get(), pos, state, 0,
                tierOf(state).capacity(), tierOf(state).maxIo());
        this.tier = tierOf(state);
        setupSideConfig(SideConfig.builder()
                .channel(Channel.ENERGY)
                .defaultPreset(SidePreset.STORAGE)
                .autoEject(Channel.ENERGY, false)
                .build());
    }

    /** The tier of the cell block in {@code state}; BASIC for anything that is not a cell. */
    private static BatteryTier tierOf(BlockState state) {
        return state.getBlock() instanceof BatteryCellBlock cell ? cell.tier() : BatteryTier.BASIC;
    }

    public BatteryTier tier() {
        return this.tier;
    }

    /** The cell's raw buffer — the bank pool distributes straight into / out of this. */
    public EnergyBuffer cellBuffer() {
        return energyBuffer();
    }

    @Override
    protected void tickMachine(Level level, BlockPos pos, BlockState state) {
        // A buffer does no work: no heat, no pollution, no progress bar. "Active" means holding a
        // charge; the block-state bucket drives the model overlay.
        long amount = getEnergy().getAmount();
        boolean charged = amount > 0;
        setActive(charged);
        if (!charged) {
            reportStatus(MachineStatus.NO_ENERGY);
        }
        int level4 = PoolMath.chargeLevel(amount, getEnergy().getCapacity());
        if (state.hasProperty(BatteryCellBlock.CHARGE) && state.getValue(BatteryCellBlock.CHARGE) != level4) {
            level.setBlock(pos, state.setValue(BatteryCellBlock.CHARGE, level4), Block.UPDATE_CLIENTS);
        }
        StorageEnergy.pushToNeighbours(level, pos, energyBuffer(), this.tier.maxIo(), sideConfig(), null, -1);
    }

    /** Shedding a buffer would achieve nothing — it has no work rate to throttle. */
    @Override
    public boolean shedable() {
        return false;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.neropower.battery_cell_" + this.tier.key());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BatteryCellMenu(containerId, playerInventory, this, this.data);
    }
}

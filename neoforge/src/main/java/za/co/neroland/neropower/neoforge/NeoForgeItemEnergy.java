package za.co.neroland.neropower.neoforge;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
//? if >=1.21.9 {
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
//?} else {
/*import net.neoforged.neoforge.energy.IEnergyStorage;
*///?}

import za.co.neroland.neropower.energy.PortableEnergy;
import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * Power cells on NeoForge's item energy capability (Forge Energy, int-clamped), delegating to the common
 * {@link PortableEnergy} seam. The cell's charge lives in its {@code neropower:cell_energy}
 * component on the stack the capability was asked for.
 */
final class NeoForgeItemEnergy {

    private NeoForgeItemEnergy() {
    }

    static void register(RegisterCapabilitiesEvent event) {
        //? if >=1.21.9 {
        event.registerItem(Capabilities.Energy.ITEM, (stack, access) -> new CellHandler(stack),
                StorageContent.POWER_CELL_BASIC.get(), StorageContent.POWER_CELL_ADVANCED.get(),
                StorageContent.POWER_CELL_ELITE.get());
        //?} else {
        /*event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new CellStorage(stack),
                StorageContent.POWER_CELL_BASIC.get(), StorageContent.POWER_CELL_ADVANCED.get(),
                StorageContent.POWER_CELL_ELITE.get());
        *///?}
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    //? if >=1.21.9 {
    /** Transaction-aware view: mutations apply at once and are undone if the transaction aborts. */
    private static final class CellHandler extends SnapshotJournal<Long> implements EnergyHandler {
        private final ItemStack stack;

        CellHandler(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public long getAmountAsLong() {
            return PortableEnergy.storedFe(this.stack);
        }

        @Override
        public long getCapacityAsLong() {
            return PortableEnergy.capacityFe(this.stack);
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            if (amount <= 0 || PortableEnergy.receiveFe(this.stack, amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return clamp(PortableEnergy.receiveFe(this.stack, amount, false));
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            if (amount <= 0 || PortableEnergy.extractFe(this.stack, amount, true) <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            return clamp(PortableEnergy.extractFe(this.stack, amount, false));
        }

        @Override
        protected Long createSnapshot() {
            return PortableEnergy.stored(this.stack);
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            PortableEnergy.set(this.stack, snapshot);
        }
    }
    //?} else {
    /*private record CellStorage(ItemStack stack) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            return clamp(PortableEnergy.receiveFe(this.stack, max, simulate));
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            return clamp(PortableEnergy.extractFe(this.stack, max, simulate));
        }

        @Override
        public int getEnergyStored() {
            return clamp(PortableEnergy.storedFe(this.stack));
        }

        @Override
        public int getMaxEnergyStored() {
            return clamp(PortableEnergy.capacityFe(this.stack));
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
    *///?}
}

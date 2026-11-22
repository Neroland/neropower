package za.co.neroland.neropower.energy;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.energy.ItemEnergyLookup;
import za.co.neroland.nerolandcore.energy.ItemEnergyProvider;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * Exposes NeroPower's portable energy items (Power Cells) to Neroland Core's item-energy seam, so any
 * Nero charger or tool can fill or drain them through {@link ItemEnergyLookup#find}. The view reads and
 * writes the stack's own energy component through {@link PortableEnergy}, with the same per-transfer
 * limit the stack has everywhere else. Holds no state of its own.
 */
public final class PortableEnergyProvider implements ItemEnergyProvider {

    public static final PortableEnergyProvider INSTANCE = new PortableEnergyProvider();

    private PortableEnergyProvider() {
    }

    /** Register with Core once, during common init. */
    public static void register() {
        ItemEnergyLookup.register(INSTANCE);
    }

    @Override
    @Nullable
    public NeroEnergyStorage find(ItemStack stack) {
        return PortableEnergy.holds(stack) ? new View(stack) : null;
    }

    /** A live view over one stack; every call goes straight to the stack's component. */
    private record View(ItemStack stack) implements NeroEnergyStorage {

        @Override
        public long getAmount() {
            return PortableEnergy.stored(this.stack);
        }

        @Override
        public long getCapacity() {
            return PortableEnergy.capacity(this.stack);
        }

        @Override
        public long insert(long maxAmount, boolean simulate) {
            return maxAmount <= 0L ? 0L : PortableEnergy.insert(this.stack, maxAmount, simulate);
        }

        @Override
        public long extract(long maxAmount, boolean simulate) {
            return maxAmount <= 0L ? 0L : PortableEnergy.extract(this.stack, maxAmount, simulate);
        }
    }
}

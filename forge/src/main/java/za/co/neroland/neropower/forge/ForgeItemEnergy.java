package za.co.neroland.neropower.forge;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.energy.PortableEnergy;

/**
 * Power cells (any {@code energy.PortableEnergyItem}) on Forge's item energy capability (Forge Energy,
 * int-clamped), delegating to the common {@link PortableEnergy} seam.
 */
final class ForgeItemEnergy {

    private static final Identifier CELL_ENERGY = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "cell_energy");

    private ForgeItemEnergy() {
    }

    static void register() {
        //? if >=1.21.6 {
        AttachCapabilitiesEvent.ItemStacks.BUS.addListener(ForgeItemEnergy::onAttach);
        //?} else {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, ForgeItemEnergy::onAttach);
        *///?}
    }

    //? if >=1.21.6 {
    private static void onAttach(AttachCapabilitiesEvent.ItemStacks event) {
    //?} else {
    /*private static void onAttach(AttachCapabilitiesEvent<ItemStack> event) {
    *///?}
        ItemStack stack = event.getObject();
        if (PortableEnergy.holds(stack)) {
            event.addCapability(CELL_ENERGY, new Provider(stack));
        }
    }

    private static int clamp(long value) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
    }

    private static final class Provider implements ICapabilityProvider {
        private final LazyOptional<IEnergyStorage> energy;

        Provider(ItemStack stack) {
            this.energy = LazyOptional.of(() -> new CellStorage(stack));
        }

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY.orEmpty(cap, this.energy);
        }
    }

    private record CellStorage(ItemStack stack) implements IEnergyStorage {
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
}

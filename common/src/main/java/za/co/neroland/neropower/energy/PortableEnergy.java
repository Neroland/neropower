package za.co.neroland.neropower.energy;

import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerolandcore.energy.EnergyConversions;

import za.co.neroland.neropower.registry.content.StorageContent;

/**
 * NeroPower's local item-energy seam (decision 008): Neroland Core has no item energy capability yet,
 * so every NeroPower user of portable energy (battery cell and surge bank charge slots, the Induction
 * Charger adapter, the loaders' FE item capabilities) goes through here. The charge is the
 * {@code neropower:cell_energy} component (Long NE) on a stack whose item is a
 * {@link PortableEnergyItem}; the stack carries nothing else and no player data.
 *
 * <p>All amounts are NE unless the method says FE. Rules live in {@link PortableEnergyMath}.
 */
public final class PortableEnergy {

    private PortableEnergy() {
    }

    /** Whether the stack is a portable energy item. */
    public static boolean holds(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof PortableEnergyItem;
    }

    /** NE stored on the stack (0 for anything that is not a portable energy item), clamped to capacity. */
    public static long stored(ItemStack stack) {
        if (!holds(stack)) {
            return 0L;
        }
        Long value = stack.get(StorageContent.CELL_ENERGY.get());
        long raw = value == null ? 0L : value;
        return Math.max(0L, Math.min(raw, capacity(stack)));
    }

    /** NE the stack can hold (0 for anything else). */
    public static long capacity(ItemStack stack) {
        return holds(stack) ? Math.max(0L, ((PortableEnergyItem) stack.getItem()).portableCapacity(stack)) : 0L;
    }

    /** NE the stack may move in one transfer (0 for anything else). */
    public static long io(ItemStack stack) {
        return holds(stack) ? Math.max(0L, ((PortableEnergyItem) stack.getItem()).portableIo(stack)) : 0L;
    }

    /** Insert up to {@code amount} NE; returns NE accepted. Changes the stack only when not simulating. */
    public static long insert(ItemStack stack, long amount, boolean simulate) {
        if (!holds(stack) || stack.getCount() != 1) {
            return 0L;
        }
        long stored = stored(stack);
        long accepted = PortableEnergyMath.acceptable(stored, capacity(stack), io(stack), amount);
        if (accepted > 0 && !simulate) {
            set(stack, stored + accepted);
        }
        return accepted;
    }

    /** Extract up to {@code amount} NE; returns NE given. Changes the stack only when not simulating. */
    public static long extract(ItemStack stack, long amount, boolean simulate) {
        if (!holds(stack) || stack.getCount() != 1) {
            return 0L;
        }
        long stored = stored(stack);
        long given = PortableEnergyMath.givable(stored, io(stack), amount);
        if (given > 0 && !simulate) {
            set(stack, stored - given);
        }
        return given;
    }

    /** Write the charge (clamped to 0..capacity). */
    public static void set(ItemStack stack, long amount) {
        if (holds(stack)) {
            stack.set(StorageContent.CELL_ENERGY.get(), Math.max(0L, Math.min(amount, capacity(stack))));
        }
    }

    /**
     * The Induction Charger adapter ({@code api.WirelessCharging.ItemCharger}): accept up to {@code maxFe}
     * FE, converted to NE with Core's {@code neroToForgeRatio} (floor), and report the FE taken (ceiling).
     * 0 for anything that is not a portable energy item, or a full one.
     */
    public static long chargeFe(ItemStack stack, long maxFe, boolean simulate) {
        if (!holds(stack) || maxFe <= 0) {
            return 0L;
        }
        double ratio = EnergyConversions.neroToForgeRatio();
        long[] result = PortableEnergyMath.chargeFromFe(stored(stack), capacity(stack), io(stack), maxFe, ratio);
        if (result[0] <= 0) {
            return 0L;
        }
        if (!simulate) {
            insert(stack, result[0], false);
        }
        return result[1];
    }

    // --- FE views for the loaders' item energy capabilities (NeoForge / Forge; int-clamped there) ---------

    /** FE stored, for a loader capability (floor). */
    public static long storedFe(ItemStack stack) {
        return PortableEnergyMath.neToFeFloor(stored(stack), EnergyConversions.neroToForgeRatio());
    }

    /** FE capacity, for a loader capability (floor). */
    public static long capacityFe(ItemStack stack) {
        return PortableEnergyMath.neToFeFloor(capacity(stack), EnergyConversions.neroToForgeRatio());
    }

    /** Receive up to {@code maxFe} FE (a loader capability's receive); returns FE accepted. */
    public static long receiveFe(ItemStack stack, long maxFe, boolean simulate) {
        return chargeFe(stack, maxFe, simulate);
    }

    /**
     * Give up to {@code maxFe} FE (a loader capability's extract); returns FE delivered. The NE cost is
     * rounded up so a delivery is never free.
     */
    public static long extractFe(ItemStack stack, long maxFe, boolean simulate) {
        if (!holds(stack) || maxFe <= 0) {
            return 0L;
        }
        double ratio = EnergyConversions.neroToForgeRatio();
        long ne = PortableEnergyMath.givable(stored(stack), io(stack), Long.MAX_VALUE);
        long fe = Math.min(maxFe, PortableEnergyMath.neToFeFloor(ne, ratio));
        if (fe <= 0) {
            return 0L;
        }
        long cost = PortableEnergyMath.feToNeCeil(fe, ratio);
        if (cost > ne) {
            return 0L;
        }
        if (!simulate) {
            extract(stack, cost, false);
        }
        return fe;
    }
}

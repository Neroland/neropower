package za.co.neroland.neropower.generation;

import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;
import za.co.neroland.nerolandcore.gas.GasBuffer;
import za.co.neroland.nerolandcore.gas.NeroGasStorage;
import za.co.neroland.nerolandcore.gas.NeroGases;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * What the Hydrogen Fuel Cell burns, by id. Neither NeroTech nor Nerospace tags hydrogen or oxygen, so
 * the cell names the gases itself: Core gas {@code nerotech:hydrogen}, and the ecosystem's one oxygen
 * {@code nerospace:oxygen} (NeroTech's legacy {@code nerotech:oxygen} is accepted and stored as it). On
 * the loaders' standard fluid surface, where other mods' pipes carry gases as transport fluids, the
 * cell takes the fluids in its own tags {@code neropower:fuel_cell_hydrogen} and
 * {@code neropower:fuel_cell_oxygen} (NeroTech's transport fluids, Nerospace's entry optional). No
 * class of NeroTech's or Nerospace's is touched.
 */
public final class FuelCellGases {

    public static final Identifier HYDROGEN = Identifier.fromNamespaceAndPath("nerotech", "hydrogen");
    public static final Identifier OXYGEN = Identifier.fromNamespaceAndPath("nerospace", "oxygen");
    public static final Identifier LEGACY_OXYGEN = Identifier.fromNamespaceAndPath("nerotech", "oxygen");

    /** Transport-fluid ids a gas is shown as on the standard fluid surface. */
    public static final Identifier HYDROGEN_FLUID = Identifier.fromNamespaceAndPath("nerotech", "hydrogen");
    public static final Identifier OXYGEN_FLUID = Identifier.fromNamespaceAndPath("nerotech", "oxygen");

    public static final TagKey<Fluid> HYDROGEN_FLUIDS = TagKey.create(Registries.FLUID,
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "fuel_cell_hydrogen"));
    public static final TagKey<Fluid> OXYGEN_FLUIDS = TagKey.create(Registries.FLUID,
            Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "fuel_cell_oxygen"));

    private FuelCellGases() {
    }

    /** The id a gas is stored as: legacy oxygen becomes the shared one. */
    public static Identifier canonical(@Nullable Identifier gas) {
        if (gas == null) {
            return NeroGases.EMPTY;
        }
        return LEGACY_OXYGEN.equals(gas) ? OXYGEN : gas;
    }

    public static boolean isHydrogen(@Nullable Identifier gas) {
        return HYDROGEN.equals(canonical(gas));
    }

    public static boolean isOxygen(@Nullable Identifier gas) {
        return OXYGEN.equals(canonical(gas));
    }

    /** A gas tank that takes one gas (after {@link #canonical}). */
    public static final class Tank implements NeroGasStorage {

        private final GasBuffer buffer;
        private final Predicate<Identifier> accepts;

        public Tank(long capacity, Predicate<Identifier> accepts, Runnable onChanged) {
            this.buffer = new GasBuffer(capacity, onChanged);
            this.accepts = accepts;
        }

        @Override
        public Identifier getGas() {
            return this.buffer.getGas();
        }

        @Override
        public long getAmount() {
            return this.buffer.getAmount();
        }

        @Override
        public long getCapacity() {
            return this.buffer.getCapacity();
        }

        @Override
        public long fill(Identifier gas, long amount, boolean simulate) {
            Identifier id = canonical(gas);
            return this.accepts.test(id) ? this.buffer.fill(id, amount, simulate) : 0L;
        }

        @Override
        public long drain(long amount, boolean simulate) {
            return this.buffer.drain(amount, simulate);
        }

        public boolean accepts(Identifier gas) {
            return this.accepts.test(canonical(gas));
        }

        public void resize(long capacity) {
            if (capacity != this.buffer.getCapacity()) {
                this.buffer.resize(capacity);
            }
        }

        public void save(ValueOutput output, String key) {
            if (this.buffer.getRawAmount() <= 0) {
                return;
            }
            output.putString(key + "Gas", this.buffer.getRawGas().toString());
            output.putInt(key + "Amount", this.buffer.getRawAmount());
        }

        public void load(ValueInput input, String key) {
            Identifier gas = canonical(Identifier.tryParse(input.getStringOr(key + "Gas", NeroGases.EMPTY.toString())));
            int amount = input.getIntOr(key + "Amount", 0);
            if (amount <= 0 || !this.accepts.test(gas)) {
                this.buffer.setRaw(NeroGases.EMPTY, 0);
                return;
            }
            this.buffer.setRaw(gas, amount);
        }
    }

    /**
     * The two inlet tanks as one Core gas surface: a fill goes to the tank that takes that gas; nothing
     * can be drained back out through it (the cell's fuel is input only).
     */
    public record Inlets(Tank hydrogen, Tank oxygen) implements NeroGasStorage {

        @Override
        public Identifier getGas() {
            if (this.hydrogen.getAmount() > 0) {
                return this.hydrogen.getGas();
            }
            return this.oxygen.getAmount() > 0 ? this.oxygen.getGas() : NeroGases.EMPTY;
        }

        @Override
        public long getAmount() {
            return this.hydrogen.getAmount() > 0 ? this.hydrogen.getAmount() : this.oxygen.getAmount();
        }

        @Override
        public long getCapacity() {
            return this.hydrogen.getAmount() > 0 || this.oxygen.getAmount() <= 0
                    ? this.hydrogen.getCapacity() : this.oxygen.getCapacity();
        }

        @Override
        public long fill(Identifier gas, long amount, boolean simulate) {
            if (this.hydrogen.accepts(gas)) {
                return this.hydrogen.fill(gas, amount, simulate);
            }
            return this.oxygen.accepts(gas) ? this.oxygen.fill(gas, amount, simulate) : 0L;
        }

        @Override
        public long drain(long amount, boolean simulate) {
            return 0L;
        }
    }

    /**
     * A gas tank seen as its transport fluid on the standard fluid surface. Amounts pass through as mB.
     * Both directions stay open on this raw view (a loader's transactional handler must be able to undo
     * a transfer); the block entity's gated view makes the face input-only.
     */
    public record FluidView(Tank tank, TagKey<Fluid> accepted, Identifier shownAs, Identifier gas)
            implements NeroFluidStorage {

        @Override
        public Fluid getFluid() {
            if (this.tank.getAmount() <= 0) {
                return Fluids.EMPTY;
            }
            return BuiltInRegistries.FLUID.getValue(this.shownAs);
        }

        @Override
        public long getAmount() {
            return getFluid() == Fluids.EMPTY ? 0L : this.tank.getAmount();
        }

        @Override
        public long getCapacity() {
            return this.tank.getCapacity();
        }

        @Override
        public long fill(Fluid fluid, long amount, boolean simulate) {
            if (fluid == null || fluid == Fluids.EMPTY || !fluid.defaultFluidState().is(this.accepted)) {
                return 0L;
            }
            return this.tank.fill(this.gas, amount, simulate);
        }

        @Override
        public long drain(long amount, boolean simulate) {
            return this.tank.drain(amount, simulate);
        }
    }
}

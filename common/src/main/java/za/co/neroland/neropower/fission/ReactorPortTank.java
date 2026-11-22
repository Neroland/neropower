package za.co.neroland.neropower.fission;

import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.fluid.FluidBuffer;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * One Reactor Steam Port tank: a Core {@link FluidBuffer} that only takes the fluids its predicate
 * allows. Both directions stay open on the raw tank so a loader's transactional handler can always undo
 * a transfer; one-way faces come from the views the port hands out. World data only.
 */
public final class ReactorPortTank implements NeroFluidStorage, za.co.neroland.neropower.turbine.PortPool.Slot<Fluid> {

    private final FluidBuffer buffer;
    private final Predicate<Fluid> accepts;

    public ReactorPortTank(long capacity, Predicate<Fluid> accepts, Runnable onChanged) {
        this.buffer = new FluidBuffer(capacity, onChanged);
        this.accepts = accepts;
    }

    /** A predicate accepting any fluid in {@code tag}. */
    public static Predicate<Fluid> tagged(TagKey<Fluid> tag) {
        return fluid -> fluid != Fluids.EMPTY && fluid.defaultFluidState().is(tag);
    }

    /** Whether this tank would take {@code fluid} at all (ignoring room). */
    public boolean accepts(Fluid fluid) {
        return fluid != Fluids.EMPTY && this.accepts.test(fluid);
    }

    @Override
    public Fluid getFluid() {
        return this.buffer.getFluid();
    }

    @Override
    public long getAmount() {
        return this.buffer.getAmount();
    }

    @Override
    public long getCapacity() {
        return this.buffer.getCapacity();
    }

    /** Free space (mB). */
    public long room() {
        return Math.max(0L, getCapacity() - getAmount());
    }

    @Override
    public long fill(Fluid fluid, long amount, boolean simulate) {
        if (!accepts(fluid)) {
            return 0L;
        }
        return this.buffer.fill(fluid, amount, simulate);
    }

    @Override
    public long drain(long amount, boolean simulate) {
        return this.buffer.drain(amount, simulate);
    }

    // --- shared-tank slot --------------------------------------------------------

    @Override
    public Fluid slotFluid() {
        return getFluid();
    }

    @Override
    public boolean slotEmpty() {
        return getAmount() <= 0L || getFluid() == Fluids.EMPTY;
    }

    @Override
    public long slotAmount() {
        return getAmount();
    }

    @Override
    public long slotCapacity() {
        return getCapacity();
    }

    @Override
    public boolean slotAccepts(Fluid fluid) {
        return accepts(fluid);
    }

    @Override
    public long slotFill(Fluid fluid, long amount, boolean simulate) {
        return fill(fluid, amount, simulate);
    }

    @Override
    public long slotDrain(long amount, boolean simulate) {
        return drain(amount, simulate);
    }

    /** Follow a config change of the tank size (clamps the contents). */
    public void resize(long capacity) {
        if (capacity != this.buffer.getCapacity()) {
            this.buffer.resize(capacity);
        }
    }

    /** Write {@code <key>Fluid} (registry id) and {@code <key>Amount}; nothing when empty. */
    public void save(ValueOutput output, String key) {
        Fluid fluid = this.buffer.getRawFluid();
        if (fluid == Fluids.EMPTY || this.buffer.getRawAmount() <= 0) {
            return;
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        output.putString(key + za.co.neroland.neropower.turbine.PortTankSave.FLUID, id.toString());
        output.putInt(key + za.co.neroland.neropower.turbine.PortTankSave.AMOUNT, this.buffer.getRawAmount());
    }

    /** Read what {@link #save} wrote; an unknown or no-longer-accepted fluid loads as empty. */
    public void load(ValueInput input, String key) {
        String raw = input.getStringOr(key + za.co.neroland.neropower.turbine.PortTankSave.FLUID, "");
        int amount = input.getIntOr(key + za.co.neroland.neropower.turbine.PortTankSave.AMOUNT, 0);
        Identifier id = raw.isEmpty() ? null : Identifier.tryParse(raw);
        Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
        if (fluid == null || !accepts(fluid) || amount <= 0) {
            this.buffer.setRaw(Fluids.EMPTY, 0);
            return;
        }
        this.buffer.setRaw(fluid, amount);
    }
}

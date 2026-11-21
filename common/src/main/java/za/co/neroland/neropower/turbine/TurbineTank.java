package za.co.neroland.neropower.turbine;

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
 * A single-fluid tank (Core {@link FluidBuffer}) that only accepts the fluids its predicate allows. Both
 * directions stay open on the raw tank, so a loader's transactional handler can always undo a transfer;
 * one-way faces are applied by the views a block entity hands out. World data only.
 */
public final class TurbineTank implements NeroFluidStorage {

    private final FluidBuffer buffer;
    private final Predicate<Fluid> accepts;

    public TurbineTank(long capacity, Predicate<Fluid> accepts, Runnable onChanged) {
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

    /** Fill permille of capacity. */
    public int permille() {
        long capacity = getCapacity();
        return capacity <= 0L ? 0 : (int) Math.min(1000L, getAmount() * 1000L / capacity);
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

    /** Follow a config change of the tank size (clamps the contents). */
    public void resize(long capacity) {
        if (capacity != this.buffer.getCapacity()) {
            this.buffer.resize(capacity);
        }
    }

    /** Write {@code <key>Fluid} (registry id) and {@code <key>Amount}. */
    public void save(ValueOutput output, String key) {
        Fluid fluid = this.buffer.getRawFluid();
        if (fluid == Fluids.EMPTY || this.buffer.getRawAmount() <= 0) {
            return;
        }
        Identifier id = BuiltInRegistries.FLUID.getKey(fluid);
        output.putString(key + "Fluid", id.toString());
        output.putInt(key + "Amount", this.buffer.getRawAmount());
    }

    /** Read what {@link #save} wrote; an unknown or no-longer-accepted fluid loads as empty. */
    public void load(ValueInput input, String key) {
        String raw = input.getStringOr(key + "Fluid", "");
        int amount = input.getIntOr(key + "Amount", 0);
        Identifier id = raw.isEmpty() ? null : Identifier.tryParse(raw);
        Fluid fluid = id == null ? Fluids.EMPTY : BuiltInRegistries.FLUID.getValue(id);
        if (fluid == null || !accepts(fluid) || amount <= 0) {
            this.buffer.setRaw(Fluids.EMPTY, 0);
            return;
        }
        this.buffer.setRaw(fluid, amount);
    }
}

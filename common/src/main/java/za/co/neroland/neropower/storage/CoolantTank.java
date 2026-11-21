package za.co.neroland.neropower.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerolandcore.fluid.FluidBuffer;
import za.co.neroland.nerolandcore.fluid.NeroFluidStorage;

/**
 * The Storage Ring Controller's cryo coolant tank: Core's {@link FluidBuffer} that accepts only fluids in
 * {@code #nerotech:cryo_coolant} (NeroTech's tag; there is no {@code c:} cryo tag), flowing variants
 * normalised to their source. Saved as {@code CoolantFluid} / {@code CoolantAmount}.
 */
public final class CoolantTank implements NeroFluidStorage {

    /** NeroTech's cryo coolant fluid tag (decision 002, row 9). */
    public static final TagKey<Fluid> CRYO_COOLANT =
            TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("nerotech", "cryo_coolant"));

    private final FluidBuffer buffer;

    public CoolantTank(long capacity, Runnable onChanged) {
        this.buffer = new FluidBuffer(capacity, onChanged);
    }

    /** Whether a fluid is cryo coolant. */
    public static boolean isCoolant(Fluid fluid) {
        return fluid != null && fluid != Fluids.EMPTY && fluid.defaultFluidState().is(CRYO_COOLANT);
    }

    private static Fluid normalize(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    /** Follow a config change of the tank size (clamps what it holds). */
    public void resize(long capacity) {
        if (capacity != this.buffer.getCapacity()) {
            this.buffer.resize(capacity);
        }
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

    @Override
    public long fill(Fluid fluid, long amount, boolean simulate) {
        if (!isCoolant(fluid)) {
            return 0L;
        }
        return this.buffer.fill(normalize(fluid), amount, simulate);
    }

    @Override
    public long drain(long amount, boolean simulate) {
        return this.buffer.drain(amount, simulate);
    }

    public void save(ValueOutput output) {
        output.putString("CoolantFluid", BuiltInRegistries.FLUID.getKey(this.buffer.getRawFluid()).toString());
        output.putInt("CoolantAmount", this.buffer.getRawAmount());
    }

    public void load(ValueInput input) {
        Fluid fluid = BuiltInRegistries.FLUID.getValue(
                Identifier.parse(input.getStringOr("CoolantFluid", "minecraft:empty")));
        this.buffer.setRaw(fluid == null ? Fluids.EMPTY : normalize(fluid), input.getIntOr("CoolantAmount", 0));
    }
}

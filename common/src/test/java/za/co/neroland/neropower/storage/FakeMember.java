package za.co.neroland.neropower.storage;

import net.minecraft.core.BlockPos;

import za.co.neroland.nerolandcore.energy.EnergyBuffer;
import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/** A pool member on the plain JVM: a Core buffer, its rate and its tier. */
final class FakeMember implements PoolMember {

    final EnergyBuffer buffer;
    final int io;
    final boolean fast;
    boolean removed;

    FakeMember(int capacity, int io, boolean fast, int stored) {
        this.buffer = new EnergyBuffer(capacity, io, io, () -> { });
        this.buffer.setRaw(stored);
        this.io = io;
        this.fast = fast;
    }

    static FakeMember cell(int capacity, int io, int stored) {
        return new FakeMember(capacity, io, false, stored);
    }

    static FakeMember surge(int capacity, int io, int stored) {
        return new FakeMember(capacity, io, true, stored);
    }

    @Override
    public NeroEnergyStorage poolBuffer() {
        return this.buffer;
    }

    @Override
    public long poolIo() {
        return this.io;
    }

    @Override
    public boolean fastTier() {
        return this.fast;
    }

    @Override
    public BlockPos getBlockPos() {
        return null;
    }

    @Override
    public boolean isRemoved() {
        return this.removed;
    }
}

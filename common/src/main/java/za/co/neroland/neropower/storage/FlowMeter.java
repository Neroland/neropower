package za.co.neroland.neropower.storage;

import za.co.neroland.nerolandcore.energy.NeroEnergyStorage;

/**
 * A counting view over a store: every real insert and extract through it is tallied, so a screen can
 * show "In" and "Out" for the last tick rather than only the net change. The store's own rules (limits,
 * capacity) are untouched; the view adds and removes nothing. Call {@link #roll()} once per tick.
 */
public final class FlowMeter implements NeroEnergyStorage {

    private final NeroEnergyStorage inner;
    private long in;
    private long out;
    private long lastIn;
    private long lastOut;

    public FlowMeter(NeroEnergyStorage inner) {
        this.inner = inner;
    }

    /** Close the tick: the tallies become {@link #lastIn()} / {@link #lastOut()} and restart at 0. */
    public void roll() {
        this.lastIn = this.in;
        this.lastOut = this.out;
        this.in = 0L;
        this.out = 0L;
    }

    /** NE inserted over the last closed tick. */
    public long lastIn() {
        return this.lastIn;
    }

    /** NE extracted over the last closed tick. */
    public long lastOut() {
        return this.lastOut;
    }

    @Override
    public long getAmount() {
        return this.inner.getAmount();
    }

    @Override
    public long getCapacity() {
        return this.inner.getCapacity();
    }

    @Override
    public long insert(long maxAmount, boolean simulate) {
        long moved = this.inner.insert(maxAmount, simulate);
        if (!simulate && moved > 0) {
            this.in += moved;
        }
        return moved;
    }

    @Override
    public long extract(long maxAmount, boolean simulate) {
        long moved = this.inner.extract(maxAmount, simulate);
        if (!simulate && moved > 0) {
            this.out += moved;
        }
        return moved;
    }
}

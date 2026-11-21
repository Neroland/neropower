package za.co.neroland.neropower.menu;

import java.util.function.ToLongFunction;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerData;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerotech.machine.NeroTechMachineBlockEntity;

/**
 * The gauge tail every NeroPower machine menu appends after NeroTech's shared data and NeroTech's own
 * 14-int gauge tail. NeroTech's tail already carries stored energy, capacity, heat, heat capacity and
 * the safe band, so this one carries only what NeroTech's cannot: one machine-specific amount (a bank's
 * I/O limit, a beam's last pass) and, on storage screens, the stored energy and capacity as <b>wide</b>
 * values, because NeroTech's 15-bit halves stop at 2^30 - 1 (about 1.07 billion NE) and a large store
 * goes past that.
 *
 * <p>ContainerData travels as shorts, so every value is split into three 15-bit thirds ({@link #hi},
 * {@link #mid}, {@link #lo}; 45 bits, about 3.5e13). Server side the values are read live from the
 * machine on every broadcast; client side they are whatever the server last sent. Index the tail only
 * through this object, never by absolute data-slot position. Machine-scoped numbers only, never player
 * data.
 */
public final class GaugeTail implements ContainerData {

    /** Wide index: the machine-specific amount. */
    public static final int EXTRA = 0;
    /** Wide index: stored energy (NE), storage screens only (0 elsewhere). */
    public static final int WIDE_ENERGY = 3;
    /** Wide index: energy capacity (NE), storage screens only (0 elsewhere). */
    public static final int WIDE_CAPACITY = 6;
    /** Ints in the tail. */
    public static final int COUNT = 9;

    /** Largest value three 15-bit thirds hold. */
    public static final long MAX = (1L << 45) - 1L;

    /** Largest value of the old two-halves pair (kept for {@link #hi2}/{@link #lo2}/{@link #join}). */
    public static final long MAX_PAIR = (1L << 30) - 1L;

    @Nullable
    private final NeroTechMachineBlockEntity machine;
    private final ToLongFunction<NeroTechMachineBlockEntity> extra;
    private final boolean wideEnergy;
    private final int[] synced = new int[COUNT];

    private GaugeTail(@Nullable NeroTechMachineBlockEntity machine, ToLongFunction<NeroTechMachineBlockEntity> extra,
            boolean wideEnergy) {
        this.machine = machine;
        this.extra = extra;
        this.wideEnergy = wideEnergy;
    }

    /** The tail for a menu over {@code container} (live on the server, a sync target on the client). */
    public static GaugeTail of(Container container) {
        return of(container, be -> 0L);
    }

    /** As {@link #of(Container)}, with {@code extra} read from the machine for the {@link #EXTRA} value. */
    public static GaugeTail of(Container container, ToLongFunction<NeroTechMachineBlockEntity> extra) {
        return new GaugeTail(container instanceof NeroTechMachineBlockEntity be ? be : null, extra, false);
    }

    /** A storage screen's tail: as {@link #of(Container, ToLongFunction)}, plus the wide energy values. */
    public static GaugeTail storage(Container container, ToLongFunction<NeroTechMachineBlockEntity> extra) {
        return new GaugeTail(container instanceof NeroTechMachineBlockEntity be ? be : null, extra, true);
    }

    private long live(int wide) {
        NeroTechMachineBlockEntity be = this.machine;
        if (be == null) {
            return 0L;
        }
        return switch (wide) {
            case EXTRA -> this.extra.applyAsLong(be);
            case WIDE_ENERGY -> this.wideEnergy ? be.getEnergy().getAmount() : 0L;
            case WIDE_CAPACITY -> this.wideEnergy ? be.getEnergy().getCapacity() : 0L;
            default -> 0L;
        };
    }

    @Override
    public int get(int index) {
        if (index < 0 || index >= COUNT) {
            return 0;
        }
        if (this.machine == null) {
            return this.synced[index];
        }
        int wide = index - index % 3;
        long value = live(wide);
        return switch (index - wide) {
            case 0 -> hi(value);
            case 1 -> mid(value);
            default -> lo(value);
        };
    }

    @Override
    public void set(int index, int value) {
        if (index >= 0 && index < COUNT) {
            this.synced[index] = value;
        }
    }

    @Override
    public int getCount() {
        return COUNT;
    }

    /** The wide value starting at {@code wide} ({@link #EXTRA}, {@link #WIDE_ENERGY}, {@link #WIDE_CAPACITY}). */
    public long value(int wide) {
        return join3(get(wide), get(wide + 1), get(wide + 2));
    }

    private static long clamp(long value) {
        return Math.max(0L, Math.min(MAX, value));
    }

    /** Upper 15 bits of a value clamped to 0..{@link #MAX}. */
    public static int hi(long value) {
        return (int) (clamp(value) >> 30);
    }

    /** Middle 15 bits of a value clamped to 0..{@link #MAX}. */
    public static int mid(long value) {
        return (int) ((clamp(value) >> 15) & 0x7FFF);
    }

    /** Lower 15 bits of a value clamped to 0..{@link #MAX}. */
    public static int lo(long value) {
        return (int) (clamp(value) & 0x7FFF);
    }

    /** Re-join three thirds. */
    public static long join3(int hi, int mid, int lo) {
        return ((long) (Math.max(0, hi) & 0x7FFF) << 30) | ((long) (mid & 0x7FFF) << 15) | (lo & 0x7FFF);
    }

    /** Upper half of the two-int pair encoding (values clamped to 0..{@link #MAX_PAIR}). */
    public static int hi2(long value) {
        return (int) (Math.max(0L, Math.min(MAX_PAIR, value)) >> 15);
    }

    /** Lower half of the two-int pair encoding. */
    public static int lo2(long value) {
        return (int) (Math.max(0L, Math.min(MAX_PAIR, value)) & 0x7FFF);
    }

    /** Re-join two halves of the pair encoding. */
    public static long join(int hi, int lo) {
        return ((long) Math.max(0, hi) << 15) | (lo & 0x7FFF);
    }
}

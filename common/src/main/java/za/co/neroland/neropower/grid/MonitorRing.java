package za.co.neroland.neropower.grid;

/**
 * The Power Monitor's 60-sample history: one sample every 20 ticks of NE/t in, NE/t
 * out and stored permille. In memory only, never saved. Synced compactly as one int array of
 * {@code 1 + 3 × size} entries (newest last), clamped to int; machine numbers only, nothing personal.
 */
public final class MonitorRing {

    public static final int SIZE = 60;

    private final long[] in = new long[SIZE];
    private final long[] out = new long[SIZE];
    private final int[] stored = new int[SIZE];
    private int head;
    private int count;

    public MonitorRing() {
    }

    /** Append a sample, dropping the oldest once full. */
    public void push(long inPerTick, long outPerTick, int storedPermille) {
        this.in[this.head] = Math.max(0L, inPerTick);
        this.out[this.head] = Math.max(0L, outPerTick);
        this.stored[this.head] = Math.max(0, Math.min(1000, storedPermille));
        this.head = (this.head + 1) % SIZE;
        this.count = Math.min(SIZE, this.count + 1);
    }

    public int size() {
        return this.count;
    }

    private int slot(int age) {
        return Math.floorMod(this.head - 1 - age, SIZE);
    }

    /** Sample {@code i} counted from the oldest (0) to the newest ({@code size()-1}). */
    public long in(int i) {
        return this.in[slot(this.count - 1 - i)];
    }

    public long out(int i) {
        return this.out[slot(this.count - 1 - i)];
    }

    public int storedPermille(int i) {
        return this.stored[slot(this.count - 1 - i)];
    }

    public long averageIn() {
        long sum = 0L;
        for (int i = 0; i < this.count; i++) {
            sum += in(i);
        }
        return this.count == 0 ? 0L : sum / this.count;
    }

    public long averageOut() {
        long sum = 0L;
        for (int i = 0; i < this.count; i++) {
            sum += out(i);
        }
        return this.count == 0 ? 0L : sum / this.count;
    }

    /** The compact sync form: {@code [count, in0, out0, stored0, ...]} oldest first. */
    public int[] toArray() {
        int[] data = new int[1 + 3 * this.count];
        data[0] = this.count;
        for (int i = 0; i < this.count; i++) {
            data[1 + 3 * i] = (int) Math.min(Integer.MAX_VALUE, in(i));
            data[2 + 3 * i] = (int) Math.min(Integer.MAX_VALUE, out(i));
            data[3 + 3 * i] = storedPermille(i);
        }
        return data;
    }

    /** Replace the contents with a {@link #toArray} form; malformed input reads as empty. */
    public void fromArray(int[] data) {
        this.head = 0;
        this.count = 0;
        if (data == null || data.length == 0) {
            return;
        }
        int n = Math.max(0, Math.min(SIZE, data[0]));
        if (data.length < 1 + 3 * n) {
            return;
        }
        for (int i = 0; i < n; i++) {
            push(data[1 + 3 * i], data[2 + 3 * i], data[3 + 3 * i]);
        }
    }

    /** The sync form as text (base-36 ints joined by commas): the same on every MC line's tag API. */
    public static String encode(int[] data) {
        StringBuilder out = new StringBuilder(data.length * 4);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(Integer.toString(data[i], 36));
        }
        return out.toString();
    }

    /** Parse {@link #encode}; malformed text reads as empty. */
    public static int[] decode(String text) {
        if (text == null || text.isEmpty()) {
            return new int[0];
        }
        String[] parts = text.split(",");
        if (parts.length > 1 + 3 * SIZE) {
            return new int[0];
        }
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i], 36);
            }
        } catch (NumberFormatException e) {
            return new int[0];
        }
        return out;
    }

    /** In / out NE per tick from a stored change over {@code ticks}: {@code [in, out]}. */
    public static long[] flowFromDelta(long before, long after, int ticks) {
        int t = Math.max(1, ticks);
        long delta = after - before;
        return delta >= 0 ? new long[] {delta / t, 0L} : new long[] {0L, -delta / t};
    }

    /** Comparator: fill × 15, rounded down; 0 with no source. */
    public static int comparator(long stored, long capacity) {
        if (capacity <= 0L || stored <= 0L) {
            return 0;
        }
        return (int) Math.max(0L, Math.min(15L, stored * 15L / capacity));
    }
}

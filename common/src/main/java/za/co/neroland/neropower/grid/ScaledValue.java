package za.co.neroland.neropower.grid;

/**
 * Carries a large setting (a breaker limit, a load-bank rate) through decision 012's button channel, whose
 * value field holds only 0..999: {@code value = mantissa × 10^exponent}, mantissa 0..999, exponent
 * 0..{@value #MAX_EXP}. The action id encodes the exponent ({@code baseAction + exponent}), so one click is
 * one packet. Exact to three significant digits, which is what a typed box or a 1-2-5 slider needs.
 */
public final class ScaledValue {

    public static final int MAX_EXP = 5;
    public static final int MAX_MANTISSA = 999;

    private ScaledValue() {
    }

    /** {@code [exponent, mantissa]} for {@code value} (clamped to 0..999×10^5), rounded to 3 digits. */
    public static int[] encode(long value) {
        long v = Math.max(0L, value);
        int exp = 0;
        while (v > MAX_MANTISSA && exp < MAX_EXP) {
            v = (v + 5) / 10;
            exp++;
        }
        return new int[] {exp, (int) Math.min(MAX_MANTISSA, v)};
    }

    /** The value an {@code [exponent, mantissa]} pair stands for. */
    public static long decode(int exponent, int mantissa) {
        int exp = Math.max(0, Math.min(MAX_EXP, exponent));
        long v = Math.max(0, Math.min(MAX_MANTISSA, mantissa));
        for (int i = 0; i < exp; i++) {
            v *= 10L;
        }
        return v;
    }
}

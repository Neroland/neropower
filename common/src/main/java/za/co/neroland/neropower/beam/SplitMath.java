package za.co.neroland.neropower.beam;

import java.util.Locale;

/**
 * Beam Splitter arithmetic, Minecraft-free so every rule is unit-tested: the lens
 * loss on what arrives, each link's share of what is left, and the cap that keeps the shares at or under
 * 1000 permille. Splitting never creates energy: the lens loss is rounded <b>up</b>, every allocation is
 * rounded <b>down</b>, and the shares can never add up to more than the whole.
 */
public final class SplitMath {

    public static final int PERMILLE = 1_000;

    /** The four NeroTech focus lenses (ids {@code nerotech:<key>_focus_lens}) plus "no lens". Wire order: append only. */
    public enum Lens {
        NONE(""), QUARTZ("quartz"), GLACITE("glacite"), EMBER("ember"), VOID("void");

        public static final Lens[] VALUES = values();

        private final String key;

        Lens(String key) {
            this.key = key;
        }

        /** The config / item key ({@code quartz}); empty for {@link #NONE}. */
        public String key() {
            return this.key;
        }

        /** The lens for an item id path ({@code quartz_focus_lens}), or {@link #NONE}. */
        public static Lens byItemPath(String path) {
            for (Lens lens : VALUES) {
                if (lens != NONE && (lens.key + "_focus_lens").equals(path)) {
                    return lens;
                }
            }
            return NONE;
        }

        public static Lens byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
        }
    }

    /** Default {@code beamSplitterLensLoss}. */
    public static final String DEFAULT_LENS_LOSS = "quartz=50,glacite=30,ember=20,void=10";

    private SplitMath() {
    }

    /**
     * The loss (permille) of each lens, parsed from {@code beamSplitterLensLoss} ({@code key=permille,...});
     * index = {@link Lens#ordinal()}. A missing or bad entry falls back to the default; values clamp to
     * 0..1000. {@link Lens#NONE} reads 1000 (nothing passes without a lens).
     */
    public static int[] parseLensLoss(String spec) {
        int[] out = {PERMILLE, 50, 30, 20, 10};
        if (spec == null) {
            return out;
        }
        for (String part : spec.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = part.substring(eq + 1).trim();
            for (Lens lens : Lens.VALUES) {
                if (lens != Lens.NONE && lens.key.equals(key)) {
                    try {
                        out[lens.ordinal()] = clamp(Integer.parseInt(value), 0, PERMILLE);
                    } catch (NumberFormatException ignored) {
                        // keep the default for this lens
                    }
                }
            }
        }
        return out;
    }

    /** What the lens takes from {@code arrived} NE (rounded up, never more than arrived). */
    public static long lensLoss(long arrived, int lossPermille) {
        if (arrived <= 0L) {
            return 0L;
        }
        int loss = clamp(lossPermille, 0, PERMILLE);
        long taken = (arrived * loss + PERMILLE - 1) / PERMILLE;
        return Math.min(arrived, taken);
    }

    /** The most a link may be given when the other links already hold {@code othersSum} permille. */
    public static int capShare(int requested, int othersSum) {
        int free = Math.max(0, PERMILLE - Math.max(0, othersSum));
        return clamp(requested, 0, free);
    }

    /**
     * Each share's allocation of {@code available} NE, floored. Shares are first capped cumulatively in
     * list order so their sum never passes 1000 (a tampered save cannot over-allocate), so
     * {@code sum(result) <= available} always.
     */
    public static long[] allocate(long available, int[] shares) {
        long[] out = new long[shares.length];
        if (available <= 0L) {
            return out;
        }
        int used = 0;
        for (int i = 0; i < shares.length; i++) {
            int share = capShare(shares[i], used);
            used += share;
            out[i] = available * share / PERMILLE;
        }
        return out;
    }

    /** Sum of shares after the cumulative cap (what is actually allocated). */
    public static int allocatedPermille(int[] shares) {
        int used = 0;
        for (int share : shares) {
            used += capShare(share, used);
        }
        return used;
    }

    /** The contract's render bucket for one share: permille / 100, 0..10. */
    public static int shareBucket(int permille) {
        return clamp(permille, 0, PERMILLE) / 100;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

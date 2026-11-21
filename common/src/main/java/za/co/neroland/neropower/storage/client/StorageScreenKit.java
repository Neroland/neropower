package za.co.neroland.neropower.storage.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.neropower.failure.FailureStage;

/**
 * Code-drawn pieces of the screen gauge kit the storage screens share: the ring gauge, pips, a plain
 * vertical bar, the stage block and the stall line text. Draw-only: every tooltip is answered by the
 * screen's {@code readoutTooltip} (or the inherited energy tooltip), so each gauge keeps one provider.
 * Panel-relative rectangles are offset by the caller ({@code leftPos}, {@code topPos}).
 */
public final class StorageScreenKit {

    public static final int EDGE = 0xFF05080D;
    public static final int TROUGH = 0xFF0B1119;
    public static final int DIVIDER = 0xFF2A3542;
    public static final int VALUE = 0xFFD6ECFF;
    public static final int SUBTLE = 0xFF8DA0B4;
    public static final int ENERGY = 0xFFE0B33A;
    public static final int WARMTH = 0xFFE0543A;
    public static final int VIOLET = 0xFF9B7BFF;
    public static final int VIOLET_DIM = 0xFF5B3FD6;
    public static final int CRYO = 0xFF7FD8F0;
    public static final int CAN = 0xFFFFA028;
    public static final int STABLE = 0xFF3CB043;
    public static final int WARNING = 0xFFE0B33A;
    public static final int UNSTABLE = 0xFFFF5438;
    public static final int FAILURE = 0xFFFF3824;

    /** Ring gauge segments. */
    public static final int RING_SEGMENTS = 24;

    private StorageScreenKit() {
    }

    /**
     * The 46 x 46 ring gauge at screen {@code (x, y)}: 24 segments clockwise from 12 o'clock, lit up to
     * {@code permille}, the percentage centred.
     */
    public static void ring(GuiGraphicsExtractor extractor, Font font, int x, int y, int permille, int lit) {
        int cx = x + 23;
        int cy = y + 23;
        int litCount = Math.max(0, Math.min(RING_SEGMENTS, Math.round(permille * RING_SEGMENTS / 1000.0F)));
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double angle = Math.toRadians(i * (360.0D / RING_SEGMENTS) + 7.5D - 90.0D);
            int sx = cx + (int) Math.round(Math.cos(angle) * 19.0D) - 2;
            int sy = cy + (int) Math.round(Math.sin(angle) * 19.0D) - 2;
            extractor.fill(sx, sy, sx + 4, sy + 4, i < litCount ? lit : TROUGH);
        }
        String pct = (permille / 10) + "%";
        extractor.text(font, Component.literal(pct), cx - font.width(pct) / 2, cy - 4, VALUE, false);
    }

    /** A vertical bar at screen {@code (x, y)}, {@code w x h}, filled upward to {@code permille}. */
    public static void bar(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int permille, int fill) {
        extractor.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        extractor.fill(x, y, x + w, y + h, TROUGH);
        int fh = Math.round(h * Math.max(0, Math.min(1000, permille)) / 1000.0F);
        if (fh > 0) {
            extractor.fill(x, y + h - fh, x + w, y + h, fill);
        }
        extractor.fill(x, y, x + w, y + 2, fill);
    }

    /** {@code count} pips of 6 x 10 with 2 px gaps at screen {@code (x, y)}; the first {@code lit} filled. */
    public static void pips(GuiGraphicsExtractor extractor, int x, int y, int count, int lit, int color) {
        for (int i = 0; i < count; i++) {
            int px = x + i * 8;
            if (i < lit) {
                extractor.fill(px, y, px + 6, y + 10, color);
            } else {
                extractor.fill(px, y, px + 6, y + 10, DIVIDER);
                extractor.fill(px + 1, y + 1, px + 5, y + 9, TROUGH);
            }
        }
    }

    /** The 96 x 17 stage block at screen {@code (x, y)}: "Stage: word" and a four-cell strip. */
    public static void stage(GuiGraphicsExtractor extractor, Font font, int x, int y, FailureStage stage) {
        int color = stageColor(stage);
        extractor.text(font, Component.translatable("gui.neropower.gauge.stage.line",
                Component.translatable("neropower.failure.stage." + stage.name().toLowerCase(java.util.Locale.ROOT))),
                x, y, color, false);
        FailureStage[] all = FailureStage.values();
        for (int i = 0; i < all.length && i < 4; i++) {
            int cx = x + i * 24;
            boolean on = all[i].code() <= stage.code();
            if (all[i] == stage) {
                extractor.fill(cx - 1, y + 9, cx + 24, y + 18, VALUE);
            }
            extractor.fill(cx, y + 10, cx + 23, y + 17, on ? stageColor(all[i]) : TROUGH);
        }
    }

    public static int stageColor(FailureStage stage) {
        return switch (stage) {
            case STABLE -> STABLE;
            case WARNING -> WARNING;
            case UNSTABLE -> UNSTABLE;
            case FAILURE -> FAILURE;
        };
    }

    /** The {@code gui.neropower.stall.<key>} key for a stallCause code (gauge kit table). */
    public static String stallKey(int cause) {
        return switch (cause) {
            case 1 -> "not_formed";
            case 2 -> "no_fuel";
            case 3 -> "no_steam";
            case 4 -> "no_water";
            case 5 -> "exhaust_blocked";
            case 6 -> "no_heat_sink";
            case 7 -> "output_full";
            case 8 -> "buffer_full";
            case 9 -> "no_power";
            case 10 -> "no_lens";
            case 11 -> "no_link";
            case 12 -> "beam_blocked";
            case 13 -> "no_coolant";
            case 14 -> "nothing_to_do";
            case 15 -> "locked_out";
            default -> "none";
        };
    }

    /**
     * The stall line for a synced readout: the screen's own idle and running text for rest and work, the
     * kit's cause text for a stall, and the kit's words for hot, trip and fail.
     */
    public static Component stallLine(int readout, int cause, Component idle, Component running) {
        return switch (readout) {
            case 0 -> idle;
            case 1 -> running;
            case 2 -> Component.translatable("gui.neropower.stall." + stallKey(cause));
            case 3 -> Component.translatable("gui.neropower.stall.hot");
            case 4 -> Component.translatable("gui.neropower.stall.tripped");
            default -> Component.translatable("gui.neropower.stall.failing");
        };
    }
}

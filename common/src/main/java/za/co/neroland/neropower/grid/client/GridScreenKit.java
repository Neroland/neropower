package za.co.neroland.neropower.grid.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Code-drawn controls of the screen gauge kit the beam and grid screens share:
 * word buttons, checkboxes, sliders, horizontal bars and list rows, plus the hit test. Draw-only and
 * screen-relative (callers add {@code leftPos}/{@code topPos}); tooltips stay with each screen's
 * {@code readoutTooltip}, so every element keeps one provider.
 */
public final class GridScreenKit {

    public static final int EDGE = 0xFF05080D;
    public static final int TROUGH = 0xFF0B1119;
    public static final int DIVIDER = 0xFF2A3542;
    public static final int VALUE = 0xFFD6ECFF;
    public static final int SUBTLE = 0xFF8DA0B4;
    public static final int ENERGY = 0xFFE0B33A;
    public static final int AMBER = 0xFFE0B33A;
    public static final int HOT = 0xFFFF5438;
    public static final int FAILURE = 0xFFFF3824;
    public static final int FLAG_TEXT = 0xFFF2F4F6;
    public static final int STABLE = 0xFF3CB043;
    public static final int SHARE = 0xFFFFA028;
    public static final int OUT = 0xFF55C2F0;
    public static final int VIOLET = 0xFF9B7BFF;
    public static final int BUTTON = 0xFF1B232E;
    public static final int BUTTON_HOVER = 0xFF2A3542;
    public static final int SELECTED = 0x332A3542;

    private GridScreenKit() {
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A word button; {@code mx/my} panel-relative, drawn at screen {@code (sx, sy)} = panel + offset. */
    public static void button(GuiGraphicsExtractor g, Font font, int ox, int oy, int x, int y, int w, int h,
            Component label, boolean enabled, boolean hovered) {
        g.fill(ox + x - 1, oy + y - 1, ox + x + w + 1, oy + y + h + 1, EDGE);
        g.fill(ox + x, oy + y, ox + x + w, oy + y + h, enabled && hovered ? BUTTON_HOVER : BUTTON);
        int tw = font.width(label);
        g.text(font, label, ox + x + (w - tw) / 2, oy + y + (h - 8) / 2, enabled ? VALUE : SUBTLE, false);
    }

    public static void checkbox(GuiGraphicsExtractor g, Font font, int ox, int oy, int x, int y, boolean on,
            Component label) {
        g.fill(ox + x, oy + y, ox + x + 10, oy + y + 10, SUBTLE);
        g.fill(ox + x + 1, oy + y + 1, ox + x + 9, oy + y + 9, TROUGH);
        if (on) {
            g.fill(ox + x + 2, oy + y + 2, ox + x + 8, oy + y + 8, VALUE);
        }
        g.text(font, label, ox + x + 12, oy + y + 1, VALUE, false);
    }

    /** A horizontal bar filled to {@code permille} (0..1000) in {@code fill}. */
    public static void hbar(GuiGraphicsExtractor g, int ox, int oy, int x, int y, int w, int h, int permille, int fill) {
        g.fill(ox + x - 1, oy + y - 1, ox + x + w + 1, oy + y + h + 1, EDGE);
        g.fill(ox + x, oy + y, ox + x + w, oy + y + h, TROUGH);
        int fw = Math.round(w * Math.max(0, Math.min(1000, permille)) / 1000.0F);
        if (fw > 0) {
            g.fill(ox + x, oy + y, ox + x + fw, oy + y + h, fill);
        }
    }

    /** A slider track with its thumb at {@code permille}. */
    public static void slider(GuiGraphicsExtractor g, int ox, int oy, int x, int y, int w, int permille, int fill) {
        hbar(g, ox, oy, x, y, w, 6, permille, fill);
        int tx = x + Math.round((w - 4) * Math.max(0, Math.min(1000, permille)) / 1000.0F);
        g.fill(ox + tx, oy + y - 2, ox + tx + 4, oy + y + 8, VALUE);
    }

    /** The fraction 0..1000 at a panel-relative x over a track. */
    public static int permilleAt(int mx, int x, int w) {
        return Math.round(Math.max(0F, Math.min(1F, (mx - x) / (float) Math.max(1, w))) * 1000F);
    }

    /** A list row's selection tint and 2 px marker. */
    public static void selectedRow(GuiGraphicsExtractor g, int ox, int oy, int x, int y, int w, int h) {
        g.fill(ox + x, oy + y, ox + x + w, oy + y + h, SELECTED);
        g.fill(ox + x, oy + y, ox + x + 2, oy + y + h, VALUE);
    }

    /** Log-scale position (0..1000) of {@code value} in {@code [1, max]}. */
    public static int logPermille(long value, long max) {
        if (value <= 1L || max <= 1L) {
            return 0;
        }
        return (int) Math.round(Math.min(1.0, Math.log(value) / Math.log(max)) * 1000.0);
    }

    /** The notch nearest a log-scale position. */
    public static int nearestNotch(int[] notches, int permille, long max) {
        double target = Math.exp(Math.max(0, Math.min(1000, permille)) / 1000.0 * Math.log(max));
        int best = notches[0];
        double bestDist = Double.MAX_VALUE;
        for (int notch : notches) {
            double d = Math.abs(Math.log(notch) - Math.log(Math.max(1.0, target)));
            if (d < bestDist) {
                bestDist = d;
                best = notch;
            }
        }
        return best;
    }

    /** The notch one step above / below {@code value}. */
    public static int stepNotch(int[] notches, long value, int direction) {
        int index = 0;
        for (int i = 0; i < notches.length; i++) {
            if (notches[i] <= value) {
                index = i;
            }
        }
        if (direction > 0 && notches[index] < value) {
            return notches[Math.min(notches.length - 1, index + 1)];
        }
        return notches[Math.max(0, Math.min(notches.length - 1, index + Integer.signum(direction)))];
    }
}

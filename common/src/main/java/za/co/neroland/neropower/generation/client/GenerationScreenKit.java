package za.co.neroland.neropower.generation.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.generation.GenerationReadout;

/**
 * Drawing and tooltip pieces the two small-generator screens share (the gauge kit's tank bar, chip,
 * slot ring, stall line and tank tooltip). Code-drawn only; no textures, no player data.
 */
final class GenerationScreenKit {

    static final int EDGE = 0xFF05080D;
    static final int TROUGH = 0xFF0B1119;
    static final int DIVIDER = 0xFF2A3542;
    static final int VALUE = 0xFFD6ECFF;
    static final int SUBTLE = 0xFF8DA0B4;
    static final int ENERGY = 0xFFE0B33A;
    static final int HYDROGEN = 0xFFCEFFFF;
    static final int OXYGEN = 0xFF4DD0E1;
    static final int WATER = 0xFF3F76E4;
    static final int ROW_H = 9;

    private static final String[] STALL_KEYS = {"none", "not_formed", "no_fuel", "no_steam", "no_water",
        "exhaust_blocked", "no_heat_sink", "output_full", "buffer_full", "no_power", "no_lens", "no_link",
        "beam_blocked", "no_coolant", "nothing_to_do", "locked_out"};

    private GenerationScreenKit() {
    }

    static String stallKey(int cause) {
        return STALL_KEYS[Math.max(0, Math.min(STALL_KEYS.length - 1, cause))];
    }

    /** A tank bar: the gauge recipe plus the glass column and quarter ticks. */
    static void tank(GuiGraphicsExtractor g, int x, int y, int w, int h, long amount, long capacity, int colour) {
        float frac = capacity <= 0L ? 0F : Math.min(1F, (float) amount / capacity);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, TROUGH);
        int fh = Math.round(h * frac);
        if (fh > 0) {
            g.fill(x, y + h - fh, x + w, y + h, colour);
        }
        g.fill(x, y, x + w, y + 2, colour);
        g.fill(x + 2, y + 2, x + 4, y + h, 0x33FFFFFF);
        for (int q = 1; q <= 3; q++) {
            int ty = y + h - h * q / 4;
            g.fill(x + w - 3, ty, x + w, ty + 1, DIVIDER);
        }
    }

    /** The tank tooltip (gauge kit 5.3); gas tanks add the units line. */
    static void tankTooltip(List<Component> lines, Component role, Component medium, long amount, long capacity,
            double perSecond, int unitMb) {
        lines.add(role);
        lines.add(amount <= 0L ? GaugeTooltips.tr("tank.empty") : medium);
        lines.add(GaugeTooltips.tr("tank.amount", GaugeMath.grouped(amount), GaugeMath.grouped(capacity)));
        if (unitMb > 0) {
            lines.add(GaugeTooltips.tr("tank.units", GaugeMath.unitCount(GaugeMath.units(amount, unitMb))));
        }
        if (perSecond > 0.5D) {
            lines.add(GaugeTooltips.tr("tank.filling", GaugeMath.grouped(Math.round(perSecond))));
        } else if (perSecond < -0.5D) {
            lines.add(GaugeTooltips.tr("tank.draining", GaugeMath.grouped(Math.round(-perSecond))));
        } else {
            lines.add(GaugeTooltips.tr("tank.steady"));
        }
    }

    /** A chip (gauge kit 4): outline and text in the state colour on the trough. */
    static void chip(GuiGraphicsExtractor g, Font font, int x, int y, int maxW, Component text, int colour) {
        Component shown = fit(font, text, maxW - 6);
        int w = Math.min(maxW, font.width(shown) + 6);
        g.fill(x, y, x + w, y + 11, colour);
        g.fill(x + 1, y + 1, x + w - 1, y + 10, TROUGH);
        g.text(font, shown, x + 3, y + 2, colour, false);
    }

    /**
     * A slot ring (gauge kit 4): a 2 px square track 3 px outside a 16 × 16 slot at (sx, sy), lit clockwise
     * from the top centre for {@code frac}; {@code hollow} draws a 1 px outline only (a spent capsule).
     */
    static void slotRing(GuiGraphicsExtractor g, int sx, int sy, float frac, int colour, boolean hollow) {
        int x0 = sx - 3;
        int y0 = sy - 3;
        int size = 22;
        if (hollow) {
            g.fill(x0, y0, x0 + size, y0 + 1, colour);
            g.fill(x0, y0 + size - 1, x0 + size, y0 + size, colour);
            g.fill(x0, y0, x0 + 1, y0 + size, colour);
            g.fill(x0 + size - 1, y0, x0 + size, y0 + size, colour);
            return;
        }
        // Track.
        g.fill(x0, y0, x0 + size, y0 + 2, TROUGH);
        g.fill(x0, y0 + size - 2, x0 + size, y0 + size, TROUGH);
        g.fill(x0, y0, x0 + 2, y0 + size, TROUGH);
        g.fill(x0 + size - 2, y0, x0 + size, y0 + size, TROUGH);
        // Perimeter walked clockwise from the top centre, one 2 x 2 cell per step.
        int cells = (size / 2) * 4 - 4;
        int lit = Math.round(Math.max(0F, Math.min(1F, frac)) * cells);
        int half = size / 4;
        for (int i = 0; i < lit; i++) {
            int[] c = ringCell((i + half) % cells, size / 2);
            g.fill(x0 + c[0] * 2, y0 + c[1] * 2, x0 + c[0] * 2 + 2, y0 + c[1] * 2 + 2, colour);
        }
    }

    /** Cell {@code i} of a clockwise walk round an {@code n} x {@code n} grid's edge from its top-left. */
    private static int[] ringCell(int i, int n) {
        int side = n - 1;
        if (i < side) {
            return new int[] {i, 0};
        }
        if (i < 2 * side) {
            return new int[] {side, i - side};
        }
        if (i < 3 * side) {
            return new int[] {side - (i - 2 * side), side};
        }
        return new int[] {0, side - (i - 3 * side)};
    }

    /** Text cut to a width with an ellipsis. */
    static Component fit(Font font, Component text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String raw = text.getString();
        while (!raw.isEmpty() && font.width(raw + "…") > width) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return Component.literal(raw + "…");
    }

    /** Stall-line hover (gauge kit 5.2): "Status", the full line, the fix when there is one. */
    static void stallTooltip(List<Component> lines, Component text, int readout, int cause, String fixKey) {
        lines.add(GaugeTooltips.tr("status"));
        lines.add(text);
        if (readout == GenerationReadout.STALL && fixKey != null) {
            lines.add(Component.translatable("gui.neropower.stall.fix." + fixKey));
        }
    }
}

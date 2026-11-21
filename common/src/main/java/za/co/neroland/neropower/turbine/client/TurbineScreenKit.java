package za.co.neroland.neropower.turbine.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;

/**
 * Small drawing and tooltip pieces the steam screens share (the gauge kit's tank bar, button, stall
 * line and tank tooltip). Code-drawn only; no textures, no player data.
 */
final class TurbineScreenKit {

    static final int EDGE = 0xFF05080D;
    static final int TROUGH = 0xFF0B1119;
    static final int DIVIDER = 0xFF2A3542;
    static final int VALUE = 0xFFD6ECFF;
    static final int SUBTLE = 0xFF8DA0B4;
    static final int ENERGY = 0xFFE0B33A;
    static final int STEAM = 0xFFE8ECEF;
    static final int EXHAUST = 0xFFB9C2C8;
    static final int WATER = 0xFF3F76E4;
    static final int CRYO = 0xFF7FD8F0;
    static final int STABLE = 0xFF3CB043;
    static final int WARNING = 0xFFE0B33A;
    static final int UNSTABLE = 0xFFFF5438;
    static final int FAILURE = 0xFFFF3824;
    static final int FLAG_TEXT = 0xFFF2F4F6;
    static final int BUTTON = 0xFF1B232E;
    static final int BUTTON_HOVER = 0xFF2A3542;

    /** The shared stall table (gauge kit 5.2), by {@code stallCause}. */
    private static final String[] STALL_KEYS = {"none", "not_formed", "no_fuel", "no_steam", "no_water",
        "exhaust_blocked", "no_heat_sink", "output_full", "buffer_full", "no_power", "no_lens", "no_link",
        "beam_blocked", "no_coolant", "nothing_to_do", "locked_out"};

    private TurbineScreenKit() {
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

    /** The tank tooltip (gauge kit 5.3). */
    static void tankTooltip(List<Component> lines, Component role, Component medium, long amount, long capacity,
            double perSecond) {
        lines.add(role);
        lines.add(amount <= 0L ? GaugeTooltips.tr("tank.empty") : medium);
        lines.add(GaugeTooltips.tr("tank.amount", GaugeMath.grouped(amount), GaugeMath.grouped(capacity)));
        if (perSecond > 0.5D) {
            lines.add(GaugeTooltips.tr("tank.filling", GaugeMath.grouped(Math.round(perSecond))));
        } else if (perSecond < -0.5D) {
            lines.add(GaugeTooltips.tr("tank.draining", GaugeMath.grouped(Math.round(-perSecond))));
        } else {
            lines.add(GaugeTooltips.tr("tank.steady"));
        }
    }

    /** A word button (gauge kit 6). */
    static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component text,
            boolean enabled, boolean hovered) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE);
        g.fill(x, y, x + w, y + h, enabled && hovered ? BUTTON_HOVER : BUTTON);
        int tw = font.width(text);
        g.text(font, text, x + (w - tw) / 2, y + 2, enabled ? VALUE : SUBTLE, false);
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

    /** Stage colour by failure-stage code. */
    static int stageColour(int code) {
        return switch (code) {
            case 1 -> WARNING;
            case 2 -> UNSTABLE;
            case 3 -> FAILURE;
            default -> STABLE;
        };
    }

    /** The stage block (gauge kit 4): word row and four cells. */
    static void stageBlock(GuiGraphicsExtractor g, Font font, int x, int y, int code) {
        int colour = stageColour(code);
        g.text(font, Component.translatable("gui.neropower.gauge.stage.label",
                Component.translatable(stageKey(code))), x, y, colour, false);
        for (int i = 0; i < 4; i++) {
            int cx = x + i * 24;
            g.fill(cx, y + 10, cx + 23, y + 17, i <= code ? stageColour(i) : TROUGH);
            if (i == code) {
                g.fill(cx - 1, y + 9, cx + 24, y + 10, VALUE);
                g.fill(cx - 1, y + 17, cx + 24, y + 18, VALUE);
                g.fill(cx - 1, y + 9, cx, y + 18, VALUE);
                g.fill(cx + 23, y + 9, cx + 24, y + 18, VALUE);
            }
        }
    }

    static String stageKey(int code) {
        return switch (code) {
            case 1 -> "neropower.failure.stage.warning";
            case 2 -> "neropower.failure.stage.unstable";
            case 3 -> "neropower.failure.stage.failure";
            default -> "neropower.failure.stage.stable";
        };
    }
}

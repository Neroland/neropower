package za.co.neroland.neropower.backend.client;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import za.co.neroland.neropower.backend.BackEndReadout;
import za.co.neroland.neropower.failure.FailureStage;

/**
 * Gauge-kit pieces the three back-end screens draw (slot ring, pips, stage block, chip, stall line),
 * code-drawn with {@code fill} and {@code text} in the kit's palette. Screen coordinates in, nothing
 * stored. Colour never carries a state alone: each piece pairs it with a word, a shape or a count.
 */
final class BackEndHud {

    static final int TITLE = 0xFFD6ECFF;
    static final int SUBTLE = 0xFF8DA0B4;
    static final int DIVIDER = 0xFF2A3542;
    static final int TROUGH = 0xFF0B1119;
    static final int HEAT = 0xFFE0543A;
    static final int WATER = 0xFF3F76E4;
    static final int OK = 0xFF3CB043;
    static final int WARN = 0xFFE0B33A;
    static final int HOT = 0xFFFF5438;
    static final int ALARM = 0xFFFF3824;

    /** Stall row height. */
    static final int ROW_H = 9;

    private BackEndHud() {
    }

    /** Stage colour by code (0 stable .. 3 failure). */
    static int stageColour(int code) {
        return switch (code) {
            case 1 -> WARN;
            case 2 -> HOT;
            case 3 -> ALARM;
            default -> OK;
        };
    }

    /**
     * A 22 × 22 slot ring at {@code (x, y)}: a 2 px square track filled clockwise from the top centre
     * to {@code frac}.
     */
    static void slotRing(GuiGraphicsExtractor g, int x, int y, float frac, int colour, boolean outline) {
        if (outline) {
            outlineRect(g, x - 1, y - 1, 24, 24, WARN);
        }
        ringPath(g, x, y, 1.0F, TROUGH);
        ringPath(g, x, y, Math.max(0F, Math.min(1F, frac)), colour);
    }

    /** Fill the first {@code frac} of the ring's 88 px perimeter, clockwise from the top centre. */
    private static void ringPath(GuiGraphicsExtractor g, int x, int y, float frac, int colour) {
        int len = Math.round(88 * frac);
        if (len <= 0) {
            return;
        }
        // top, right half: x+11 .. x+22
        int seg = Math.min(len, 11);
        g.fill(x + 11, y, x + 11 + seg, y + 2, colour);
        len -= seg;
        // right edge, top to bottom
        seg = Math.min(len, 22);
        if (seg > 0) {
            g.fill(x + 20, y, x + 22, y + seg, colour);
        }
        len -= seg;
        // bottom edge, right to left
        seg = Math.min(len, 22);
        if (seg > 0) {
            g.fill(x + 22 - seg, y + 20, x + 22, y + 22, colour);
        }
        len -= seg;
        // left edge, bottom to top
        seg = Math.min(len, 22);
        if (seg > 0) {
            g.fill(x, y + 22 - seg, x + 2, y + 22, colour);
        }
        len -= seg;
        // top, left half: x .. x+11
        seg = Math.min(len, 11);
        if (seg > 0) {
            g.fill(x, y, x + seg, y + 2, colour);
        }
    }

    /** {@code count} pips of 6 × 6 with 2 px gaps; the first {@code lit} filled, the rest outlined. */
    static void pips(GuiGraphicsExtractor g, int x, int y, int count, int lit, int colour) {
        for (int i = 0; i < count; i++) {
            int px = x + i * 8;
            if (i < lit) {
                g.fill(px, y, px + 6, y + 6, colour);
            } else {
                outlineRect(g, px, y, 6, 6, DIVIDER);
            }
        }
    }

    /** The 96 × 17 stage block: "Stage: word" and a strip of four cells, the live one outlined. */
    static void stageBlock(GuiGraphicsExtractor g, Font font, int x, int y, int code) {
        FailureStage stage = FailureStage.byCode(code);
        g.text(font, Component.translatable("gui.neropower.gauge.stage.label",
                Component.translatable(stage.translationKey())), x, y, stageColour(code), false);
        for (int i = 0; i < 4; i++) {
            int cx = x + i * 24;
            g.fill(cx, y + 10, cx + 23, y + 17, i <= code ? stageColour(i) : TROUGH);
            if (i == code) {
                outlineRect(g, cx - 1, y + 9, 25, 9, TITLE);
            }
        }
    }

    /** A chip: 1 px outline and text in the state colour on the trough; returns its width. */
    static int chip(GuiGraphicsExtractor g, Font font, int x, int y, Component text, int colour, boolean closedGlyph) {
        int w = font.width(text) + 6 + 9;
        g.fill(x, y, x + w, y + 11, TROUGH);
        outlineRect(g, x, y, w, 11, colour);
        // 7 × 7 box glyph: closed (shielded) or open-topped (unshielded)
        int gx = x + 3;
        int gy = y + 2;
        g.fill(gx, gy + 6, gx + 7, gy + 7, colour);
        g.fill(gx, gy, gx + 1, gy + 7, colour);
        g.fill(gx + 6, gy, gx + 7, gy + 7, colour);
        if (closedGlyph) {
            g.fill(gx, gy, gx + 7, gy + 1, colour);
        }
        g.text(font, text, x + 12, y + 2, colour, false);
        return w;
    }

    /** The stall-line text for a readout and cause; {@code running} and {@code idle} are the screen's own. */
    static Component stallText(int readout, int cause, Component running, Component idle) {
        return switch (readout) {
            case BackEndReadout.WORK -> running;
            case BackEndReadout.STALL -> Component.translatable("gui.neropower.stall." + BackEndReadout.causeKey(cause));
            case BackEndReadout.HOT -> Component.translatable("gui.neropower.stall.hot");
            case BackEndReadout.TRIP -> Component.translatable("gui.neropower.stall.tripped");
            case BackEndReadout.FAIL -> Component.translatable("gui.neropower.stall.failing");
            default -> idle;
        };
    }

    /** Draw the stall line (subtle colour; FAILURE blinks 5 on / 5 off unless animations are off). */
    static void stallLine(GuiGraphicsExtractor g, Font font, int x, int y, Component text, int readout, long tick,
            boolean animate) {
        if (readout == BackEndReadout.FAIL && animate && (tick / 5) % 2 == 1) {
            return;
        }
        g.text(font, text, x, y, readout == BackEndReadout.FAIL ? ALARM : SUBTLE, false);
    }

    /** The stall line's tooltip: "Status", the full text, and the fix for a stall cause. */
    static void stallTooltip(List<Component> lines, Component text, int readout, int cause) {
        lines.add(Component.translatable("gui.neropower.gauge.status"));
        lines.add(text);
        if (readout == BackEndReadout.STALL && cause != BackEndReadout.CAUSE_NONE) {
            lines.add(Component.translatable("gui.neropower.stall.fix." + BackEndReadout.causeKey(cause)));
        }
    }

    static void outlineRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    /** Whether a panel-relative point is inside a rectangle. */
    static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}

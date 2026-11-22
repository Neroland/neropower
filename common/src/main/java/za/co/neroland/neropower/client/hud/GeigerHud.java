package za.co.neroland.neropower.client.hud;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.HumanoidArm;

import za.co.neroland.neropower.radiation.client.GeigerReadModel;
import za.co.neroland.nerotech.api.MachineSettings;

/**
 * The Geiger Counter HUD: a small plate with an arc dial, the holder's level in pall, a 3 x 3 chunk
 * grid and the count of sources near. Drawn only from {@link GeigerReadModel}; it stores nothing but
 * the corner the player chose and never shows a name, a position or an owner.
 *
 * <p>Every reading is carried twice: the band by its word and its dial segment, a grid cell by its
 * shade and its dots, the top band by its word and the rim. With animations off the needle jumps and
 * the rim is steady.
 *
 * <p>Not registered here. The client entry point of each loader calls {@link #extract} from a HUD
 * layer placed under the chat layer.
 */
public final class GeigerHud {

    /** Screen corner the plate sits in. */
    public enum Corner {
        BOTTOM_LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT;

        /** Parse a config value such as {@code bottom_left}; unknown text gives the default. */
        public static Corner parse(String text) {
            if (text != null) {
                for (Corner c : values()) {
                    if (c.name().equalsIgnoreCase(text.trim())) {
                        return c;
                    }
                }
            }
            return BOTTOM_LEFT;
        }
    }

    public static final int WIDTH = 92;
    public static final int HEIGHT = 54;
    /** Height with a third text row (a badge is carried, or the "disabled" banner is showing). */
    public static final int HEIGHT_TALL = 64;
    /** Distance from the screen edges. */
    public static final int INSET = 12;
    /** Ticks the "disabled on this server" banner stays up after the counter is taken in hand. */
    public static final int OFF_BANNER_TICKS = 100;

    private static final int PLATE = 0xC011161D;
    private static final int EDGE = 0xFF05080D;
    private static final int TITLE = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int DIVIDER = 0xFF2A3542;
    private static final int DOT_DARK = 0xFF11161D;
    private static final int LIME = 0xFFB6F23A;
    /** Dial segment colours, band 0..4. */
    private static final int[] BAND = {0xFF2A3542, 0xFF8DA0B4, 0xFFE0B33A, 0xFFFF5438, 0xFFB6F23A};
    /** Grid cell shades, band 0..4. */
    private static final int[] SHADE = {0xFF0B1119, 0xFF2C3F0A, 0xFF55781A, 0xFF86B52A, 0xFFB6F23A};
    /** First level of each band, then one past the top. */
    private static final int[] BAND_FROM = {0, 10, 200, 500, 800, 1001};

    private static final int DIAL_X = 3;
    private static final int DIAL_Y = 3;
    private static final int DIAL_CX = 23;
    private static final int DIAL_CY = 24;
    private static final int DIAL_OUTER = 22;
    private static final int DIAL_INNER = 17;
    private static final int NEEDLE = 20;
    private static final int GRID_X = 66;
    private static final int GRID_Y = 4;

    private static Corner corner = Corner.BOTTOM_LEFT;

    private GeigerHud() {
    }

    /** The corner the plate is drawn in (client option {@code geigerHudCorner}). */
    public static void setCorner(Corner value) {
        corner = value == null ? Corner.BOTTOM_LEFT : value;
    }

    public static Corner corner() {
        return corner;
    }

    /** Draw the HUD for this frame, or nothing when no counter is held or the HUD is hidden. */
    public static void extract(GuiGraphicsExtractor g, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden(mc) || !GeigerReadModel.visible()) {
            return;
        }
        boolean off = GeigerReadModel.radiationOff();
        boolean waiting = GeigerReadModel.awaitingData();
        boolean banner = off && GeigerReadModel.heldTicks() < OFF_BANNER_TICKS;
        boolean badge = !banner && GeigerReadModel.badgeCarried();
        boolean live = !off && !waiting;
        boolean animate = MachineSettings.renderAnimationsEnabled();
        int height = banner || badge ? HEIGHT_TALL : HEIGHT;
        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int x = left(screenW);
        int y = top(mc.player, screenW, screenH, height);
        Font font = mc.font;

        g.fill(x, y, x + WIDTH, y + height, PLATE);
        outline(g, x, y, WIDTH, height, EDGE);

        int band = live ? GeigerReadModel.band() : 0;
        float level = live ? GeigerReadModel.needle(animate ? partialTick : 1.0F) : 0.0F;
        dial(g, x + DIAL_X, y + DIAL_Y, level, live);
        if (live && band == 4) {
            rim(g, x + DIAL_X, y + DIAL_Y, rimColour(animate, partialTick));
        }
        grid(g, x + GRID_X, y + GRID_Y, live);

        Component value = live
                ? Component.translatable("gui.neropower.rad.value", GeigerReadModel.level())
                : Component.translatable("gui.neropower.rad.none");
        g.text(font, value, x + 3, y + 32, TITLE, false);
        if (live) {
            Component word = Component.translatable("gui.neropower.rad.band." + band);
            // Band 0's dial colour is too dark for text on the plate; its word uses the subtle grey.
            g.text(font, word, x + 89 - font.width(word), y + 32, band == 0 ? SUBTLE : BAND[band], false);
        }
        if (banner) {
            List<FormattedCharSequence> lines =
                    font.split(Component.translatable("gui.neropower.rad.off.long"), WIDTH - 6);
            for (int i = 0; i < lines.size() && i < 2; i++) {
                g.text(font, lines.get(i), x + 3, y + 42 + i * 10, SUBTLE, false);
            }
            return;
        }
        if (off) {
            g.text(font, Component.translatable("gui.neropower.rad.off"), x + 3, y + 42, SUBTLE, false);
        } else {
            int sources = GeigerReadModel.sourcesNear();
            String count = sources >= 99 ? "99+" : String.valueOf(sources);
            g.text(font, Component.translatable("gui.neropower.rad.sources", count), x + 3, y + 42, SUBTLE, false);
        }
        if (badge) {
            String dose = compact(GeigerReadModel.highestBadge());
            g.text(font, Component.translatable("gui.neropower.rad.badge", dose), x + 3, y + 52, SUBTLE, false);
        }
    }

    /** A reading as short text: 340, 12.4k, 1.0M. */
    public static String compact(int reading) {
        int v = Math.max(0, reading);
        if (v < 1_000) {
            return Integer.toString(v);
        }
        if (v < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fk", Math.floor(v / 100.0D) / 10.0D);
        }
        return String.format(Locale.ROOT, "%.1fM", Math.floor(v / 100_000.0D) / 10.0D);
    }

    /**
     * The needle's angle in degrees for a level: 180 at 0 pl (left) to 0 at 1 000 pl (right), five
     * equal 36 degree segments, linear inside each band.
     */
    public static float needleAngle(float level) {
        float v = Math.max(0.0F, Math.min(1000.0F, level));
        int band = 4;
        for (int i = 0; i < 5; i++) {
            if (v < BAND_FROM[i + 1]) {
                band = i;
                break;
            }
        }
        float span = BAND_FROM[band + 1] - BAND_FROM[band] - (band == 4 ? 1 : 0);
        float within = Math.max(0.0F, Math.min(1.0F, (v - BAND_FROM[band]) / span));
        return 180.0F - (band + within) * 36.0F;
    }

    // --- F1 -----------------------------------------------------------------------------------------
    // The hide-HUD flag moved between Minecraft lines (a field on Options, later a method on the HUD
    // object held by Gui) and neither shape is on every compile classpath, so it is looked up once
    // by name. If neither is found the HUD counts as shown; the loader's HUD layer is not called
    // while the HUD is hidden in any case.

    private static boolean hiddenResolved;
    private static java.lang.reflect.Field optionsHideGui;
    private static java.lang.reflect.Field guiHud;
    private static java.lang.reflect.Method hudIsHidden;

    private static boolean hudHidden(Minecraft mc) {
        if (!hiddenResolved) {
            hiddenResolved = true;
            try {
                optionsHideGui = net.minecraft.client.Options.class.getDeclaredField("hideGui");
                optionsHideGui.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                optionsHideGui = null;
            }
            if (optionsHideGui == null) {
                try {
                    guiHud = net.minecraft.client.gui.Gui.class.getDeclaredField("hud");
                    guiHud.setAccessible(true);
                    hudIsHidden = guiHud.getType().getMethod("isHidden");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    guiHud = null;
                    hudIsHidden = null;
                }
            }
        }
        try {
            if (optionsHideGui != null) {
                return optionsHideGui.getBoolean(mc.options);
            }
            if (guiHud != null && hudIsHidden != null) {
                Object hud = guiHud.get(mc.gui);
                return hud != null && Boolean.TRUE.equals(hudIsHidden.invoke(hud));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
        return false;
    }

    // --- placement -----------------------------------------------------------------------------------

    private static int left(int screenW) {
        return corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT ? screenW - INSET - WIDTH : INSET;
    }

    private static int top(LocalPlayer player, int screenW, int screenH, int height) {
        if (corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT) {
            return INSET;
        }
        int bottom = screenH - INSET;
        // The offhand slot sits beside the hotbar on the side opposite the main arm. On a narrow
        // window it would run under the plate, so the plate moves up clear of the hotbar row.
        if (player != null && !player.getOffhandItem().isEmpty() && screenW / 2 - 120 < INSET + WIDTH + 2) {
            boolean slotLeft = player.getMainArm() == HumanoidArm.RIGHT;
            if (slotLeft == (corner == Corner.BOTTOM_LEFT)) {
                bottom = screenH - 36;
            }
        }
        return bottom - height;
    }

    // --- dial ----------------------------------------------------------------------------------------

    /** The 46 x 26 arc dial at {@code (x, y)}: five band segments, the needle and its hub. */
    private static void dial(GuiGraphicsExtractor g, int x, int y, float level, boolean live) {
        for (int row = 0; row <= DIAL_CY; row++) {
            int runStart = 0;
            int runColour = 0;
            for (int col = 0; col <= 46; col++) {
                int colour = col < 46 ? ringColour(col, row) : 0;
                if (colour != runColour) {
                    if (runColour != 0) {
                        g.fill(x + runStart, y + row, x + col, y + row + 1, runColour);
                    }
                    runStart = col;
                    runColour = colour;
                }
            }
        }
        double angle = Math.toRadians(live ? needleAngle(level) : 180.0F);
        double dx = Math.cos(angle);
        double dy = -Math.sin(angle);
        for (int r = 2; r <= NEEDLE; r++) {
            int px = x + DIAL_CX + (int) Math.round(dx * r);
            int py = y + DIAL_CY + (int) Math.round(dy * r);
            g.fill(px, py, px + 1, py + 1, TITLE);
        }
        g.fill(x + DIAL_CX - 1, y + DIAL_CY - 1, x + DIAL_CX + 2, y + DIAL_CY + 2, SUBTLE);
    }

    /** The band colour of a dial pixel, or 0 outside the half ring (and in the 1 px gaps between bands). */
    private static int ringColour(int col, int row) {
        double dx = col + 0.5D - (DIAL_CX + 0.5D);
        double dy = (DIAL_CY + 0.5D) - (row + 0.5D);
        double d2 = dx * dx + dy * dy;
        if (dy < 0.0D || d2 > DIAL_OUTER * DIAL_OUTER || d2 < DIAL_INNER * DIAL_INNER) {
            return 0;
        }
        double fromLeft = 180.0D - Math.toDegrees(Math.atan2(dy, dx));
        int band = Math.min(4, (int) (fromLeft / 36.0D));
        double into = fromLeft - band * 36.0D;
        if (band > 0 && into < 1.5D || band < 4 && into > 34.5D) {
            return 0;
        }
        return BAND[band];
    }

    /** The 1 px rim just outside the arc (top band only). */
    private static void rim(GuiGraphicsExtractor g, int x, int y, int colour) {
        for (int row = 0; row <= DIAL_CY; row++) {
            for (int col = 0; col < 46; col++) {
                double dx = col - DIAL_CX;
                double dy = DIAL_CY - row;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d >= DIAL_OUTER + 0.5D && d < DIAL_OUTER + 1.5D) {
                    g.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
                }
            }
        }
    }

    /** Lime at 40 to 100 % alpha on a 1 Hz sine; steady with animations off. */
    private static int rimColour(boolean animate, float partialTick) {
        if (!animate) {
            return LIME;
        }
        double t = (GeigerReadModel.heldTicks() + partialTick) / 20.0D;
        int alpha = (int) Math.round(255.0D * (0.7D + 0.3D * Math.sin(t * 2.0D * Math.PI)));
        return (alpha << 24) | (LIME & 0x00FFFFFF);
    }

    // --- grid ----------------------------------------------------------------------------------------

    /** The 3 x 3 chunk grid at {@code (x, y)}, north up; hollow cells when there is nothing to show. */
    private static void grid(GuiGraphicsExtractor g, int x, int y, boolean live) {
        g.fill(x + 10, y - 3, x + 13, y - 1, SUBTLE);                 // north tick
        for (int i = 0; i < 9; i++) {
            int cx = x + (i % 3) * 8;
            int cy = y + (i / 3) * 8;
            if (!live) {
                outline(g, cx, cy, 7, 7, DIVIDER);
                continue;
            }
            int band = GeigerReadModel.cellBand(i);
            g.fill(cx, cy, cx + 7, cy + 7, SHADE[band]);
            int dot = band == 4 ? DOT_DARK : TITLE;
            for (int d = 0; d < band; d++) {
                int px = cx + 2 + (d % 2) * 2;
                int py = cy + 2 + (d / 2) * 2;
                g.fill(px, py, px + 1, py + 1, dot);
            }
        }
        outline(g, x + 7, y + 7, 9, 9, TITLE);                        // the holder's chunk
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }
}

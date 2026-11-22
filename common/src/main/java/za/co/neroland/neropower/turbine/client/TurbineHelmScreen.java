package za.co.neroland.neropower.turbine.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.turbine.TurbineHelmMenu;
import za.co.neroland.neropower.turbine.TurbineMath;
import za.co.neroland.neropower.turbine.TurbineReadout;

import static za.co.neroland.neropower.turbine.client.TurbineScreenKit.*;

/**
 * Turbine Helm screen (wide tall hull 248 x 222, spec {@code turbine_helm}): solo energy bar, inlet steam
 * and exhaust tank bars, the rotor-speed arc dial, output / coil / governor lines, the trip flag, the
 * overspeed stage block, the slice diagram, port and steam-use rows, the Reset button and the stall
 * line. Every value comes from the menu's synced ints; NeroTech's screen is the only tooltip emitter.
 */
public class TurbineHelmScreen extends PowerMachineScreen<TurbineHelmMenu> {

    private static final int W = 248;
    private static final int H = 222;
    private static final int[] E = {8, 20, 10, 46};
    private static final int[] T1 = {24, 20, 14, 46};
    private static final int[] T2 = {42, 20, 14, 46};
    private static final int[] D1 = {64, 20, 46, 26};
    private static final int[] L0 = {64, 48, 46, 9};
    private static final int[] L1 = {116, 21, 90, 9};
    private static final int[] L2 = {116, 31, 90, 9};
    private static final int[] L3 = {116, 41, 90, 9};
    private static final int[] F1 = {116, 51, 48, 11};
    private static final int[] ST = {64, 63, 96, 17};
    private static final int SD_X = 40;
    private static final int SD_Y = 84;
    private static final int[] R1 = {8, 96, 232, 9};
    private static final int[] R2 = {8, 106, 232, 9};
    private static final int[] B1 = {210, 72, 36, 12};
    private static final int[] S = {8, 116, 232, 9};
    private static final int[] GOV_SLOT = {228, 36, 16, 16};

    private static final int RATED_LO = 1700;
    private static final int RATED_HI = 1900;
    private static final int DIM_BAND = 0xFF2A3542;
    private static final int GHOST = 0x4DD6ECFF;

    public TurbineHelmScreen(TurbineHelmMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, W, H);
        this.inventoryLabelX = TurbineHelmMenu.PLAYER_X;
    }

    @Override
    protected boolean drawsDefaultGauges() {
        return false;
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    @Override
    protected int presetX() {
        return 210;
    }

    @Override
    protected int presetY() {
        return 56;
    }

    @Override
    protected void sampleReadout(long tick) {
        sampleTank(0, this.menu.steamMb());
        sampleTank(1, this.menu.exhaustMb());
    }

    private int scaleMax() {
        return Math.max(TurbineMath.MAX_TARGET_RPM, this.menu.tripRpm() + 300);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        TurbineHelmMenu m = this.menu;

        gauge(g, x + E[0], y + E[1], E[2], E[3], m.energyFraction(), ENERGY);
        tank(g, x + T1[0], y + T1[1], T1[2], T1[3], m.steamMb(), m.steamCapacityMb(), STEAM);
        tank(g, x + T2[0], y + T2[1], T2[2], T2[3], m.exhaustMb(), m.exhaustCapacityMb(), EXHAUST);

        dial(g, x + D1[0], y + D1[1], m.rpm());
        Component rpm = Component.translatable("gui.neropower.turbine.rpm", GaugeMath.grouped(m.rpm()));
        g.text(this.font, rpm, x + L0[0] + (L0[2] - this.font.width(rpm)) / 2, y + L0[1], VALUE, false);

        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.turbine.out",
                GaugeMath.signedRate(m.outputNePerTick())), L1[2]), x + L1[0], y + L1[1], VALUE, false);
        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.turbine.coils",
                GaugeMath.grouped(m.coilCeilingNePerTick())), L2[2]), x + L2[0], y + L2[1], VALUE, false);
        g.text(this.font, Component.translatable(m.governor()
                ? "gui.neropower.turbine.governor.fitted" : "gui.neropower.turbine.governor.none"),
                x + L3[0], y + L3[1], m.governor() ? VALUE : SUBTLE, false);
        if (m.tripped()) {
            g.fill(x + F1[0], y + F1[1], x + F1[0] + F1[2], y + F1[1] + F1[3], FAILURE);
            Component flag = Component.translatable("gui.neropower.turbine.tripped");
            g.text(this.font, flag, x + F1[0] + (F1[2] - this.font.width(flag)) / 2, y + F1[1] + 2, FLAG_TEXT, false);
        }
        stageBlock(g, this.font, x + ST[0], y + ST[1], m.failureStageCode());
        slices(g, x, y);

        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.turbine.ports", m.portsInlet(),
                m.portsExhaust(), m.portsVent()), R1[2]), x + R1[0], y + R1[1], VALUE, false);
        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.turbine.steam",
                GaugeMath.grouped(m.steamUsedMbPerTick()), GaugeMath.grouped(m.flowCapMbPerTick()),
                GaugeTooltips.percentOf(m.efficiencyPermille())), R2[2]), x + R2[0], y + R2[1], VALUE, false);

        boolean canReset = m.tripped() && TurbineMath.canReset(m.rpm());
        button(g, this.font, x + B1[0], y + B1[1], B1[2], B1[3],
                Component.translatable("gui.neropower.control.turbine.reset"), canReset,
                over(mouseX - x, mouseY - y, B1[0], B1[1], B1[2], B1[3]));

        if (!m.governor() && this.menu.getSlot(3).getItem().isEmpty()) {
            g.fill(x + GOV_SLOT[0] + 4, y + GOV_SLOT[1] + 4, x + GOV_SLOT[0] + 12, y + GOV_SLOT[1] + 12, GHOST);
        }

        g.text(this.font, fit(this.font, stallText(), S[2]), x + S[0], y + S[1], SUBTLE, false);
    }

    /** The arc dial: bands in 3-degree steps along the rim, then the needle and hub. */
    private void dial(GuiGraphicsExtractor g, int x, int y, int rpm) {
        int max = scaleMax();
        int cx = x + 23;
        int cy = y + 24;
        int warn = this.menu.warnRpm();
        int trip = this.menu.tripRpm();
        for (int deg = 0; deg <= 180; deg += 3) {
            int value = max * deg / 180;
            int colour = value >= RATED_LO && value <= RATED_HI ? ENERGY
                    : value >= warn && value <= trip ? UNSTABLE : DIM_BAND;
            if (Math.abs(value - trip) <= max / 120) {
                colour = FAILURE;
            }
            double a = Math.toRadians(180 - deg);
            for (int r = 17; r <= 21; r += 2) {
                int px = cx + (int) Math.round(Math.cos(a) * r);
                int py = cy - (int) Math.round(Math.sin(a) * r);
                g.fill(px, py, px + 2, py + 2, colour);
            }
        }
        double a = Math.toRadians(180 - 180.0D * Math.min(rpm, max) / max);
        for (int r = 0; r <= 20; r++) {
            int px = cx + (int) Math.round(Math.cos(a) * r);
            int py = cy - (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, VALUE);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, VALUE);
    }

    /** The slice diagram: helm glyph, then one 10 x 10 icon per interior slice. */
    private void slices(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 34, y + SD_Y, x + 38, y + SD_Y + 10, SUBTLE);
        int n = this.menu.sliceCount();
        for (int i = 0; i < n; i++) {
            int ix = x + SD_X + 12 * i;
            int iy = y + SD_Y;
            int code = this.menu.sliceCode(i);
            if (code >= 1 && code <= 3) {
                // Vane slice: four-spoke cross, 1-3 bars under it.
                g.fill(ix + 4, iy, ix + 6, iy + 7, VALUE);
                g.fill(ix + 1, iy + 3, ix + 9, iy + 5, VALUE);
                for (int b = 0; b < code; b++) {
                    g.fill(ix + 1 + b * 3, iy + 8, ix + 3 + b * 3, iy + 10, code == 3 ? 0xFF9B7BFF : ENERGY);
                }
            } else if (code >= 4 && code <= 6) {
                // Stator slice: hollow square, 1-3 dots inside.
                g.fill(ix, iy, ix + 10, iy + 1, VALUE);
                g.fill(ix, iy + 9, ix + 10, iy + 10, VALUE);
                g.fill(ix, iy, ix + 1, iy + 10, VALUE);
                g.fill(ix + 9, iy, ix + 10, iy + 10, VALUE);
                for (int d = 0; d < code - 3; d++) {
                    g.fill(ix + 2 + d * 2, iy + 4, ix + 3 + d * 2, iy + 6, code == 6 ? 0xFF9B7BFF : ENERGY);
                }
            } else {
                // Incomplete or bare: "?" in a dashed box.
                for (int d = 0; d < 10; d += 2) {
                    g.fill(ix + d, iy, ix + d + 1, iy + 1, WARNING);
                    g.fill(ix + d, iy + 9, ix + d + 1, iy + 10, WARNING);
                    g.fill(ix, iy + d, ix + 1, iy + d + 1, WARNING);
                    g.fill(ix + 9, iy + d, ix + 10, iy + d + 1, WARNING);
                }
                g.text(this.font, Component.literal("?"), ix + 3, iy + 1, WARNING, false);
            }
        }
    }

    private Component stallText() {
        TurbineHelmMenu m = this.menu;
        return switch (m.readout()) {
            case TurbineReadout.WORK -> Component.translatable("gui.neropower.turbine.running",
                    GaugeMath.grouped(m.outputNePerTick()), GaugeMath.grouped(m.rpm()));
            case TurbineReadout.STALL -> m.stallCause() == 1
                    ? Component.translatable("gui.neropower.turbine.stall.not_formed")
                    : Component.translatable("gui.neropower.stall." + stallKey(m.stallCause()));
            case TurbineReadout.HOT -> Component.translatable("gui.neropower.turbine.stall.overspeed");
            case TurbineReadout.TRIP -> Component.translatable("gui.neropower.stall.tripped");
            case TurbineReadout.FAIL -> Component.translatable("gui.neropower.stall.failing");
            default -> Component.translatable("gui.neropower.stall.idle");
        };
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        TurbineHelmMenu m = this.menu;
        if (over(mx, my, E[0], E[1], E[2], E[3])) {
            // This hull's own energy bar (one provider chain: GaugeTooltipOnceTest).
            energyTooltip(lines);
            return;
        }
        if (over(mx, my, T1[0], T1[1], T1[2], T1[3])) {
            tankTooltip(lines, GaugeTooltips.tr("turbine.steam_in"), Component.translatable("fluid.neropower.steam"),
                    m.steamMb(), m.steamCapacityMb(), tankWindow(0).perSecond());
        } else if (over(mx, my, T2[0], T2[1], T2[2], T2[3])) {
            tankTooltip(lines, GaugeTooltips.tr("turbine.exhaust"),
                    Component.translatable("fluid.neropower.exhaust_steam"), m.exhaustMb(), m.exhaustCapacityMb(),
                    tankWindow(1).perSecond());
            if (m.exhaustCapacityMb() > 0L && m.exhaustMb() >= m.exhaustCapacityMb()) {
                lines.add(GaugeTooltips.tr("turbine.exhaust.full"));
            }
        } else if (over(mx, my, D1[0], D1[1], D1[2], D1[3] + L0[3] + 2)) {
            lines.add(GaugeTooltips.tr("turbine.speed"));
            lines.add(GaugeTooltips.tr("turbine.speed.value", GaugeMath.grouped(m.rpm())));
            lines.add(GaugeTooltips.tr("turbine.speed.scales"));
            lines.add(GaugeTooltips.tr("turbine.speed.trips", GaugeMath.grouped(m.tripRpm())));
        } else if (over(mx, my, L1[0], L1[1], L1[2], L2[1] + L2[3] - L1[1])) {
            lines.add(GaugeTooltips.tr("turbine.output"));
            lines.add(GaugeTooltips.tr("turbine.output.now", GaugeMath.grouped(m.outputNePerTick())));
            lines.add(GaugeTooltips.tr("turbine.output.ceiling", GaugeMath.grouped(m.coilCeilingNePerTick()),
                    m.woundStators()));
            lines.add(GaugeTooltips.tr("turbine.output.overspeed"));
        } else if (over(mx, my, L3[0], L3[1], L3[2], L3[3])
                || (this.menu.getSlot(3).getItem().isEmpty() && over(mx, my, GOV_SLOT[0], GOV_SLOT[1], 16, 16))) {
            lines.add(GaugeTooltips.tr("turbine.governor"));
            lines.add(GaugeTooltips.tr(m.governor() ? "turbine.governor.fitted" : "turbine.governor.none"));
        } else if (over(mx, my, ST[0], ST[1], ST[2], ST[3])) {
            lines.add(GaugeTooltips.tr("stage"));
            lines.add(Component.translatable(stageKey(m.failureStageCode())));
            int next = switch (m.failureStageCode()) {
                case 0 -> m.warnRpm();
                case 1 -> m.unstableRpm();
                default -> m.tripRpm();
            };
            lines.add(GaugeTooltips.tr("turbine.stage.measure", GaugeMath.grouped(m.rpm()), GaugeMath.grouped(next)));
            String action = switch (m.failureAction()) {
                case 1 -> "shed";
                case 2 -> "blast";
                default -> "trip";
            };
            lines.add(GaugeTooltips.tr("turbine.stage.action." + action));
        } else if (over(mx, my, R1[0], R1[1], R1[2], R1[3])) {
            lines.add(GaugeTooltips.tr("turbine.ports"));
            lines.add(GaugeTooltips.tr("turbine.ports.inlet"));
            lines.add(GaugeTooltips.tr("turbine.ports.exhaust"));
            lines.add(GaugeTooltips.tr("turbine.ports.vent", GaugeTooltips.percentOf(m.ventEfficiencyPermille())));
            lines.add(GaugeTooltips.tr("turbine.ports.cycle"));
        } else if (over(mx, my, R2[0], R2[1], R2[2], R2[3])) {
            lines.add(GaugeTooltips.tr("turbine.steam_use"));
            lines.add(GaugeTooltips.tr("turbine.steam_use.used", GaugeMath.grouped(m.steamUsedMbPerTick()),
                    GaugeMath.grouped(m.flowCapMbPerTick())));
            lines.add(GaugeTooltips.tr("turbine.steam_use.efficiency", GaugeTooltips.percentOf(m.efficiencyPermille())));
            lines.add(GaugeTooltips.tr("turbine.steam_use.exhaust", GaugeTooltips.percentOf(m.exhaustFactorPermille())));
        } else if (over(mx, my, B1[0], B1[1], B1[2], B1[3])) {
            lines.add(Component.translatable("gui.neropower.control.turbine.reset.tip"));
            if (!m.tripped()) {
                lines.add(Component.translatable("gui.neropower.control.turbine.reset.not_tripped"));
            } else if (!TurbineMath.canReset(m.rpm())) {
                lines.add(Component.translatable("gui.neropower.control.turbine.reset.wait",
                        GaugeMath.grouped(m.rpm())));
            }
        } else if (over(mx, my, S[0], S[1], S[2], S[3])) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stallText());
            if (m.readout() == TurbineReadout.STALL && m.stallCause() == 1) {
                lines.add(Component.translatable("gui.neropower.stall.fix.turbine_not_formed"));
            }
        } else {
            sliceTooltip(lines, mx, my);
        }
    }

    private void sliceTooltip(List<Component> lines, int mx, int my) {
        int n = this.menu.sliceCount();
        if (my < SD_Y || my >= SD_Y + 10 || mx < SD_X) {
            return;
        }
        int i = (mx - SD_X) / 12;
        if (i >= n || (mx - SD_X) % 12 >= 10) {
            return;
        }
        int code = this.menu.sliceCode(i);
        lines.add(GaugeTooltips.tr("turbine.slice", i + 1, n));
        if (code >= 1 && code <= 3) {
            lines.add(GaugeTooltips.tr("turbine.slice.vane", Component.translatable("item.neropower." + vaneId(code)),
                    GaugeMath.grouped(this.menu.tierFlow(code)), GaugeTooltips.percentOf(this.menu.tierEfficiency(code))));
        } else if (code >= 4 && code <= 6) {
            int tier = code - 3;
            lines.add(GaugeTooltips.tr("turbine.slice.stator", tier, GaugeMath.grouped(8L * this.menu.tierCeiling(tier))));
        } else {
            lines.add(GaugeTooltips.tr("turbine.slice.incomplete"));
        }
    }

    private static String vaneId(int tier) {
        return switch (tier) {
            case 2 -> "cindersteel_vane_set";
            case 3 -> "superconducting_vane_set";
            default -> "steel_vane_set";
        };
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mx = mouseButtonEvent.x();
        double my = mouseButtonEvent.y();
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
    *///?}
        int bx = this.leftPos + B1[0];
        int by = this.topPos + B1[1];
        if (mx >= bx && mx < bx + B1[2] && my >= by && my < by + B1[3]) {
            if (this.menu.tripped() && TurbineMath.canReset(this.menu.rpm())
                    && this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        MenuButtons.encode(TurbineHelmMenu.ACTION_RESET, 0));
            }
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mx, my, button);
        *///?}
    }
}

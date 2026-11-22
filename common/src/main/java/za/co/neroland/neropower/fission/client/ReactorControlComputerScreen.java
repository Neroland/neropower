package za.co.neroland.neropower.fission.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.fission.ControlComputerLogic;
import za.co.neroland.neropower.fission.ReactorControlComputerMenu;
import za.co.neroland.neropower.menu.MenuButtons;

/**
 * Reactor Control Computer screen (wide tall hull, 248 × 222): four
 * signal rows (Heat, Fuel, Output, Stage) whose live bars are also the threshold sliders (click to set),
 * the Auto-SCRAM checkbox and level slider, the redstone and comparator choices (left click next, right
 * click previous), a lamp test and the stall line. No default gauges: every number is the core's.
 */
public class ReactorControlComputerScreen extends PowerMachineScreen<ReactorControlComputerMenu> {

    private static final int LABEL = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int EDGE = 0xFF05080D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int DIVIDER = 0xFF2A3542;
    private static final int BUTTON = 0xFF1B232E;
    private static final int CHIP_ON = 0xFFE0B33A;
    private static final int AUTO = 0xFFFF5438;
    private static final int[] SIGNAL_COLORS = {0xFFE0543A, 0xFFE0B33A, 0xFF55C2F0, 0xFF3CB043};
    private static final int[] STAGE_COLORS = {0xFF3CB043, 0xFFE0B33A, 0xFFFF5438, 0xFFFF3824};
    private static final String[] SIGNAL_KEYS = {"heat", "fuel", "output", "stage", "off"};

    private static final int ROW_Y = 22;
    private static final int ROW_STRIDE = 12;
    private static final int BAR_X = 40;
    private static final int BAR_W = 90;
    private static final int CB_X = 8;
    private static final int CB_Y = 70;
    private static final int SL_X = 88;
    private static final int SL_Y = 72;
    private static final int SL_W = 80;
    private static final int CY1_Y = 83;
    private static final int CY2_Y = 97;
    private static final int CY_X = 72;
    private static final int CY_W = 56;
    private static final int B1_X = 146;
    private static final int B1_W = 60;
    private static final int S_Y = 116;

    public ReactorControlComputerScreen(ReactorControlComputerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, ReactorControlComputerMenu.WIDTH, ReactorControlComputerMenu.HEIGHT);
        this.inventoryLabelX = 43;
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

    private static Component signalName(int signal) {
        return Component.translatable("gui.neropower.control_computer.signal." + SIGNAL_KEYS[Math.max(0, Math.min(4, signal))]);
    }

    // --- drawing -----------------------------------------------------------------------------------

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        boolean linked = this.menu.linked();
        for (int signal = 0; signal < ControlComputerLogic.SIGNALS; signal++) {
            int ry = y + ROW_Y + signal * ROW_STRIDE;
            g.text(this.font, signalName(signal), x + 8, ry, LABEL, false);
            int reading = this.menu.reading(signal);
            int threshold = this.menu.threshold(signal);
            boolean stage = signal == ControlComputerLogic.STAGE;
            float fraction = stage ? reading / 3F : reading / 1000F;
            float thumb = stage ? threshold / 3F : threshold / 1000F;
            int bx = x + BAR_X;
            g.fill(bx - 1, ry, bx + BAR_W + 1, ry + 9, EDGE);
            g.fill(bx, ry + 1, bx + BAR_W, ry + 8, TROUGH);
            if (stage) {
                for (int cell = 0; cell < 4; cell++) {
                    int cx = bx + cell * 23;
                    g.fill(cx, ry + 2, cx + 21, ry + 7, linked && cell <= reading ? STAGE_COLORS[cell] : DIVIDER);
                }
            } else if (linked) {
                int w = Math.round(BAR_W * Math.max(0F, Math.min(1F, fraction)));
                if (w > 0) {
                    g.fill(bx, ry + 1, bx + w, ry + 8, SIGNAL_COLORS[signal]);
                }
            }
            int tx = bx + Math.round((BAR_W - 4) * Math.max(0F, Math.min(1F, thumb)));
            g.fill(tx, ry - 1, tx + 4, ry + 9, LABEL);
            Component value = !linked ? Component.literal("--")
                    : stage ? Component.translatable("gui.neropower.control_computer.stage_short." + reading)
                    : Component.literal(reading / 10 + "%");
            g.text(this.font, value, x + 134, ry, LABEL, false);
            Component limit = stage
                    ? Component.translatable("gui.neropower.control_computer.at_least",
                            Component.translatable("gui.neropower.control_computer.stage_short." + threshold))
                    : Component.translatable(signal == ControlComputerLogic.FUEL
                            ? "gui.neropower.control_computer.at_most" : "gui.neropower.control_computer.at_least",
                            threshold / 10 + "%");
            g.text(this.font, limit, x + 162, ry, SUBTLE, false);
            boolean on = linked && ControlComputerLogic.on(signal, this.menu.heat(), this.menu.fuel(),
                    this.menu.output(), this.menu.stage(), threshold);
            int level = linked ? ControlComputerLogic.comparator(signal, this.menu.heat(), this.menu.fuel(),
                    this.menu.output(), this.menu.stage()) : 0;
            int cx = x + 194;
            g.fill(cx, ry - 1, cx + 12, ry + 10, on ? CHIP_ON : DIVIDER);
            if (!on) {
                g.fill(cx + 1, ry, cx + 11, ry + 9, TROUGH);
            }
            Component chip = Component.literal(Integer.toString(level));
            g.text(this.font, chip, cx + (12 - this.font.width(chip)) / 2 + 1, ry + 1, on ? EDGE : LABEL, false);
        }

        // Auto-SCRAM checkbox and level slider.
        boolean auto = this.menu.autoScram();
        g.fill(x + CB_X, y + CB_Y, x + CB_X + 10, y + CB_Y + 10, SUBTLE);
        g.fill(x + CB_X + 1, y + CB_Y + 1, x + CB_X + 9, y + CB_Y + 9, TROUGH);
        if (auto) {
            g.fill(x + CB_X + 2, y + CB_Y + 2, x + CB_X + 8, y + CB_Y + 8, LABEL);
        }
        g.text(this.font, Component.translatable("gui.neropower.control_computer.auto_scram"),
                x + CB_X + 12, y + CB_Y + 1, this.menu.tripped() ? AUTO : LABEL, false);
        int at = this.menu.autoScramAt();
        float slider = (at - ControlComputerLogic.AUTO_SCRAM_MIN)
                / (float) (ControlComputerLogic.AUTO_SCRAM_MAX - ControlComputerLogic.AUTO_SCRAM_MIN);
        g.fill(x + SL_X - 1, y + SL_Y - 1, x + SL_X + SL_W + 1, y + SL_Y + 7, EDGE);
        g.fill(x + SL_X, y + SL_Y, x + SL_X + SL_W, y + SL_Y + 6, TROUGH);
        int sw = Math.round(SL_W * slider);
        if (sw > 0) {
            g.fill(x + SL_X, y + SL_Y, x + SL_X + sw, y + SL_Y + 6, auto ? AUTO : DIVIDER);
        }
        int st = x + SL_X + Math.round((SL_W - 4) * slider);
        g.fill(st, y + CB_Y, st + 4, y + CB_Y + 10, auto ? LABEL : SUBTLE);
        g.text(this.font, Component.translatable("gui.neropower.control_computer.at", at / 10),
                x + 172, y + CB_Y + 1, auto ? LABEL : SUBTLE, false);

        // Redstone and comparator choices.
        g.text(this.font, Component.translatable("gui.neropower.control_computer.redstone"), x + 8, y + CY1_Y + 2, LABEL, false);
        button(g, x + CY_X, y + CY1_Y, CY_W, Component.literal("‹ ").append(signalName(this.menu.redstoneSignal()))
                .append(" ›"));
        g.text(this.font, Component.translatable("gui.neropower.control_computer.now", this.menu.redstoneNow()),
                x + 134, y + CY1_Y + 2, this.menu.redstoneNow() > 0 ? CHIP_ON : SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.control_computer.comparator"), x + 8, y + CY2_Y + 2, LABEL, false);
        button(g, x + CY_X, y + CY2_Y, CY_W, Component.literal("‹ ").append(signalName(this.menu.comparatorSignal()))
                .append(" ›"));
        button(g, x + B1_X, y + CY2_Y, B1_W, Component.translatable("gui.neropower.control_computer.test"));

        // Stall line.
        g.text(this.font, stallLine(), x + 8, y + S_Y, !linked ? SUBTLE : this.menu.tripped() ? AUTO : LABEL, false);
    }

    private Component stallLine() {
        return !this.menu.linked() ? Component.translatable("gui.neropower.stall.control_computer.unlinked")
                : this.menu.tripped() ? Component.translatable("gui.neropower.stall.control_computer.tripped")
                : Component.translatable("gui.neropower.control_computer.running",
                        Component.translatable(FailureStage.byCode(this.menu.stage()).translationKey()));
    }

    private void button(GuiGraphicsExtractor g, int bx, int by, int w, Component label) {
        g.fill(bx - 1, by - 1, bx + w + 1, by + 13, EDGE);
        g.fill(bx, by, bx + w, by + 12, BUTTON);
        int tw = this.font.width(label);
        g.text(this.font, label, bx + Math.max(2, (w - tw) / 2), by + 2, LABEL, false);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- tooltips ----------------------------------------------------------------------------------

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        for (int signal = 0; signal < ControlComputerLogic.SIGNALS; signal++) {
            if (inRect(mx, my, 8, ROW_Y + signal * ROW_STRIDE - 1, 200, ROW_STRIDE)) {
                rowTooltip(lines, signal);
                return;
            }
        }
        if (inRect(mx, my, CB_X, CB_Y, 76, 10)) {
            lines.add(GaugeTooltips.tr("control_computer.auto_scram_tip", this.menu.autoScram()
                    ? Component.translatable("options.on") : Component.translatable("options.off")));
        } else if (inRect(mx, my, SL_X, CB_Y, SL_W + 40, 10)) {
            lines.add(GaugeTooltips.tr("control_computer.auto_scram_at_tip"));
        } else if (inRect(mx, my, CY_X, CY1_Y, CY_W, 12) || inRect(mx, my, 134, CY1_Y, 72, 12)) {
            lines.add(GaugeTooltips.tr("control_computer.redstone_tip", signalName(this.menu.redstoneSignal())));
            lines.add(GaugeTooltips.tr("control_computer.redstone_now", this.menu.redstoneNow()));
        } else if (inRect(mx, my, CY_X, CY2_Y, CY_W, 12)) {
            lines.add(GaugeTooltips.tr("control_computer.comparator_tip", signalName(this.menu.comparatorSignal())));
        } else if (inRect(mx, my, B1_X, CY2_Y, B1_W, 12)) {
            lines.add(GaugeTooltips.tr("control_computer.test_tip"));
        } else if (inRect(mx, my, 8, S_Y - 1, 200, 10)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stallLine());
            if (!this.menu.linked()) {
                lines.add(Component.translatable("gui.neropower.stall.fix.control_computer_unlinked"));
            } else if (this.menu.tripped()) {
                lines.add(Component.translatable("gui.neropower.stall.fix.control_computer_tripped"));
            }
        }
    }

    private void rowTooltip(List<Component> lines, int signal) {
        boolean linked = this.menu.linked();
        int reading = this.menu.reading(signal);
        int threshold = this.menu.threshold(signal);
        int level = linked ? ControlComputerLogic.comparator(signal, this.menu.heat(), this.menu.fuel(),
                this.menu.output(), this.menu.stage()) : 0;
        switch (signal) {
            case ControlComputerLogic.HEAT -> {
                lines.add(GaugeTooltips.tr("control_computer.heat"));
                lines.add(GaugeTooltips.tr("control_computer.heat_value", GaugeTooltips.percentOf(reading)));
                lines.add(GaugeTooltips.tr("control_computer.on_at_least", GaugeTooltips.percentOf(threshold)));
            }
            case ControlComputerLogic.FUEL -> {
                lines.add(GaugeTooltips.tr("control_computer.fuel"));
                lines.add(GaugeTooltips.tr("control_computer.fuel_value", GaugeTooltips.percentOf(reading), this.menu.rods()));
                lines.add(GaugeTooltips.tr("control_computer.on_at_most", GaugeTooltips.percentOf(threshold)));
            }
            case ControlComputerLogic.OUTPUT -> {
                lines.add(GaugeTooltips.tr("control_computer.output"));
                lines.add(GaugeTooltips.tr(this.menu.steamMode() ? "control_computer.output_steam" : "control_computer.output_ne",
                        this.menu.outputRate()));
                lines.add(GaugeTooltips.tr("control_computer.output_value", GaugeTooltips.percentOf(reading)));
                lines.add(GaugeTooltips.tr("control_computer.on_at_least", GaugeTooltips.percentOf(threshold)));
            }
            default -> {
                lines.add(GaugeTooltips.tr("control_computer.stage"));
                lines.add(Component.translatable(FailureStage.byCode(reading).translationKey()));
                lines.add(GaugeTooltips.tr("control_computer.stage_on",
                        Component.translatable(FailureStage.byCode(threshold).translationKey())));
            }
        }
        lines.add(GaugeTooltips.tr("control_computer.level_now", level));
        lines.add(GaugeTooltips.tr("control_computer.slider_tip"));
    }

    // --- input -------------------------------------------------------------------------------------

    private void send(int action, int value) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MenuButtons.encode(action, value));
        }
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        int button = mouseButtonEvent.button();
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    *///?}
        if (handleClick((int) mouseX - this.leftPos, (int) mouseY - this.topPos, button)) {
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }

    private boolean handleClick(int mx, int my, int button) {
        boolean back = button == 1;
        for (int signal = 0; signal < ControlComputerLogic.SIGNALS; signal++) {
            int ry = ROW_Y + signal * ROW_STRIDE;
            if (inRect(mx, my, BAR_X - 2, ry - 1, BAR_W + 4, ROW_STRIDE)) {
                float f = Math.max(0F, Math.min(1F, (mx - BAR_X) / (float) BAR_W));
                if (signal == ControlComputerLogic.STAGE) {
                    send(ReactorControlComputerMenu.ACTION_THRESHOLD_STAGE, 1 + Math.min(2, (int) (f * 3F)));
                } else {
                    send(ReactorControlComputerMenu.ACTION_THRESHOLD_HEAT + signal, Math.round(f * 100F));
                }
                return true;
            }
        }
        if (inRect(mx, my, CB_X, CB_Y, 76, 10)) {
            send(ReactorControlComputerMenu.ACTION_AUTO_SCRAM, this.menu.autoScram() ? 0 : 1);
            return true;
        }
        if (inRect(mx, my, SL_X - 2, CB_Y, SL_W + 4, 10)) {
            float f = Math.max(0F, Math.min(1F, (mx - SL_X) / (float) SL_W));
            send(ReactorControlComputerMenu.ACTION_AUTO_SCRAM_AT, 50 + Math.round(f * 50F));
            return true;
        }
        if (inRect(mx, my, CY_X, CY1_Y, CY_W, 12)) {
            int next = Math.floorMod(this.menu.redstoneSignal() + (back ? -1 : 1), ControlComputerLogic.OFF + 1);
            send(ReactorControlComputerMenu.ACTION_REDSTONE_SIGNAL, next);
            return true;
        }
        if (inRect(mx, my, CY_X, CY2_Y, CY_W, 12)) {
            int next = Math.floorMod(this.menu.comparatorSignal() + (back ? -1 : 1), ControlComputerLogic.SIGNALS);
            send(ReactorControlComputerMenu.ACTION_COMPARATOR_SIGNAL, next);
            return true;
        }
        if (inRect(mx, my, B1_X, CY2_Y, B1_W, 12)) {
            send(ReactorControlComputerMenu.ACTION_TEST_LAMPS, 0);
            return true;
        }
        return false;
    }
}

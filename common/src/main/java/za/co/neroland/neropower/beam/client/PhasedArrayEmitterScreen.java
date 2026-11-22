package za.co.neroland.neropower.beam.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.beam.PhasedArrayEmitterMenu;
import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.grid.client.GridScreenKit;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.storage.client.StorageScreenKit;

/**
 * Phased Array Emitter screen (tall hull 176 × 222): solo energy bar, the
 * target list (serving marker, letter handle, distance, loss), Up / Down / Unlink on the selected row and
 * the stall line. No position, dimension or owner is ever shown.
 */
public class PhasedArrayEmitterScreen extends PowerMachineScreen<PhasedArrayEmitterMenu> {

    private static final int ROWS_X = 24;
    private static final int ROWS_Y = 32;

    private int selected = -1;

    public PhasedArrayEmitterScreen(PhasedArrayEmitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 222);
    }

    @Override
    protected boolean drawsDefaultGauges() {
        return false;
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    private int rowHeight() {
        return this.menu.maxTargets() <= 4 ? 20 : 10;
    }

    private static String handle(int row) {
        return String.valueOf((char) ('A' + row));
    }

    private int energyPermille() {
        long cap = this.menu.energyCapacity();
        return cap <= 0 ? 0 : (int) Math.min(1000L, this.menu.energyStored() * 1000L / cap);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        int mx = mouseX - x;
        int my = mouseY - y;
        StorageScreenKit.bar(g, x + 8, y + 20, 10, 46, energyPermille(), StorageScreenKit.ENERGY);
        g.text(this.font, Component.translatable("gui.neropower.phased_array.head.target"), x + 24, y + 21, GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.phased_array.head.dist"), x + 66, y + 21, GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.phased_array.head.loss"), x + 104, y + 21, GridScreenKit.SUBTLE, false);
        int count = this.menu.targetCount();
        if (this.selected >= count) {
            this.selected = -1;
        }
        int rh = rowHeight();
        for (int i = 0; i < count; i++) {
            int ry = ROWS_Y + i * rh;
            int ty = ry + (rh - 8) / 2;
            if (i == this.selected) {
                GridScreenKit.selectedRow(g, x, y, ROWS_X, ry, 110, rh);
            }
            if (i == this.menu.servingIndex()) {
                g.text(this.font, Component.literal(">"), x + 28, y + ty, GridScreenKit.VIOLET, false);
            }
            g.text(this.font, Component.literal(handle(i)), x + 36, y + ty, GridScreenKit.VALUE, false);
            g.text(this.font, Component.translatable("gui.neropower.phased_array.dist", this.menu.distance(i)),
                    x + 66, y + ty, GridScreenKit.VALUE, false);
            if (this.menu.blocked(i)) {
                g.text(this.font, Component.translatable("gui.neropower.phased_array.blocked"), x + 104, y + ty, GridScreenKit.AMBER, false);
            } else {
                g.text(this.font, Component.literal(GaugeTooltips.percentOf(this.menu.lossPermille(i)) + "%"),
                        x + 104, y + ty, GridScreenKit.VALUE, false);
            }
        }
        boolean sel = this.selected >= 0;
        GridScreenKit.button(g, this.font, x, y, 138, 72, 36, 12, Component.translatable("gui.neropower.control.phased_array.up"),
                sel && this.selected > 0, GridScreenKit.in(mx, my, 138, 72, 36, 12));
        GridScreenKit.button(g, this.font, x, y, 138, 86, 36, 12, Component.translatable("gui.neropower.control.phased_array.down"),
                sel && this.selected < count - 1, GridScreenKit.in(mx, my, 138, 86, 36, 12));
        GridScreenKit.button(g, this.font, x, y, 138, 100, 36, 12, Component.translatable("gui.neropower.control.phased_array.unlink"),
                sel, GridScreenKit.in(mx, my, 138, 100, 36, 12));
        g.text(this.font, stall(), x + 8, y + 116, GridScreenKit.SUBTLE, false);
    }

    private Component stall() {
        int serving = this.menu.servingIndex();
        Component running = Component.translatable("gui.neropower.phased_array.running",
                GaugeMath.signedRate(this.menu.passNe()), serving >= 0 ? handle(serving) : "-");
        Component idle = Component.translatable("gui.neropower.phased_array.idle");
        if (this.menu.readout() == 2 && this.menu.stallCause() == 11) {
            return Component.translatable("gui.neropower.phased_array.no_targets");
        }
        if (this.menu.readout() == 2 && this.menu.stallCause() == 12) {
            return Component.translatable("gui.neropower.phased_array.all_blocked");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (GridScreenKit.in(mx, my, 8, 20, 10, 46)) {
            energyTooltip(lines);
            return;
        }
        if (GridScreenKit.in(mx, my, 24, 21, 110, 9)) {
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.targets"));
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.targets.line", this.menu.maxTargets()));
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.targets.link"));
            return;
        }
        int rh = rowHeight();
        for (int i = 0; i < this.menu.targetCount(); i++) {
            if (!GridScreenKit.in(mx, my, ROWS_X, ROWS_Y + i * rh, 110, rh)) {
                continue;
            }
            long pass = this.menu.transferPerPass();
            long arrives = pass * (1000 - this.menu.lossPermille(i)) / 1000L;
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.target", handle(i)));
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.target.dist", this.menu.distance(i)));
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.target.loss",
                    GaugeTooltips.percentOf(this.menu.lossPermille(i)), GaugeMath.grouped(arrives), GaugeMath.grouped(pass)));
            String state = this.menu.outOfRange(i) ? "out_of_range" : this.menu.blocked(i) ? "blocked"
                    : i == this.menu.servingIndex() ? "serving" : "waiting";
            lines.add(Component.translatable("gui.neropower.gauge.phased_array.target." + state));
            return;
        }
        if (GridScreenKit.in(mx, my, 138, 72, 36, 40)) {
            if (this.selected < 0) {
                lines.add(Component.translatable("gui.neropower.control.select_first"));
            } else if (my < 86) {
                lines.add(Component.translatable(this.selected == 0 ? "gui.neropower.control.phased_array.first"
                        : "gui.neropower.control.phased_array.up.tip", handle(this.selected)));
            } else if (my < 100) {
                lines.add(Component.translatable(this.selected == this.menu.targetCount() - 1
                        ? "gui.neropower.control.phased_array.last" : "gui.neropower.control.phased_array.down.tip",
                        handle(this.selected)));
            } else {
                lines.add(Component.translatable("gui.neropower.control.phased_array.unlink.tip", handle(this.selected)));
            }
        } else if (GridScreenKit.in(mx, my, 8, 116, 160, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
        }
    }

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
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    *///?}
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        int rh = rowHeight();
        int count = this.menu.targetCount();
        for (int i = 0; i < count; i++) {
            if (GridScreenKit.in(mx, my, ROWS_X, ROWS_Y + i * rh, 110, rh)) {
                this.selected = i;
                return true;
            }
        }
        if (this.selected >= 0) {
            if (GridScreenKit.in(mx, my, 138, 72, 36, 12) && this.selected > 0) {
                send(PhasedArrayEmitterMenu.ACTION_MOVE, this.selected * 2);
                this.selected--;
                return true;
            }
            if (GridScreenKit.in(mx, my, 138, 86, 36, 12) && this.selected < count - 1) {
                send(PhasedArrayEmitterMenu.ACTION_MOVE, this.selected * 2 + 1);
                this.selected++;
                return true;
            }
            if (GridScreenKit.in(mx, my, 138, 100, 36, 12)) {
                send(PhasedArrayEmitterMenu.ACTION_UNLINK, this.selected);
                this.selected = -1;
                return true;
            }
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }
}

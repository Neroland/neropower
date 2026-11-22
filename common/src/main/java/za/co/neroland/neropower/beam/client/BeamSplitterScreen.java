package za.co.neroland.neropower.beam.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.beam.BeamSplitterMenu;
import za.co.neroland.neropower.beam.SplitMath;
import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.grid.client.GridScreenKit;
import za.co.neroland.neropower.menu.MenuButtons;
import za.co.neroland.neropower.storage.client.StorageScreenKit;

/**
 * Beam Splitter screen (wide tall hull 248 × 222): solo energy bar, incoming
 * pass, lens slot, the link list (letter handle, distance, share bar slider, percentage), Unlink and the
 * stall line. Links are named by letter only: no position, dimension or owner is ever shown.
 */
public class BeamSplitterScreen extends PowerMachineScreen<BeamSplitterMenu> {

    private static final int ROWS_X = 24;
    private static final int ROWS_Y = 48;
    private static final int BAR_X = 96;
    private static final int BAR_W = 70;

    private int selected = -1;

    public BeamSplitterScreen(BeamSplitterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, BeamSplitterMenu.WIDTH, BeamSplitterMenu.HEIGHT);
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

    private int rowHeight() {
        return this.menu.maxLinks() <= 4 ? 16 : 8;
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
        Component in = this.menu.hasIncoming()
                ? Component.translatable("gui.neropower.beam_splitter.in", GaugeMath.signedRate(this.menu.lastIn()))
                : Component.translatable("gui.neropower.beam_splitter.no_in");
        g.text(this.font, in, x + 24, y + 21, this.menu.hasIncoming() ? GridScreenKit.VALUE : GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.beam_splitter.lens"), x + 160, y + 24, GridScreenKit.SUBTLE, false);

        g.text(this.font, Component.translatable("gui.neropower.beam_splitter.head.link"), x + 24, y + 38, GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.beam_splitter.head.dist"), x + 46, y + 38, GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.beam_splitter.head.share"), x + 96, y + 38, GridScreenKit.SUBTLE, false);
        Component free = Component.translatable("gui.neropower.beam_splitter.free",
                GaugeTooltips.percentOf(1000 - this.menu.totalShare()));
        g.text(this.font, free, x + 206 - this.font.width(free), y + 38, GridScreenKit.SUBTLE, false);

        int rh = rowHeight();
        int count = this.menu.linkCount();
        if (this.selected >= count) {
            this.selected = -1;
        }
        for (int i = 0; i < count; i++) {
            int ry = ROWS_Y + i * rh;
            int ty = ry + (rh - 8) / 2;
            if (i == this.selected) {
                GridScreenKit.selectedRow(g, x, y, ROWS_X, ry, 182, rh);
            }
            g.text(this.font, Component.literal(handle(i)), x + 30, y + ty, GridScreenKit.VALUE, false);
            if (this.menu.blocked(i)) {
                g.text(this.font, Component.translatable("gui.neropower.beam_splitter.blocked"), x + 46, y + ty, GridScreenKit.AMBER, false);
            } else {
                g.text(this.font, Component.translatable("gui.neropower.beam_splitter.dist", this.menu.distance(i)),
                        x + 46, y + ty, GridScreenKit.VALUE, false);
            }
            int by = ry + (rh - 5) / 2;
            GridScreenKit.hbar(g, x, y, BAR_X, by, BAR_W, 5, this.menu.share(i), GridScreenKit.SHARE);
            int tx = BAR_X + Math.round((BAR_W - 4) * this.menu.share(i) / 1000.0F);
            g.fill(x + tx, y + ry + 1, x + tx + 4, y + ry + rh - 1, GridScreenKit.VALUE);
            g.text(this.font, Component.translatable("gui.neropower.beam_splitter.share",
                    GaugeTooltips.percentOf(this.menu.share(i))), x + 170, y + ty, GridScreenKit.VALUE, false);
        }
        GridScreenKit.button(g, this.font, x, y, 210, 72, 36, 12,
                Component.translatable("gui.neropower.control.beam_splitter.unlink"), this.selected >= 0,
                GridScreenKit.in(mx, my, 210, 72, 36, 12));
        g.text(this.font, stall(), x + 8, y + 116, GridScreenKit.SUBTLE, false);
    }

    private Component stall() {
        Component running = Component.translatable("gui.neropower.beam_splitter.running",
                GaugeMath.signedRate(this.menu.lastIn()), this.menu.linkCount());
        Component idle = Component.translatable("gui.neropower.beam_splitter.idle");
        if (this.menu.readout() == 2 && this.menu.stallCause() == 11) {
            return Component.translatable(this.menu.linkCount() == 0 ? "gui.neropower.beam_splitter.no_links"
                    : "gui.neropower.beam_splitter.no_in");
        }
        if (this.menu.readout() == 2 && this.menu.stallCause() == 12) {
            return Component.translatable("gui.neropower.beam_splitter.all_blocked");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        int rh = rowHeight();
        if (GridScreenKit.in(mx, my, 8, 20, 10, 46)) {
            energyTooltip(lines);
            return;
        }
        if (GridScreenKit.in(mx, my, 24, 21, 130, 9)) {
            long in = this.menu.lastIn();
            int loss = this.menu.lensLossPermille();
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.in"));
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.in.last", GaugeMath.grouped(in)));
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.in.lens", GaugeTooltips.percentOf(loss),
                    Component.translatable("gui.neropower.lens." + this.menu.lens().name().toLowerCase(java.util.Locale.ROOT))));
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.in.left",
                    GaugeMath.grouped(in - SplitMath.lensLoss(in, loss))));
            return;
        }
        if (GridScreenKit.in(mx, my, 160, 20, 46, 18)) {
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.lens"));
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.lens.table",
                    GaugeTooltips.percentOf(this.menu.configuredLoss(SplitMath.Lens.QUARTZ)),
                    GaugeTooltips.percentOf(this.menu.configuredLoss(SplitMath.Lens.GLACITE)),
                    GaugeTooltips.percentOf(this.menu.configuredLoss(SplitMath.Lens.EMBER)),
                    GaugeTooltips.percentOf(this.menu.configuredLoss(SplitMath.Lens.VOID))));
            return;
        }
        if (GridScreenKit.in(mx, my, 150, 38, 56, 9)) {
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.free"));
            lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.free.line",
                    GaugeTooltips.percentOf(1000 - this.menu.totalShare())));
            return;
        }
        for (int i = 0; i < this.menu.linkCount(); i++) {
            int ry = ROWS_Y + i * rh;
            if (!GridScreenKit.in(mx, my, ROWS_X, ry, 182, rh)) {
                continue;
            }
            if (GridScreenKit.in(mx, my, BAR_X, ry, BAR_W, rh)) {
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.share", handle(i)));
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.share.line",
                        GaugeTooltips.percentOf(this.menu.share(i))));
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.share.how"));
            } else {
                long kept = this.menu.lastIn() - SplitMath.lensLoss(this.menu.lastIn(), this.menu.lensLossPermille());
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.link", handle(i)));
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.link.dist", this.menu.distance(i)));
                lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.link.gets",
                        GaugeTooltips.percentOf(this.menu.share(i)), GaugeMath.grouped(kept * this.menu.share(i) / 1000L)));
                if (this.menu.blocked(i)) {
                    lines.add(Component.translatable("gui.neropower.gauge.beam_splitter.link.blocked"));
                }
            }
            return;
        }
        if (GridScreenKit.in(mx, my, 210, 72, 36, 12)) {
            lines.add(this.selected >= 0
                    ? Component.translatable("gui.neropower.control.beam_splitter.unlink.tip", handle(this.selected))
                    : Component.translatable("gui.neropower.control.select_first"));
        } else if (GridScreenKit.in(mx, my, 8, 116, 232, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
        }
    }

    // --- input ------------------------------------------------------------------------------------

    private void send(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void setShare(int row, int permille) {
        int others = this.menu.totalShare() - this.menu.share(row);
        send(BeamSplitterMenu.shareButton(row, SplitMath.capShare(permille, others)));
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
        for (int i = 0; i < this.menu.linkCount(); i++) {
            int ry = ROWS_Y + i * rh;
            if (GridScreenKit.in(mx, my, ROWS_X, ry, 182, rh)) {
                this.selected = i;
                if (GridScreenKit.in(mx, my, BAR_X, ry, BAR_W, rh)) {
                    setShare(i, GridScreenKit.permilleAt(mx, BAR_X, BAR_W));
                }
                return true;
            }
        }
        if (GridScreenKit.in(mx, my, 210, 72, 36, 12) && this.selected >= 0) {
            send(MenuButtons.encode(BeamSplitterMenu.ACTION_UNLINK, this.selected));
            this.selected = -1;
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        int rh = rowHeight();
        for (int i = 0; i < this.menu.linkCount(); i++) {
            if (scrollY != 0 && GridScreenKit.in(mx, my, ROWS_X, ROWS_Y + i * rh, 182, rh)) {
                this.selected = i;
                setShare(i, this.menu.share(i) + (scrollY > 0 ? 10 : -10));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}

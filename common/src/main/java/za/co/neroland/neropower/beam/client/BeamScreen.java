package za.co.neroland.neropower.beam.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.beam.BeamMenu;
import za.co.neroland.neropower.beam.BeamStatus;
import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;

/**
 * The beam endpoint screen (shared by transmitter, receiver, relay and orbital receiver): the bare hull
 * plus a four-line readout in the machine area: link state, distance, loss and last-pass throughput, and
 * the {@link BeamStatus} line coloured by whether the beam is flowing. Everything comes from the menu's
 * synced ints (the last pass from the gauge tail, so large passes survive the short sync); hovering the
 * readout sums it up with the status. The screen never guesses.
 */
public class BeamScreen extends PowerMachineScreen<BeamMenu> {

    /** Flowing (green) / stalled (amber) / dark (grey) — the AnalyticsWidget status ramp's stops. */
    private static final int FLOWING = 0xFF3CB043;
    private static final int STALLED = 0xFFE0B33A;
    private static final int DARK = 0xFF8A94A6;
    private static final int LABEL = 0xFFD6ECFF;

    /** Readout origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 20;
    private static final int LINE_STRIDE = 11;
    private static final int TEXT_W = 96;

    public BeamScreen(BeamMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        BeamStatus status = this.menu.status();

        extractor.text(this.font, Component.translatable(this.menu.linked()
                ? "gui.neropower.beam.linked" : "gui.neropower.beam.unlinked"), x, y, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.beam.distance",
                this.menu.distance(), this.menu.lossPermille() / 10), x, y + LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.beam.last_pass",
                this.menu.gaugeExtra()), x, y + 2 * LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable(status.translationKey()), x, y + 3 * LINE_STRIDE,
                statusColour(status), false);
    }

    private static int statusColour(BeamStatus status) {
        return switch (status) {
            case TRANSMITTING, RECEIVING -> FLOWING;
            case IDLE, UNLINKED -> DARK;
            default -> STALLED;
        };
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (!overLines(mx, my, TEXT_X, TEXT_Y, LINE_STRIDE, 0, 3, TEXT_W)) {
            return;
        }
        lines.add(GaugeTooltips.tr("beam"));
        lines.add(GaugeTooltips.tr("beam.last_pass", GaugeMath.grouped(this.menu.gaugeExtra())));
        if (this.menu.distance() > 0) {
            lines.add(GaugeTooltips.tr("beam.loss", GaugeTooltips.percentOf(this.menu.lossPermille()),
                    this.menu.distance()));
        } else {
            lines.add(GaugeTooltips.tr("beam.loss_only", GaugeTooltips.percentOf(this.menu.lossPermille())));
        }
        lines.add(GaugeTooltips.tr("beam.status", Component.translatable(this.menu.status().translationKey())));
    }
}

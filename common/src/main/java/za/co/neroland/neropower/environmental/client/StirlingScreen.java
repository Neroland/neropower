package za.co.neroland.neropower.environmental.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.environmental.StirlingMenu;

/**
 * The Stirling Generator screen: the shared hull plus a three-line status block in the machine area: the
 * hottest neighbour's gradient, the heat drawn last tick, and whether a cold face is helping. All three
 * come straight from the menu's synced ints; hovering them gives the draw per tick and per second.
 */
public class StirlingScreen extends PowerMachineScreen<StirlingMenu> {

    /** Running (green) / cold face present (cyan) / idle or missing (subtle grey). */
    private static final int RUNNING = 0xFF3CB043;
    private static final int COLD = 0xFF55C2F0;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int LABEL = 0xFFD6ECFF;

    /** Status block origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 22;
    private static final int LINE_STRIDE = 11;
    private static final int TEXT_W = 96;

    public StirlingScreen(StirlingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        int gradient = this.menu.gradient();
        int drawn = this.menu.drawnLastTick();
        boolean cold = this.menu.hasColdFace();

        extractor.text(this.font, Component.translatable("gui.neropower.stirling.gradient", gradient),
                x, y, gradient > 0 ? LABEL : SUBTLE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.stirling.drawn", drawn),
                x, y + LINE_STRIDE, drawn > 0 ? RUNNING : SUBTLE, false);
        extractor.text(this.font, Component.translatable(cold
                        ? "gui.neropower.stirling.cold_face"
                        : "gui.neropower.stirling.no_cold_face"), x, y + 2 * LINE_STRIDE,
                cold ? COLD : SUBTLE, false);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (!overLines(mx, my, TEXT_X, TEXT_Y, LINE_STRIDE, 0, 2, TEXT_W)) {
            return;
        }
        int drawn = this.menu.drawnLastTick();
        lines.add(GaugeTooltips.tr("stirling"));
        lines.add(GaugeTooltips.tr("stirling.gradient", GaugeMath.grouped(this.menu.gradient())));
        if (drawn > 0) {
            lines.add(GaugeTooltips.tr("stirling.draw", GaugeMath.grouped(drawn),
                    GaugeMath.grouped((long) drawn * GaugeMath.TICKS_PER_SECOND)));
        } else {
            lines.add(GaugeTooltips.tr("stirling.idle"));
        }
        lines.add(GaugeTooltips.tr(this.menu.hasColdFace() ? "stirling.cold_face" : "stirling.no_cold_face"));
    }
}

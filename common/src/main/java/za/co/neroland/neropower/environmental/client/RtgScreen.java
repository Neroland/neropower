package za.co.neroland.neropower.environmental.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.environmental.EnvironmentalConfig;
import za.co.neroland.neropower.environmental.RtgMenu;

/**
 * The Radioisotope Generator screen: the shared hull plus a two-line decay readout under the pellet
 * slots: current output as a percentage of the fresh rate, and days elapsed / days until the pellet is
 * spent. Both come straight from the menu's synced ints; hovering them adds the time left.
 */
public class RtgScreen extends PowerMachineScreen<RtgMenu> {

    /** Healthy (green) / fading (amber) / no pellet (subtle grey). */
    private static final int HEALTHY = 0xFF3CB043;
    private static final int FADING = 0xFFE0B33A;
    private static final int SUBTLE = 0xFF8DA0B4;

    /** Readout origin: clear of the gauge column (x+8..30), under the slots (y+33..49). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 50;
    private static final int LINE_STRIDE = 9;
    private static final int TEXT_W = 96;

    public RtgScreen(RtgMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        int permille = this.menu.outputPermille();
        if (permille <= 0) {
            extractor.text(this.font, Component.translatable("gui.neropower.rtg.no_pellet"), x, y, SUBTLE, false);
            return;
        }
        int color = permille >= 500 ? HEALTHY : FADING;
        extractor.text(this.font, Component.translatable("gui.neropower.rtg.output", permille / 10),
                x, y, color, false);
        extractor.text(this.font, Component.translatable("gui.neropower.rtg.days",
                this.menu.elapsedDays(), this.menu.remainingDays()), x, y + LINE_STRIDE, SUBTLE, false);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (!overLines(mx, my, TEXT_X, TEXT_Y, LINE_STRIDE, 0, 1, TEXT_W)) {
            return;
        }
        int permille = this.menu.outputPermille();
        lines.add(GaugeTooltips.tr("rtg"));
        if (permille <= 0) {
            lines.add(GaugeTooltips.tr("rtg.no_pellet"));
            return;
        }
        lines.add(GaugeTooltips.tr("rtg.output", GaugeTooltips.percentOf(permille)));
        lines.add(GaugeTooltips.tr("rtg.days", this.menu.elapsedDays(), this.menu.remainingDays()));
        lines.add(GaugeTooltips.tr("rtg.time_left",
                GaugeMath.duration((long) this.menu.remainingDays() * EnvironmentalConfig.TICKS_PER_DAY)));
    }
}

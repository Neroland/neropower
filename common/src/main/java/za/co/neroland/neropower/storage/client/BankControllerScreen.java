package za.co.neroland.neropower.storage.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.storage.BankControllerMenu;
import za.co.neroland.neropower.storage.BankMode;

/**
 * The Battery Bank Controller screen: the shared hull plus a four-line status block in the machine area:
 * pooled cells, stored / capacity, effective I/O and the bank mode. All of it comes from the menu's synced
 * ints (the pool's amounts from the gauge tail, which syncs them in 15-bit halves), so the panel never
 * guesses. Hovering the first three lines gives the pool's fill, net charge rate and I/O limit. (The mode
 * is cycled at the block, sneak-use with an empty hand, not here.)
 */
public class BankControllerScreen extends PowerMachineScreen<BankControllerMenu> {

    private static final int LABEL = 0xFFD6ECFF;
    /** BUFFER white / PRIORITY_SOURCE amber (indexed by mode ordinal). */
    private static final int[] MODE_COLORS = {0xFFE6E6F0, 0xFFE0B33A};
    private static final int UNFORMED = 0xFF8DA0B4;

    /** Status block origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 20;
    private static final int LINE_STRIDE = 10;
    private static final int TEXT_W = 96;

    public BankControllerScreen(BankControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        int members = this.menu.memberCount();
        BankMode mode = this.menu.mode();
        int modeColor = MODE_COLORS[Math.min(mode.ordinal(), MODE_COLORS.length - 1)];

        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.cells", members),
                x, y, members > 0 ? LABEL : UNFORMED, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.stored",
                compact(this.menu.wideEnergyStored()), compact(this.menu.wideEnergyCapacity())),
                x, y + LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.io",
                compact(this.menu.gaugeExtra())), x, y + 2 * LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.mode",
                Component.translatable(mode.translationKey())), x, y + 3 * LINE_STRIDE, modeColor, false);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (!overLines(mx, my, TEXT_X, TEXT_Y, LINE_STRIDE, 0, 2, TEXT_W)) {
            return;
        }
        int members = this.menu.memberCount();
        lines.add(GaugeTooltips.tr("bank"));
        lines.add(GaugeTooltips.tr("bank.cells", members));
        if (members <= 0) {
            lines.add(GaugeTooltips.tr("bank.unformed"));
            return;
        }
        long stored = this.menu.wideEnergyStored();
        long capacity = this.menu.wideEnergyCapacity();
        lines.add(GaugeTooltips.tr("energy.amount", GaugeMath.compact(stored), GaugeMath.compact(capacity),
                GaugeMath.percent(stored, capacity)));
        lines.add(GaugeTooltips.tr("energy.net", GaugeMath.signedRate(energyNetPerTick())));
        lines.add(GaugeTooltips.tr("bank.io", GaugeMath.compact(this.menu.gaugeExtra())));
    }

    /** Short NE figures for a 96px-wide column: 999, 12.5k, 3.2M, 1.1G. */
    private static String compact(long value) {
        if (value < 1_000L) {
            return Long.toString(value);
        }
        if (value < 1_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", value / 1_000.0);
        }
        if (value < 1_000_000_000L) {
            return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        }
        return String.format(java.util.Locale.ROOT, "%.2fG", value / 1_000_000_000.0);
    }
}

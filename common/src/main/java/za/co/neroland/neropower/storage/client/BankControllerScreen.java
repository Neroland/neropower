package za.co.neroland.neropower.storage.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.storage.BankControllerMenu;
import za.co.neroland.neropower.storage.BankMode;

/**
 * The Battery Bank Controller screen: the shared {@link MachineScreen} hull plus a four-line status
 * block in the machine area — pooled cells, stored / capacity, effective I/O and the bank mode. All
 * of it comes straight from the menu's synced ints, so the panel never guesses. (The mode is cycled
 * at the block — sneak-use with an empty hand — not here.)
 */
public class BankControllerScreen extends MachineScreen<BankControllerMenu> {

    private static final int LABEL = 0xFFD6ECFF;
    /** BUFFER white / PRIORITY_SOURCE amber (indexed by mode ordinal). */
    private static final int[] MODE_COLORS = {0xFFE6E6F0, 0xFFE0B33A};
    private static final int UNFORMED = 0xFF8DA0B4;

    /** Status block origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 20;
    private static final int LINE_STRIDE = 10;

    public BankControllerScreen(BankControllerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        int members = this.menu.memberCount();
        BankMode mode = this.menu.mode();
        int modeColor = MODE_COLORS[Math.min(mode.ordinal(), MODE_COLORS.length - 1)];

        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.cells", members),
                x, y, members > 0 ? LABEL : UNFORMED, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.stored",
                compact(this.menu.pooledAmount()), compact(this.menu.pooledCapacity())),
                x, y + LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.io",
                compact(this.menu.effectiveIo())), x, y + 2 * LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.bank_controller.mode",
                Component.translatable(mode.translationKey())), x, y + 3 * LINE_STRIDE, modeColor, false);
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

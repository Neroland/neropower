package za.co.neroland.neropower.environmental.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.environmental.RtgMenu;

/**
 * The Radioisotope Generator screen: NeroTech's shared {@link MachineScreen} hull plus a two-line decay
 * readout under the pellet slots — current output as a percentage of the fresh rate, and days elapsed /
 * days until the pellet is spent. Both come straight from the menu's synced ints.
 */
public class RtgScreen extends MachineScreen<RtgMenu> {

    /** Healthy (green) / fading (amber) / no pellet (subtle grey). */
    private static final int HEALTHY = 0xFF3CB043;
    private static final int FADING = 0xFFE0B33A;
    private static final int SUBTLE = 0xFF8DA0B4;

    /** Readout origin: clear of the gauge column (x+8..30), under the slots (y+33..49). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 50;
    private static final int LINE_STRIDE = 9;

    public RtgScreen(RtgMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

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
}

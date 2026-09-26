package za.co.neroland.neropower.environmental.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.environmental.StirlingMenu;

/**
 * The Stirling Generator screen: NeroTech's shared {@link MachineScreen} hull plus a three-line status
 * block in the machine area — the hottest neighbour's gradient, the heat drawn last tick, and whether
 * a cold face is helping. All three come straight from the menu's synced ints.
 */
public class StirlingScreen extends MachineScreen<StirlingMenu> {

    /** Running (green) / cold face present (cyan) / idle or missing (subtle grey). */
    private static final int RUNNING = 0xFF3CB043;
    private static final int COLD = 0xFF55C2F0;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int LABEL = 0xFFD6ECFF;

    /** Status block origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 22;
    private static final int LINE_STRIDE = 11;

    public StirlingScreen(StirlingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

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
}

package za.co.neroland.neropower.beam.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.beam.BeamMenu;
import za.co.neroland.neropower.beam.BeamStatus;

/**
 * The beam endpoint screen (shared by transmitter, receiver, relay and orbital receiver): the
 * bare {@link MachineScreen} hull plus a four-line readout in the machine area — link state,
 * distance, loss and last-pass throughput, and the {@link BeamStatus} line coloured by whether the
 * beam is flowing. Everything comes from the menu's synced ints; the screen never guesses.
 */
public class BeamScreen extends MachineScreen<BeamMenu> {

    /** Flowing (green) / stalled (amber) / dark (grey) — the AnalyticsWidget status ramp's stops. */
    private static final int FLOWING = 0xFF3CB043;
    private static final int STALLED = 0xFFE0B33A;
    private static final int DARK = 0xFF8A94A6;
    private static final int LABEL = 0xFFD6ECFF;

    /** Readout origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 20;
    private static final int LINE_STRIDE = 11;

    public BeamScreen(BeamMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;
        BeamStatus status = this.menu.status();
        int statusColour = switch (status) {
            case TRANSMITTING, RECEIVING -> FLOWING;
            case IDLE, UNLINKED -> DARK;
            default -> STALLED;
        };

        extractor.text(this.font, Component.translatable(this.menu.linked()
                ? "gui.neropower.beam.linked" : "gui.neropower.beam.unlinked"), x, y, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.beam.distance",
                this.menu.distance(), this.menu.lossPermille() / 10), x, y + LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable("gui.neropower.beam.last_pass",
                this.menu.lastPass()), x, y + 2 * LINE_STRIDE, LABEL, false);
        extractor.text(this.font, Component.translatable(status.translationKey()), x, y + 3 * LINE_STRIDE,
                statusColour, false);
    }
}

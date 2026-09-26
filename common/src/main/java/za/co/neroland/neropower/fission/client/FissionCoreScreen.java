package za.co.neroland.neropower.fission.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreMenu;

/**
 * The Fission Core screen: NeroTech's shared {@link MachineScreen} hull plus three status lines
 * under the rod row — each rod's burn-up, the shell / control-rod readout, and poison with the
 * failure stage — all straight from the menu's synced ints, so the panel never guesses. Rod slots
 * the current shell cannot use are shaded over.
 */
public class FissionCoreScreen extends MachineScreen<FissionCoreMenu> {

    private static final int LABEL = 0xFFD6ECFF;
    private static final int NOMINAL = 0xFF3CB043;
    private static final int WARNING = 0xFFE0B33A;
    private static final int DANGER = 0xFFE0543A;
    private static final int LOCKED = 0xA0050A10;

    /** Status block origin, clear of the gauge column (x+8..30) and the upgrade block (x+138). */
    private static final int TEXT_X = 40;
    private static final int TEXT_Y = 38;
    private static final int LINE_STRIDE = 8;

    public FissionCoreScreen(FissionCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);

        // Shade the rod slots the shell cannot use (they refuse every stack anyway).
        int usable = this.menu.usableRodSlots();
        for (int slot = usable; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
            Slot s = this.menu.slots.get(slot);
            int sx = this.leftPos + s.x;
            int sy = this.topPos + s.y;
            extractor.fill(sx, sy, sx + 16, sy + 16, LOCKED);
        }

        int x = this.leftPos + TEXT_X;
        int y = this.topPos + TEXT_Y;

        extractor.text(this.font, Component.translatable("gui.neropower.fission_core.burnup",
                percent(0), percent(1), percent(2), percent(3)), x, y, LABEL, false);

        int shell = this.menu.shellSize();
        Component shellLine = shell > 0
                ? Component.translatable("gui.neropower.fission_core.shell", shell, usable,
                        this.menu.controlRodPermille() / 10)
                : Component.translatable("gui.neropower.fission_core.unformed");
        extractor.text(this.font, shellLine, x, y + LINE_STRIDE, shell > 0 ? LABEL : WARNING, false);

        FailureStage stage = FailureStage.byCode(this.menu.failureStageCode());
        int stageColor = switch (stage) {
            case STABLE -> NOMINAL;
            case WARNING -> WARNING;
            case UNSTABLE, FAILURE -> DANGER;
        };
        extractor.text(this.font, Component.translatable("gui.neropower.fission_core.poison",
                this.menu.poison() / 10, Component.translatable(stage.translationKey())),
                x, y + 2 * LINE_STRIDE, stageColor, false);
    }

    /** A rod slot's burn-up as a percent string, or a dash when the slot is empty. */
    private Component percent(int slot) {
        if (slot >= this.menu.usableRodSlots()) {
            return Component.translatable("gui.neropower.fission_core.locked");
        }
        int burnup = this.menu.rodBurnup(slot);
        if (burnup <= 0 && this.menu.slots.get(slot).getItem().isEmpty()) {
            return Component.translatable("gui.neropower.fission_core.empty");
        }
        return Component.literal(burnup / 10 + "%");
    }
}

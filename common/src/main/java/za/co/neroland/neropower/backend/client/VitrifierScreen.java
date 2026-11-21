package za.co.neroland.neropower.backend.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.client.MachineScreen;
import za.co.neroland.nerotech.config.NeroTechConfig;

import za.co.neroland.neropower.backend.BackEndReadout;
import za.co.neroland.neropower.backend.CaskMath;
import za.co.neroland.neropower.backend.VitrifierBlockEntity;
import za.co.neroland.neropower.backend.VitrifierMenu;
import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;

/**
 * Vitrifier screen (spec {@code screens/vitrifier.md}): tall 176 × 186 hull, NeroTech's energy and
 * heat pair, the waste and sand slots, a mould that fills with the progress (answered by NeroTech's
 * progress tooltip), the output slot, the cost line and the stall line. Empty input slots explain what
 * goes in them.
 */
public class VitrifierScreen extends MachineScreen<VitrifierMenu> {

    private static final int MOULD_X = 74;
    private static final int MOULD_Y = 26;
    private static final int MOULD_W = 24;
    private static final int MOULD_H = 30;
    private static final int MOULD_EDGE = 0xFF2A3542;
    private static final int MOULD_FILL = 0xFFCC7012;
    private static final int MOULD_SURFACE = 0xFFFFE2A0;

    private static final int COST_X = 34;
    private static final int COST_Y = 69;
    private static final int STALL_Y = VitrifierMenu.HEIGHT - 106;

    public VitrifierScreen(VitrifierMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, VitrifierMenu.WIDTH, VitrifierMenu.HEIGHT);
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    @Override
    protected long energyUsedPerTick() {
        return this.menu.energyRate();
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;

        // Mould: an open-topped outline, filled bottom-up to the progress, a surface line while working.
        int mx = x + MOULD_X;
        int my = y + MOULD_Y;
        extractor.fill(mx, my, mx + 1, my + MOULD_H, MOULD_EDGE);
        extractor.fill(mx + MOULD_W - 1, my, mx + MOULD_W, my + MOULD_H, MOULD_EDGE);
        extractor.fill(mx, my + MOULD_H - 1, mx + MOULD_W, my + MOULD_H, MOULD_EDGE);
        int inner = MOULD_H - 2;
        float frac = this.menu.readout() == BackEndReadout.REST ? 0F
                : Math.max(0F, Math.min(1F, this.menu.workFraction()));
        if (this.menu.readout() == BackEndReadout.STALL && this.menu.stallCause() == BackEndReadout.CAUSE_OUTPUT_FULL) {
            frac = 1F;
        }
        int fh = Math.round(inner * frac);
        if (fh > 0) {
            extractor.fill(mx + 1, my + MOULD_H - 1 - fh, mx + MOULD_W - 1, my + MOULD_H - 1, MOULD_FILL);
        }
        if (this.menu.readout() == BackEndReadout.WORK && fh > 0) {
            int sy = my + MOULD_H - 1 - fh;
            extractor.fill(mx + 1, sy, mx + MOULD_W - 1, sy + 2, MOULD_SURFACE);
        }

        extractor.text(this.font, Component.translatable("gui.neropower.vitrifier.cost", this.menu.nePerTick(),
                this.menu.ticksPerOp()), x + COST_X, y + COST_Y, BackEndHud.SUBTLE, false);
        BackEndHud.stallLine(extractor, this.font, x + 8, y + STALL_Y, stallText(), this.menu.readout(),
                gaugeTick(), NeroTechConfig.renderAnimationsEnabled());
    }

    private long ticksLeft() {
        int permille = Math.round(this.menu.workFraction() * 1000F);
        return (long) (1000 - Math.max(0, Math.min(1000, permille))) * Math.max(0, this.menu.ticksPerOp()) / 1000L;
    }

    private Component stallText() {
        return BackEndHud.stallText(this.menu.readout(), this.menu.stallCause(),
                Component.translatable("gui.neropower.vitrifier.running", GaugeMath.duration(ticksLeft())),
                Component.translatable("gui.neropower.stall.idle"));
    }

    @Override
    protected List<Component> gaugeTooltip(int mx, int my) {
        List<Component> lines = super.gaugeTooltip(mx, my);
        if (!lines.isEmpty()) {
            return lines;
        }
        if (BackEndHud.in(mx, my, MOULD_X, MOULD_Y, MOULD_W, MOULD_H)) {
            progressTooltip(lines);
            return lines;
        }
        if (emptySlotAt(mx, my, VitrifierMenu.WASTE_X, VitrifierMenu.WASTE_Y, VitrifierBlockEntity.WASTE_SLOT)) {
            lines.add(Component.translatable("gui.neropower.vitrifier.slot.waste"));
            return lines;
        }
        if (emptySlotAt(mx, my, VitrifierMenu.SAND_X, VitrifierMenu.SAND_Y, VitrifierBlockEntity.SAND_SLOT)) {
            lines.add(Component.translatable("gui.neropower.vitrifier.slot.sand", CaskMath.SAND_PER_OP));
            return lines;
        }
        if (BackEndHud.in(mx, my, COST_X, COST_Y, 134, BackEndHud.ROW_H)) {
            int ne = this.menu.nePerTick();
            int ticks = this.menu.ticksPerOp();
            lines.add(GaugeTooltips.tr("vitrifier"));
            lines.add(GaugeTooltips.tr("vitrifier.cost", ne, ticks, GaugeMath.grouped(CaskMath.vitrifyCost(ne, ticks))));
            lines.add(GaugeTooltips.tr("vitrifier.inert"));
            return lines;
        }
        if (BackEndHud.in(mx, my, 8, STALL_Y, 160, BackEndHud.ROW_H)) {
            BackEndHud.stallTooltip(lines, stallText(), this.menu.readout(), this.menu.stallCause());
        }
        return lines;
    }

    private boolean emptySlotAt(int mx, int my, int sx, int sy, int index) {
        if (!BackEndHud.in(mx, my, sx, sy, 16, 16)) {
            return false;
        }
        Slot slot = this.menu.getSlot(index);
        return !slot.hasItem();
    }
}

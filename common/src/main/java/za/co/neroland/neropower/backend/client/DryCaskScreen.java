package za.co.neroland.neropower.backend.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.nerotech.client.MachineScreen;
import za.co.neroland.nerotech.config.NeroTechConfig;

import za.co.neroland.neropower.backend.CaskMath;
import za.co.neroland.neropower.backend.DryCaskMenu;
import za.co.neroland.neropower.client.GaugeTooltips;

/**
 * Dry Cask screen (spec {@code screens/dry_cask.md}): tall 176 × 222 hull, the 27-slot store at the
 * top, a solo heat bar below it (NeroTech's heat tooltip), the shielding chip, the stored and
 * trace-heat lines, the upgrade row and preset pill moved beside them, and the stall line. Empty store
 * slots say hot rods are refused; empty upgrade slots say what they take.
 */
public class DryCaskScreen extends MachineScreen<DryCaskMenu> {

    private static final int HEAT_X = 8;
    private static final int HEAT_Y = 78;
    private static final int GAUGE_W = 10;
    private static final int GAUGE_H = 46;

    private static final int CHIP_X = 24;
    private static final int CHIP_Y = 78;
    private static final int STORED_Y = 92;
    private static final int TRACE_Y = 103;
    private static final int STALL_Y = DryCaskMenu.HEIGHT - 106;

    /** Last drawn chip width, for its hover rectangle. */
    private int chipW = 74;

    public DryCaskScreen(DryCaskMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, DryCaskMenu.WIDTH, DryCaskMenu.HEIGHT);
    }

    @Override
    protected boolean drawsDefaultGauges() {
        return false;
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    @Override
    protected int presetX() {
        return 134;
    }

    @Override
    protected int presetY() {
        return 100;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        gauge(extractor, x + HEAT_X, y + HEAT_Y, GAUGE_W, GAUGE_H, this.menu.heatFraction(), BackEndHud.HEAT);

        boolean shielded = !this.menu.leaking();
        this.chipW = BackEndHud.chip(extractor, this.font, x + CHIP_X, y + CHIP_Y,
                Component.translatable(shielded ? "gui.neropower.dry_cask.shielded" : "gui.neropower.dry_cask.unshielded"),
                shielded ? BackEndHud.OK : BackEndHud.WARN, shielded);
        extractor.text(this.font, Component.translatable("gui.neropower.dry_cask.stored", this.menu.occupied()),
                x + CHIP_X, y + STORED_Y, BackEndHud.TITLE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.dry_cask.trace", this.menu.traceHeat(),
                this.menu.heatInterval()), x + CHIP_X, y + TRACE_Y, BackEndHud.SUBTLE, false);
        BackEndHud.stallLine(extractor, this.font, x + 8, y + STALL_Y, stallText(), this.menu.readout(),
                gaugeTick(), NeroTechConfig.renderAnimationsEnabled());
    }

    private Component stallText() {
        Component idle = this.menu.leaking()
                ? Component.translatable("gui.neropower.dry_cask.leaking", 6 - this.menu.shieldedFaces())
                : Component.translatable("gui.neropower.dry_cask.sealed");
        return BackEndHud.stallText(this.menu.readout(), this.menu.stallCause(), idle, idle);
    }

    @Override
    protected List<Component> gaugeTooltip(int mx, int my) {
        List<Component> lines = super.gaugeTooltip(mx, my);
        if (!lines.isEmpty()) {
            return lines;
        }
        if (over(mx, my, HEAT_X, HEAT_Y, GAUGE_W, GAUGE_H)) {
            heatTooltip(lines);
            return lines;
        }
        if (BackEndHud.in(mx, my, CHIP_X, CHIP_Y, this.chipW, 11)) {
            lines.add(GaugeTooltips.tr("cask.shielding"));
            lines.add(GaugeTooltips.tr("cask.shielding.faces", this.menu.shieldedFaces()));
            lines.add(GaugeTooltips.tr(this.menu.radiationOn() ? "cask.shielding.open" : "cask.shielding.off"));
            return lines;
        }
        if (BackEndHud.in(mx, my, CHIP_X, STORED_Y, 74, BackEndHud.ROW_H)) {
            lines.add(GaugeTooltips.tr("cask.stored"));
            lines.add(GaugeTooltips.tr("cask.stored.slots", this.menu.occupied(), CaskMath.CASK_SLOTS));
            lines.add(GaugeTooltips.tr("cask.stored.what"));
            return lines;
        }
        if (BackEndHud.in(mx, my, CHIP_X, TRACE_Y, 106, BackEndHud.ROW_H)) {
            lines.add(GaugeTooltips.tr("cask.trace"));
            lines.add(GaugeTooltips.tr("cask.trace.rate", this.menu.traceHeat(), this.menu.heatInterval(),
                    this.menu.radioactive()));
            return lines;
        }
        if (BackEndHud.in(mx, my, 8, STALL_Y, 160, BackEndHud.ROW_H)) {
            BackEndHud.stallTooltip(lines, stallText(), this.menu.readout(), this.menu.stallCause());
            return lines;
        }
        Slot empty = emptySlotAt(mx, my);
        if (empty != null) {
            lines.add(Component.translatable(empty.index < CaskMath.CASK_SLOTS
                    ? "gui.neropower.dry_cask.slot.store" : "gui.neropower.dry_cask.slot.upgrade"));
        }
        return lines;
    }

    /** The empty machine or upgrade slot under a panel-relative point, if any (player slots excluded). */
    private Slot emptySlotAt(int mx, int my) {
        int machineAndUpgrades = CaskMath.CASK_SLOTS + 4;
        for (int i = 0; i < machineAndUpgrades && i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.hasItem() && BackEndHud.in(mx, my, slot.x, slot.y, 16, 16)) {
                return slot;
            }
        }
        return null;
    }
}

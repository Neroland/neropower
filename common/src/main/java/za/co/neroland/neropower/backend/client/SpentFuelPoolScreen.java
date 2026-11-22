package za.co.neroland.neropower.backend.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.api.MachineSettings;
import za.co.neroland.nerotech.client.MachineScreen;

import za.co.neroland.neropower.backend.PoolMath;
import za.co.neroland.neropower.backend.SpentFuelPoolMenu;
import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.failure.FailureStage;

/**
 * Spent Fuel Rack screen: tall 176 × 206 hull, a solo heat
 * bar (NeroTech's heat tooltip), the four rod slots in a 2 × 2 block, each with a cooling ring, the
 * water column (pips, count, net heat), the stage block and the stall line. NeroTech's screen stays the
 * single tooltip emitter; this screen only answers {@link #gaugeTooltip} for its own parts.
 */
public class SpentFuelPoolScreen extends MachineScreen<SpentFuelPoolMenu> {

    private static final int HEAT_X = 8;
    private static final int GAUGE_Y = 20;
    private static final int GAUGE_W = 10;
    private static final int GAUGE_H = 46;

    private static final int COL_X = 96;
    private static final int COL_W = 38;
    private static final int STAGE_X = 8;
    private static final int STAGE_Y = 74;
    private static final int STALL_Y = SpentFuelPoolMenu.HEIGHT - 106;

    public SpentFuelPoolScreen(SpentFuelPoolMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SpentFuelPoolMenu.WIDTH, SpentFuelPoolMenu.HEIGHT);
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
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        gauge(extractor, x + HEAT_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, this.menu.heatFraction(), BackEndHud.HEAT);

        boolean paused = this.menu.stageCode() > 0;
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            int rx = x + SpentFuelPoolMenu.SLOT_X[i] - 3;
            int ry = y + SpentFuelPoolMenu.SLOT_Y[i] - 3;
            int state = this.menu.rodState(i);
            if (state == 2) {
                BackEndHud.slotRing(extractor, rx, ry, 1F, BackEndHud.OK, false);
                int tx = x + SpentFuelPoolMenu.SLOT_X[i] + 11;
                int ty = y + SpentFuelPoolMenu.SLOT_Y[i];
                extractor.fill(tx, ty, tx + 5, ty + 5, BackEndHud.OK);
            } else {
                float frac = state == 1 ? this.menu.coolingPermille(i) / 1000F : 0F;
                BackEndHud.slotRing(extractor, rx, ry, frac, BackEndHud.WATER, state == 1 && paused);
            }
        }

        int contacts = this.menu.waterContacts();
        extractor.text(this.font, Component.translatable("gui.neropower.spent_fuel_pool.water"),
                x + COL_X, y + 21, BackEndHud.SUBTLE, false);
        BackEndHud.pips(extractor, x + COL_X, y + 32, PoolMath.MAX_CONTACTS, contacts, BackEndHud.WATER);
        extractor.text(this.font, Component.translatable("gui.neropower.spent_fuel_pool.contacts", contacts),
                x + COL_X, y + 42, contacts > 0 ? BackEndHud.TITLE : BackEndHud.HOT, false);
        int net = netHeat();
        extractor.text(this.font, Component.translatable("gui.neropower.spent_fuel_pool.net",
                (net > 0 ? "+" : "") + net), x + COL_X, y + 54, net > 0 ? BackEndHud.HOT : BackEndHud.TITLE, false);

        BackEndHud.stageBlock(extractor, this.font, x + STAGE_X, y + STAGE_Y, this.menu.stageCode());
        BackEndHud.stallLine(extractor, this.font, x + 8, y + STALL_Y, stallText(), this.menu.readout(),
                gaugeTick(), MachineSettings.renderAnimationsEnabled());
    }

    /** Rods' decay heat minus the water's share, per tick (signed). */
    private int netHeat() {
        return this.menu.hotRods() * this.menu.rodHeatPerTick() - heatToWater();
    }

    private int heatToWater() {
        return PoolMath.heatOut(this.menu.waterContacts(), this.menu.coolingPerContact());
    }

    private Component stallText() {
        int hot = this.menu.hotRods();
        Component idle = Component.translatable(anyRod() ? "gui.neropower.spent_fuel_pool.all_cooled"
                : "gui.neropower.spent_fuel_pool.empty");
        if (this.menu.readout() == za.co.neroland.neropower.backend.BackEndReadout.HOT) {
            // Rods do not cool while the rack is hot: say the timer stopped, not just "running hot".
            return Component.translatable("gui.neropower.spent_fuel_pool.paused");
        }
        return BackEndHud.stallText(this.menu.readout(), this.menu.stallCause(),
                Component.translatable("gui.neropower.spent_fuel_pool.running", hot), idle);
    }

    private boolean anyRod() {
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (this.menu.rodState(i) != 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Component> gaugeTooltip(int mx, int my) {
        List<Component> lines = super.gaugeTooltip(mx, my);
        if (!lines.isEmpty()) {
            return lines;
        }
        if (over(mx, my, HEAT_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            heatTooltip(lines);
            return lines;
        }
        for (int i = 0; i < PoolMath.SLOTS; i++) {
            if (BackEndHud.in(mx, my, SpentFuelPoolMenu.SLOT_X[i] - 3, SpentFuelPoolMenu.SLOT_Y[i] - 3, 22, 22)) {
                rodTooltip(lines, i);
                return lines;
            }
        }
        if (BackEndHud.in(mx, my, COL_X, 21, COL_W, 30)) {
            lines.add(GaugeTooltips.tr("pool.water"));
            lines.add(GaugeTooltips.tr("pool.water.faces", this.menu.waterContacts()));
            lines.add(GaugeTooltips.tr("pool.water.rates", this.menu.coolingPerContact(), this.menu.rodHeatPerTick()));
            return lines;
        }
        if (BackEndHud.in(mx, my, COL_X, 54, COL_W, BackEndHud.ROW_H)) {
            lines.add(GaugeTooltips.tr("pool.balance"));
            lines.add(GaugeTooltips.tr("pool.balance.rates", this.menu.hotRods() * this.menu.rodHeatPerTick(),
                    heatToWater()));
            return lines;
        }
        if (BackEndHud.in(mx, my, STAGE_X, STAGE_Y, 96, 17)) {
            lines.add(GaugeTooltips.tr("stage"));
            lines.add(Component.translatable(FailureStage.byCode(this.menu.stageCode()).translationKey()));
            lines.add(GaugeTooltips.tr("pool.stage.heat", GaugeTooltips.percentOf(this.menu.stageHeat()),
                    GaugeTooltips.percentOf(this.menu.stageNext())));
            lines.add(GaugeTooltips.tr(this.menu.radiationOn() ? "pool.stage.failure_radiation" : "pool.stage.failure"));
            return lines;
        }
        if (BackEndHud.in(mx, my, 8, STALL_Y, 160, BackEndHud.ROW_H)) {
            BackEndHud.stallTooltip(lines, stallText(), this.menu.readout(), this.menu.stallCause());
        }
        return lines;
    }

    private void rodTooltip(List<Component> lines, int slot) {
        lines.add(GaugeTooltips.tr("pool.rod", slot + 1));
        switch (this.menu.rodState(slot)) {
            case 1 -> {
                lines.add(GaugeTooltips.tr("pool.rod.cooling", GaugeTooltips.percentOf(this.menu.coolingPermille(slot))));
                lines.add(this.menu.stageCode() > 0 ? GaugeTooltips.tr("pool.rod.paused")
                        : GaugeTooltips.tr("pool.rod.left", GaugeMath.duration(this.menu.coolTicksLeft(slot))));
            }
            case 2 -> lines.add(GaugeTooltips.tr("pool.rod.cooled"));
            default -> lines.add(GaugeTooltips.tr("pool.rod.empty"));
        }
    }

    /** The Guide button (opens the Power Guide at this block's chapter). */
    @Override
    protected void init() {
        super.init();
        addRenderableWidget(za.co.neroland.neropower.client.guide.PowerGuideLinks.button(this, this.menu,
                this.leftPos, this.topPos, this.imageWidth));
    }
}

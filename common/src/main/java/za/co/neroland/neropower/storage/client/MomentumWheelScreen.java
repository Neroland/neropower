package za.co.neroland.neropower.storage.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.storage.MomentumWheelMenu;

/**
 * Momentum Wheel screen (spec {@code momentum_wheel}): tall 176 x 186 hull, no default gauges; the ring gauge
 * is the energy gauge (inherited energy tooltip, wide values), with stored / in / out lines, the spin-down
 * loss and the stall line.
 */
public class MomentumWheelScreen extends PowerMachineScreen<MomentumWheelMenu> {

    public MomentumWheelScreen(MomentumWheelMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 186);
    }

    @Override
    protected boolean drawsDefaultGauges() {
        return false;
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    private int permille() {
        long capacity = this.menu.wideEnergyCapacity();
        return capacity <= 0 ? 0 : (int) Math.min(1000L, this.menu.wideEnergyStored() * 1000L / capacity);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        StorageScreenKit.ring(extractor, this.font, x + 8, y + 20, permille(), StorageScreenKit.ENERGY);
        extractor.text(this.font, Component.translatable("gui.neropower.momentum_wheel.stored",
                GaugeMath.compact(this.menu.wideEnergyStored())), x + 60, y + 21, StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.momentum_wheel.of",
                GaugeMath.compact(this.menu.wideEnergyCapacity())), x + 60, y + 31, StorageScreenKit.SUBTLE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.momentum_wheel.in",
                GaugeMath.signedRate(this.menu.inPerTick())), x + 60, y + 43, StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.momentum_wheel.out",
                GaugeMath.signedRate(-this.menu.outPerTick())), x + 60, y + 53, StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.momentum_wheel.loss",
                GaugeMath.compact(this.menu.lossPerSecond()), GaugeTooltips.percentOf(this.menu.lossPermillePerSecond())),
                x + 8, y + 69, StorageScreenKit.SUBTLE, false);
        extractor.text(this.font, stall(), x + 8, y + 80, StorageScreenKit.SUBTLE, false);
    }

    private Component stall() {
        long net = this.menu.inPerTick() - this.menu.outPerTick();
        Component running = net >= 0
                ? Component.translatable("gui.neropower.momentum_wheel.spinning_up", GaugeMath.signedRate(net))
                : Component.translatable("gui.neropower.momentum_wheel.spinning_down", GaugeMath.signedRate(net));
        Component idle = Component.translatable(this.menu.wideEnergyStored() > 0
                ? "gui.neropower.momentum_wheel.coasting" : "gui.neropower.momentum_wheel.stopped");
        if (this.menu.readout() == 2 && this.menu.stallCause() == 8) {
            return Component.translatable("gui.neropower.momentum_wheel.full");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (over(mx, my, 8, 20, 46, 46) || over(mx, my, 60, 21, 74, 19)) {
            energyTooltip(lines);
        } else if (over(mx, my, 60, 43, 74, 19)) {
            lines.add(GaugeTooltips.tr("flow"));
            lines.add(GaugeTooltips.tr("flow.in_out", GaugeMath.compact(this.menu.inPerTick()),
                    GaugeMath.compact(this.menu.outPerTick())));
            lines.add(GaugeTooltips.tr("flow.limit", GaugeMath.compact(this.menu.ioRate())));
        } else if (over(mx, my, 8, 69, 160, 9)) {
            long loss = this.menu.lossPerSecond();
            lines.add(Component.translatable("gui.neropower.momentum_wheel.loss.title"));
            lines.add(Component.translatable("gui.neropower.momentum_wheel.loss.share",
                    GaugeTooltips.percentOf(this.menu.lossPermillePerSecond())));
            lines.add(Component.translatable("gui.neropower.momentum_wheel.loss.now", GaugeMath.compact(loss)));
        } else if (over(mx, my, 8, 80, 160, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
        }
    }
}

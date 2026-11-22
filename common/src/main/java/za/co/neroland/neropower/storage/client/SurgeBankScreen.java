package za.co.neroland.neropower.storage.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.storage.ChargeSlot;
import za.co.neroland.neropower.storage.PoolMath;
import za.co.neroland.neropower.storage.SurgeBankBlock;
import za.co.neroland.neropower.storage.SurgeBankMenu;

/**
 * Surge Bank screen (spec {@code surge_bank}): tall 176 x 186 hull, a solo energy bar (the inherited energy
 * tooltip, wide values), six can pips, stored / in / out lines, the bank line and the stall line. Every
 * number comes from the menu's synced values.
 */
public class SurgeBankScreen extends PowerMachineScreen<SurgeBankMenu> {

    public SurgeBankScreen(SurgeBankMenu menu, Inventory playerInventory, Component title) {
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

    private int cans() {
        return PoolMath.bucket(this.menu.wideEnergyStored(), this.menu.wideEnergyCapacity(), SurgeBankBlock.CAN_LEVELS);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        long stored = this.menu.wideEnergyStored();
        long capacity = this.menu.wideEnergyCapacity();
        int permille = capacity <= 0 ? 0 : (int) Math.min(1000L, stored * 1000L / capacity);
        StorageScreenKit.bar(extractor, x + 8, y + 20, 10, 46, permille, StorageScreenKit.ENERGY);
        int cans = cans();
        StorageScreenKit.pips(extractor, x + 34, y + 22, SurgeBankBlock.CAN_LEVELS, cans, StorageScreenKit.CAN);
        extractor.text(this.font, Component.literal(cans + "/" + SurgeBankBlock.CAN_LEVELS), x + 84, y + 23,
                StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.surge_bank.stored",
                GaugeMath.compact(stored), GaugeMath.compact(capacity)), x + 34, y + 38, StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.surge_bank.in",
                GaugeMath.signedRate(this.menu.inPerTick())), x + 34, y + 48, StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable("gui.neropower.surge_bank.out",
                GaugeMath.signedRate(-this.menu.outPerTick())), x + 34, y + 58, StorageScreenKit.VALUE, false);
        Component bank = this.menu.pooled()
                ? Component.translatable("gui.neropower.surge_bank.pooled", this.menu.bankMembers())
                : Component.translatable("gui.neropower.surge_bank.alone");
        extractor.text(this.font, bank, x + 8, y + 69, StorageScreenKit.SUBTLE, false);
        extractor.text(this.font, stall(), x + 8, y + 80, StorageScreenKit.SUBTLE, false);
    }

    private Component stall() {
        long net = this.menu.inPerTick() - this.menu.outPerTick();
        Component running = net >= 0
                ? Component.translatable("gui.neropower.surge_bank.charging", GaugeMath.signedRate(net))
                : Component.translatable("gui.neropower.surge_bank.discharging", GaugeMath.signedRate(net));
        Component idle = Component.translatable("gui.neropower.surge_bank.holding");
        if (this.menu.readout() == 2 && this.menu.stallCause() == 9) {
            return Component.translatable("gui.neropower.surge_bank.empty");
        }
        if (this.menu.readout() == 2 && this.menu.stallCause() == 8) {
            return Component.translatable("gui.neropower.surge_bank.full");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (over(mx, my, 8, 20, 10, 46) || over(mx, my, 34, 38, 100, 9)) {
            energyTooltip(lines);
        } else if (over(mx, my, 34, 22, 74, 10)) {
            lines.add(Component.translatable("gui.neropower.surge_bank.cans"));
            lines.add(Component.translatable("gui.neropower.surge_bank.cans.detail", cans()));
        } else if (over(mx, my, 34, 48, 100, 19)) {
            lines.add(GaugeTooltips.tr("flow"));
            lines.add(GaugeTooltips.tr("flow.in_out", GaugeMath.compact(this.menu.inPerTick()),
                    GaugeMath.compact(this.menu.outPerTick())));
            lines.add(GaugeTooltips.tr("flow.limit", GaugeMath.compact(this.menu.ioRate())));
        } else if (over(mx, my, 116, 20, 16, 16) && this.menu.getSlot(ChargeSlot.SLOT).getItem().isEmpty()) {
            lines.add(Component.translatable("gui.neropower.surge_bank.charge_slot"));
        } else if (over(mx, my, 8, 69, 160, 9)) {
            lines.add(Component.translatable("gui.neropower.surge_bank.bank"));
            lines.add(Component.translatable(this.menu.pooled()
                    ? "gui.neropower.surge_bank.bank.pooled" : "gui.neropower.surge_bank.bank.alone"));
        } else if (over(mx, my, 8, 80, 160, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
        }
    }
}

package za.co.neroland.neropower.grid.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.grid.LoadBankMenu;
import za.co.neroland.neropower.storage.client.StorageScreenKit;

/**
 * Load Bank screen (tall hull 176 × 186, screens/load_bank.md): solo heat bar (NeroTech's heat tooltip),
 * the set rate (slider in 100 NE/t steps; scroll 100 a notch), Zero, what it is drawing, the heat
 * it makes and the stall line.
 */
public class LoadBankScreen extends PowerMachineScreen<LoadBankMenu> {

    public LoadBankScreen(LoadBankMenu menu, Inventory playerInventory, Component title) {
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

    private int heatPermille() {
        long cap = this.menu.heatCapacityRaw();
        return cap <= 0 ? 0 : (int) Math.min(1000L, this.menu.heatRaw() * 1000L / cap);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        int mx = mouseX - x;
        int my = mouseY - y;
        StorageScreenKit.bar(g, x + 8, y + 20, 10, 46, heatPermille(), StorageScreenKit.WARMTH);
        g.text(this.font, Component.translatable("gui.neropower.load_bank.set"), x + 24, y + 20, GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.load_bank.value", GaugeMath.grouped(this.menu.rateSet())),
                x + 24, y + 32, GridScreenKit.VALUE, false);
        GridScreenKit.button(g, this.font, x, y, 98, 30, 36, 12, Component.translatable("gui.neropower.control.load_bank.zero"),
                this.menu.rateSet() > 0, GridScreenKit.in(mx, my, 98, 30, 36, 12));
        long max = Math.max(1L, this.menu.maxRate());
        GridScreenKit.slider(g, x, y, 24, 48, 110, (int) (this.menu.rateSet() * 1000L / max), GridScreenKit.SHARE);
        boolean shortOf = this.menu.rateDrawn() < this.menu.rateSet();
        g.text(this.font, Component.translatable("gui.neropower.load_bank.drawing", GaugeMath.signedRate(this.menu.rateDrawn())),
                x + 24, y + 58, shortOf ? GridScreenKit.AMBER : GridScreenKit.VALUE, false);
        g.text(this.font, Component.translatable("gui.neropower.load_bank.heat", heatText()), x + 8, y + 69,
                GridScreenKit.VALUE, false);
        g.text(this.font, stall(), x + 8, y + 80, GridScreenKit.SUBTLE, false);
    }

    private String heatText() {
        int tenths = this.menu.heatTenthsPerTick();
        return "+" + (tenths / 10) + "." + (tenths % 10);
    }

    private Component stall() {
        Component idle = Component.translatable("gui.neropower.load_bank.idle");
        Component running = Component.translatable("gui.neropower.load_bank.running", GaugeMath.signedRate(this.menu.rateDrawn()));
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (GridScreenKit.in(mx, my, 8, 20, 10, 46)) {
            heatTooltip(lines);
        } else if (GridScreenKit.in(mx, my, 24, 20, 110, 36) && !GridScreenKit.in(mx, my, 98, 30, 36, 12)) {
            lines.add(Component.translatable("gui.neropower.control.load_bank.rate.tip", GaugeMath.grouped(this.menu.maxRate())));
        } else if (GridScreenKit.in(mx, my, 98, 30, 36, 12)) {
            lines.add(this.menu.rateSet() > 0
                    ? Component.translatable("gui.neropower.control.load_bank.zero.tip", GaugeMath.grouped(this.menu.rateSet()))
                    : Component.translatable("gui.neropower.control.load_bank.zero.disabled"));
        } else if (GridScreenKit.in(mx, my, 24, 58, 110, 9)) {
            lines.add(Component.translatable("gui.neropower.gauge.load_bank.load"));
            lines.add(Component.translatable("gui.neropower.gauge.load_bank.drawing", GaugeMath.grouped(this.menu.rateDrawn()),
                    GaugeMath.grouped(this.menu.rateSet())));
            if (this.menu.rateDrawn() < this.menu.rateSet()) {
                lines.add(Component.translatable("gui.neropower.gauge.load_bank.short"));
            }
        } else if (GridScreenKit.in(mx, my, 8, 69, 160, 9)) {
            lines.add(Component.translatable("gui.neropower.gauge.load_bank.heat"));
            lines.add(Component.translatable("gui.neropower.gauge.load_bank.heat.line", heatText(),
                    GaugeMath.grouped(this.menu.rateDrawn())));
            lines.add(Component.translatable("gui.neropower.gauge.load_bank.stirling"));
        } else if (GridScreenKit.in(mx, my, 8, 80, 160, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
        }
    }

    private void sendRate(long rate) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            long clamped = Math.max(0L, Math.min(this.menu.maxRate(), rate));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, LoadBankMenu.rateButton(clamped));
        }
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    *///?}
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (GridScreenKit.in(mx, my, 98, 30, 36, 12) && this.menu.rateSet() > 0) {
            sendRate(0L);
            return true;
        }
        if (GridScreenKit.in(mx, my, 24, 44, 110, 12)) {
            long value = this.menu.maxRate() * GridScreenKit.permilleAt(mx, 24, 110) / 1000L;
            sendRate(Math.round(value / 100.0) * 100L);
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int mx = (int) mouseX - this.leftPos;
        int my = (int) mouseY - this.topPos;
        if (scrollY != 0 && GridScreenKit.in(mx, my, 24, 20, 110, 40)) {
            long step = 100L;
            sendRate(this.menu.rateSet() + (scrollY > 0 ? step : -step));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}

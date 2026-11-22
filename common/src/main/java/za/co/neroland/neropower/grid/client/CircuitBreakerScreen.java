package za.co.neroland.neropower.grid.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.grid.BreakerLogic;
import za.co.neroland.neropower.grid.CircuitBreakerMenu;
import za.co.neroland.neropower.storage.client.StorageScreenKit;

/**
 * Circuit Breaker screen (tall hull 176 × 222): state word / trip flag, Reset,
 * load bar with the 100 % tick, stage block, the limit (1-2-5 log slider, scroll one notch), the redstone
 * reset checkbox and the stall line. No default gauges. Every number is the breaker's own.
 */
public class CircuitBreakerScreen extends PowerMachineScreen<CircuitBreakerMenu> {

    private static final int[] NOTCHES = BreakerLogic.notches();

    public CircuitBreakerScreen(CircuitBreakerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 222);
    }

    @Override
    protected boolean drawsDefaultGauges() {
        return false;
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    private int loadPermilleOfScale() {
        // the bar shows 0..125 % of the limit
        long limit = Math.max(1L, this.menu.limit());
        return (int) Math.min(1000L, this.menu.demand() * 800L / limit);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        int mx = mouseX - x;
        int my = mouseY - y;
        boolean tripped = this.menu.tripped();
        boolean blinkOff = tripped && (gameTick() % 40) >= 30;
        if (tripped) {
            if (!blinkOff) {
                g.fill(x + 8, y + 20, x + 92, y + 36, GridScreenKit.FAILURE);
            } else {
                g.fill(x + 8, y + 20, x + 92, y + 36, GridScreenKit.EDGE);
            }
            g.text(this.font, Component.translatable("gui.neropower.circuit_breaker.tripped"), x + 14, y + 24,
                    GridScreenKit.FLAG_TEXT, false);
        } else {
            g.text(this.font, Component.translatable("gui.neropower.circuit_breaker.closed"), x + 14, y + 24,
                    GridScreenKit.STABLE, false);
        }
        GridScreenKit.button(g, this.font, x, y, 96, 21, 38, 14,
                Component.translatable("gui.neropower.control.circuit_breaker.reset"), tripped,
                GridScreenKit.in(mx, my, 96, 21, 38, 14));

        int load = tripped ? 0 : loadPermilleOfScale();
        GridScreenKit.hbar(g, x, y, 8, 40, 126, 7, load, this.menu.stressPermille() > 600 ? GridScreenKit.HOT : GridScreenKit.AMBER);
        g.fill(x + 108, y + 39, x + 109, y + 48, GridScreenKit.VALUE);
        g.text(this.font, Component.translatable("gui.neropower.circuit_breaker.load",
                GaugeMath.compact(this.menu.demand()), GaugeMath.compact(this.menu.limit()),
                GaugeTooltips.percentOf(Math.min(9999, this.menu.stressPermille()))), x + 8, y + 50, GridScreenKit.VALUE, false);
        StorageScreenKit.stage(g, this.font, x + 8, y + 61, this.menu.stage());

        g.text(this.font, Component.translatable("gui.neropower.circuit_breaker.limit"), x + 8, y + 83,
                GridScreenKit.SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.circuit_breaker.value",
                GaugeMath.grouped(this.menu.limit())), x + 42, y + 83, GridScreenKit.VALUE, false);
        GridScreenKit.slider(g, x, y, 8, 96, 126, GridScreenKit.logPermille(this.menu.limit(), BreakerLogic.MAX_LIMIT),
                GridScreenKit.AMBER);
        GridScreenKit.checkbox(g, this.font, x, y, 8, 106, this.menu.redstoneReset(),
                Component.translatable("gui.neropower.control.circuit_breaker.redstone"));
        g.text(this.font, stall(), x + 8, y + 116, GridScreenKit.SUBTLE, false);
    }

    private Component stall() {
        Component idle = Component.translatable("gui.neropower.circuit_breaker.idle");
        Component running = Component.translatable("gui.neropower.circuit_breaker.running",
                GaugeMath.signedRate(this.menu.passed()));
        if (this.menu.readout() == 3) {
            return Component.translatable("gui.neropower.circuit_breaker.near_limit");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(), idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (GridScreenKit.in(mx, my, 8, 20, 84, 16)) {
            lines.add(Component.translatable("gui.neropower.gauge.breaker"));
            lines.add(Component.translatable(this.menu.tripped() ? "gui.neropower.gauge.breaker.tripped"
                    : "gui.neropower.gauge.breaker.closed"));
        } else if (GridScreenKit.in(mx, my, 96, 21, 38, 14)) {
            lines.add(Component.translatable(this.menu.tripped() ? "gui.neropower.control.circuit_breaker.reset.tip"
                    : "gui.neropower.control.circuit_breaker.reset.disabled"));
        } else if (GridScreenKit.in(mx, my, 8, 39, 126, 20)) {
            lines.add(Component.translatable("gui.neropower.gauge.breaker.load"));
            lines.add(Component.translatable("gui.neropower.gauge.breaker.load.line", GaugeMath.compact(this.menu.demand()),
                    GaugeMath.compact(this.menu.limit()), GaugeTooltips.percentOf(Math.min(9999, this.menu.stressPermille()))));
            lines.add(Component.translatable("gui.neropower.gauge.breaker.load.burst",
                    GaugeMath.compact(BreakerLogic.passCap(BreakerLogic.clampLimit(this.menu.limit())))));
        } else if (GridScreenKit.in(mx, my, 8, 61, 96, 18)) {
            lines.add(GaugeTooltips.tr("stage"));
            lines.add(Component.translatable(this.menu.stage().translationKey()));
            lines.add(Component.translatable("gui.neropower.gauge.breaker.stage.measure",
                    GaugeTooltips.percentOf(Math.min(9999, this.menu.stressPermille()))));
            lines.add(Component.translatable("gui.neropower.gauge.breaker.stage.action"));
        } else if (GridScreenKit.in(mx, my, 8, 81, 126, 23)) {
            lines.add(Component.translatable("gui.neropower.control.circuit_breaker.limit.tip"));
        } else if (GridScreenKit.in(mx, my, 8, 106, 126, 10)) {
            lines.add(Component.translatable("gui.neropower.control.circuit_breaker.redstone.tip",
                    Component.translatable(this.menu.redstoneReset() ? "gui.neropower.state.on" : "gui.neropower.state.off")));
        } else if (GridScreenKit.in(mx, my, 8, 116, 160, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
        }
    }

    // --- input ------------------------------------------------------------------------------------

    private void send(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
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
        if (GridScreenKit.in(mx, my, 96, 21, 38, 14) && this.menu.tripped()) {
            send(za.co.neroland.neropower.menu.MenuButtons.encode(CircuitBreakerMenu.ACTION_RESET, 0));
            return true;
        }
        if (GridScreenKit.in(mx, my, 6, 92, 130, 12)) {
            int notch = GridScreenKit.nearestNotch(NOTCHES, GridScreenKit.permilleAt(mx, 8, 126), BreakerLogic.MAX_LIMIT);
            send(CircuitBreakerMenu.limitButton(notch));
            return true;
        }
        if (GridScreenKit.in(mx, my, 8, 106, 126, 10)) {
            send(za.co.neroland.neropower.menu.MenuButtons.encode(CircuitBreakerMenu.ACTION_REDSTONE_RESET,
                    this.menu.redstoneReset() ? 0 : 1));
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
        if (scrollY != 0 && GridScreenKit.in(mx, my, 8, 81, 126, 23)) {
            send(CircuitBreakerMenu.limitButton(GridScreenKit.stepNotch(NOTCHES, this.menu.limit(), scrollY > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}

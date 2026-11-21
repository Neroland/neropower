package za.co.neroland.neropower.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.nerotech.client.MachineScreen;
import za.co.neroland.nerotech.menu.MachineMenu;

import za.co.neroland.neropower.menu.GaugeTailMenu;

/**
 * The base of every NeroPower machine screen: NeroTech's {@link MachineScreen} hull plus each screen's
 * own readout. NeroTech's screen is the <b>single tooltip emitter</b>: it samples the energy and heat
 * gauges and the work bar, and emits whatever {@link #gaugeTooltip} returns once per frame. This class
 * never emits a tooltip itself; it extends {@link #gaugeTooltip} with the readout ({@link #readoutTooltip})
 * where no default gauge answered, and on storage screens describes the energy gauge from the wide tail
 * value so a store past 2^30 NE reads correctly. Each gauge rectangle therefore has exactly one provider.
 * Only the machine's own numbers already on screen; no identities, no coordinates.
 *
 * @param <T> the machine menu type
 */
public abstract class PowerMachineScreen<T extends MachineMenu & GaugeTailMenu> extends MachineScreen<T> {

    /** Height of one readout text line, for hover tests. */
    protected static final int TEXT_H = 9;

    protected PowerMachineScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /** A screen on one of the taller or wider 0.4.0 hulls (NeroTech's sized constructor; never wider than 248). */
    protected PowerMachineScreen(T menu, Inventory playerInventory, Component title, int width, int height) {
        super(menu, playerInventory, title, width, height);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        super.extractContents(extractor, mouseX, mouseY, partialTick);
        sampleReadout(gameTick());
        extractReadout(extractor, mouseX, mouseY, partialTick);
    }

    /** Draw this screen's own readout over the hull (screen coordinates). Default: nothing. */
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * Append the tooltip for this screen's own readout under the panel-relative point, if any. Called only
     * when the point is not over a default gauge or the work bar. Default: nothing.
     */
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
    }

    /** Sample this screen's own rate windows (one call per frame; one sample per tick is kept). */
    protected void sampleReadout(long tick) {
    }

    /** The current game tick (the rate windows' clock), or 0 before a level exists. */
    protected final long gameTick() {
        return gaugeTick();
    }

    /** Net energy change per tick over NeroTech's sampling window. */
    protected final double energyNetPerTick() {
        return gaugeEnergyNetPerTick();
    }

    /** Storage screens: the energy gauge from the wide values (NeroTech's tail stops at 2^30 - 1). */
    @Override
    protected void energyTooltip(List<Component> lines) {
        long capacity = this.menu.wideEnergyCapacity();
        if (capacity <= 0L) {
            super.energyTooltip(lines);
            return;
        }
        GaugeTooltips.energy(lines, this.menu.wideEnergyStored(), capacity, gaugeEnergyNetPerTick(),
                energyUsedPerTick(), energyDeliveredPerTick());
    }

    /** Whether a panel-relative point is over readout lines {@code first..last} drawn from {@code (x, y)}. */
    protected static boolean overLines(int mx, int my, int x, int y, int stride, int first, int last, int width) {
        int top = y + first * stride;
        int bottom = y + last * stride + TEXT_H;
        return mx >= x && mx < x + width && my >= top && my < bottom;
    }

    /** The default gauges and work bar first (NeroTech), then this screen's readout. One provider per point. */
    @Override
    protected List<Component> gaugeTooltip(int mx, int my) {
        List<Component> lines = super.gaugeTooltip(mx, my);
        if (lines.isEmpty()) {
            readoutTooltip(lines, mx, my);
        }
        return lines;
    }
}

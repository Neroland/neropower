package za.co.neroland.neropower.grid.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.grid.MonitorRing;
import za.co.neroland.neropower.grid.PowerMonitorBlockEntity;
import za.co.neroland.neropower.grid.PowerMonitorMenu;
import za.co.neroland.neropower.storage.client.StorageScreenKit;

/**
 * Power Monitor screen (wide hull 248 × 200): the source's name, the 60-sample
 * trace graph (in solid amber, out dashed blue, stored dotted white; in/out share a scale, stored is
 * 0–100 %), the three line toggles (client-only, kept for the session), the current values and the stall
 * line. No serverbound intent. Reads the monitor block entity's synced ring; no player data.
 */
public class PowerMonitorScreen extends PowerMachineScreen<PowerMonitorMenu> {

    private static final int GX = 8;
    private static final int GY = 31;
    private static final int GW = 198;
    private static final int GH = 38;

    /** Session-only line toggles (not sent, not saved). */
    private static boolean showIn = true;
    private static boolean showOut = true;
    private static boolean showStored = true;

    public PowerMonitorScreen(PowerMonitorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, PowerMonitorMenu.WIDTH, PowerMonitorMenu.HEIGHT);
        this.inventoryLabelX = 43;
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
        return 210;
    }

    @Nullable
    private PowerMonitorBlockEntity monitor() {
        BlockPos pos = this.menu.machinePos();
        if (pos == null || this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(pos) instanceof PowerMonitorBlockEntity m ? m : null;
    }

    private Component sourceName(PowerMonitorBlockEntity m) {
        Identifier id = Identifier.tryParse(m.sourceId());
        if (id == null) {
            return Component.literal("?");
        }
        return BuiltInRegistries.BLOCK.getOptional(id).map(b -> (Component) b.getName()).orElse(Component.literal("?"));
    }

    private static long scaleMax(MonitorRing ring) {
        long max = 1L;
        for (int i = 0; i < ring.size(); i++) {
            max = Math.max(max, Math.max(ring.in(i), ring.out(i)));
        }
        return max;
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        PowerMonitorBlockEntity m = monitor();
        boolean has = m != null && m.hasSource();
        if (has) {
            g.text(this.font, Component.translatable("gui.neropower.power_monitor.reading", sourceName(m)), x + 8, y + 21,
                    GridScreenKit.VALUE, false);
        } else {
            g.text(this.font, Component.translatable("gui.neropower.power_monitor.none"), x + 8, y + 21, GridScreenKit.AMBER, false);
        }
        g.fill(x + GX - 1, y + GY - 1, x + GX + GW + 1, y + GY + GH + 1, GridScreenKit.EDGE);
        g.fill(x + GX, y + GY, x + GX + GW, y + GY + GH, GridScreenKit.TROUGH);
        g.fill(x + GX, y + GY + GH / 2, x + GX + GW, y + GY + GH / 2 + 1, GridScreenKit.DIVIDER);
        if (m != null) {
            MonitorRing ring = m.ring();
            long max = scaleMax(ring);
            int n = ring.size();
            for (int i = 0; i < n; i++) {
                int col = MonitorRing.SIZE - n + i;
                int px = x + GX + col * GW / MonitorRing.SIZE;
                if (showIn) {
                    int py = y + GY + GH - 1 - (int) (ring.in(i) * (GH - 1) / max);
                    g.fill(px, py, px + 3, py + 1, GridScreenKit.ENERGY);
                }
                if (showOut && (col % 5) < 3) {
                    int py = y + GY + GH - 1 - (int) (ring.out(i) * (GH - 1) / max);
                    g.fill(px, py, px + 3, py + 1, GridScreenKit.OUT);
                }
                if (showStored && (col % 3) == 0) {
                    int py = y + GY + GH - 1 - ring.storedPermille(i) * (GH - 1) / 1000;
                    g.fill(px, py, px + 1, py + 1, GridScreenKit.VALUE);
                }
            }
            g.text(this.font, Component.translatable("gui.neropower.power_monitor.graph_max", GaugeMath.compact(max)),
                    x + GX + 2, y + GY + 2, GridScreenKit.SUBTLE, false);
            String full = "100%";
            g.text(this.font, Component.literal(full), x + GX + GW - 2 - this.font.width(full), y + GY + 2, GridScreenKit.SUBTLE, false);
        }
        legend(g, x, y, 8, showIn, "in", GridScreenKit.ENERGY);
        legend(g, x, y, 74, showOut, "out", GridScreenKit.OUT);
        legend(g, x, y, 140, showStored, "stored", GridScreenKit.VALUE);
        long in = m == null || m.ring().size() == 0 ? 0L : m.ring().in(m.ring().size() - 1);
        long out = m == null || m.ring().size() == 0 ? 0L : m.ring().out(m.ring().size() - 1);
        g.text(this.font, Component.literal(GaugeMath.signedRate(in)), x + 8, y + 84, GridScreenKit.VALUE, false);
        g.text(this.font, Component.literal(GaugeMath.signedRate(-out)), x + 74, y + 84, GridScreenKit.VALUE, false);
        g.text(this.font, Component.literal(GaugeMath.compact(m == null ? 0L : m.stored()) + " NE"), x + 140, y + 84,
                GridScreenKit.VALUE, false);
        GridScreenKit.button(g, this.font, x, y, 210, 72, 36, 12, Component.translatable("gui.neropower.power_monitor.window"),
                false, false);
        g.text(this.font, stall(in - out), x + 8, y + 94, GridScreenKit.SUBTLE, false);
    }

    private void legend(GuiGraphicsExtractor g, int x, int y, int lx, boolean on, String key, int color) {
        GridScreenKit.checkbox(g, this.font, x, y, lx, 72, on, Component.empty());
        g.fill(x + lx + 13, y + 77, x + lx + 25, y + 78, color);
        g.text(this.font, Component.translatable("gui.neropower.power_monitor." + key), x + lx + 28, y + 73,
                GridScreenKit.VALUE, false);
    }

    private Component stall(long net) {
        if (this.menu.readout() == 2) {
            return Component.translatable("gui.neropower.power_monitor.nothing");
        }
        return StorageScreenKit.stallLine(this.menu.readout(), this.menu.stallCause(),
                Component.translatable("gui.neropower.power_monitor.idle"),
                Component.translatable("gui.neropower.power_monitor.running", GaugeMath.signedRate(net)));
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        PowerMonitorBlockEntity m = monitor();
        if (GridScreenKit.in(mx, my, 8, 21, 198, 9)) {
            lines.add(Component.translatable("gui.neropower.gauge.power_monitor.source"));
            lines.add(Component.translatable("gui.neropower.gauge.power_monitor.source.line"));
            lines.add(Component.translatable("gui.neropower.gauge.power_monitor.source.reads"));
        } else if (GridScreenKit.in(mx, my, GX, GY, GW, GH) && m != null && m.ring().size() > 0) {
            MonitorRing ring = m.ring();
            int col = Math.min(MonitorRing.SIZE - 1, (mx - GX) * MonitorRing.SIZE / GW);
            int i = col - (MonitorRing.SIZE - ring.size());
            if (i < 0) {
                return;
            }
            int secondsAgo = (ring.size() - 1 - i) * PowerMonitorBlockEntity.SAMPLE_TICKS / GaugeMath.TICKS_PER_SECOND;
            lines.add(Component.translatable("gui.neropower.gauge.power_monitor.ago", secondsAgo));
            if (showIn) {
                lines.add(Component.translatable("gui.neropower.gauge.power_monitor.in", GaugeMath.grouped(ring.in(i))));
            }
            if (showOut) {
                lines.add(Component.translatable("gui.neropower.gauge.power_monitor.out", GaugeMath.grouped(ring.out(i))));
            }
            if (showStored) {
                lines.add(Component.translatable("gui.neropower.gauge.power_monitor.stored",
                        GaugeMath.compact(m.capacity() * ring.storedPermille(i) / 1000L),
                        GaugeTooltips.percentOf(ring.storedPermille(i))));
            }
        } else if (GridScreenKit.in(mx, my, 8, 72, 198, 22)) {
            boolean on = mx < 74 ? showIn : mx < 140 ? showOut : showStored;
            if (my < 84) {
                lines.add(Component.translatable("gui.neropower.gauge.power_monitor.toggle",
                        Component.translatable(on ? "gui.neropower.state.shown" : "gui.neropower.state.hidden")));
            } else if (m != null) {
                long avg = mx < 74 ? m.ring().averageIn() : mx < 140 ? m.ring().averageOut() : -1L;
                if (avg >= 0L) {
                    lines.add(Component.translatable(mx < 74 ? "gui.neropower.gauge.power_monitor.now_in"
                            : "gui.neropower.gauge.power_monitor.now_out"));
                    lines.add(Component.translatable("gui.neropower.gauge.power_monitor.average", GaugeMath.signedRate(avg)));
                } else {
                    int permille = m.capacity() <= 0 ? 0 : (int) Math.min(1000L, m.stored() * 1000L / m.capacity());
                    lines.add(Component.translatable("gui.neropower.gauge.power_monitor.now_stored",
                            GaugeMath.compact(m.stored()), GaugeMath.compact(m.capacity()), GaugeTooltips.percentOf(permille)));
                }
            }
        } else if (GridScreenKit.in(mx, my, 210, 72, 36, 12)) {
            lines.add(Component.translatable("gui.neropower.gauge.power_monitor.window"));
        } else if (GridScreenKit.in(mx, my, 8, 94, 232, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(this.menu.readout() == 2 ? Component.translatable("gui.neropower.power_monitor.fix")
                    : stall(0L));
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
        if (GridScreenKit.in(mx, my, 8, 72, 198, 11)) {
            if (mx < 74) {
                showIn = !showIn;
            } else if (mx < 140) {
                showOut = !showOut;
            } else {
                showStored = !showStored;
            }
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }
}

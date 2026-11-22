package za.co.neroland.neropower.storage.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.storage.SmesMenu;

/**
 * Storage Ring Controller screen (spec {@code smes_controller}): wide tall 248 x 222 hull, no default
 * gauges. Ring gauge (energy, wide values, inherited energy tooltip), cryo tank, warmth bar with the
 * warning and unstable ticks, ring / I/O / quench lines, the stage block, the lockout line, a one-minute
 * trace graph sampled client-side at 1 Hz while open (nothing synced, nothing saved), and the stall line.
 */
public class SmesScreen extends PowerMachineScreen<SmesMenu> {

    private static final int SAMPLES = 60;

    private final long[] inSamples = new long[SAMPLES];
    private final long[] outSamples = new long[SAMPLES];
    private final int[] storedSamples = new int[SAMPLES];
    private int sampleCount;
    private long lastSampleTick = Long.MIN_VALUE;

    public SmesScreen(SmesMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, SmesMenu.WIDTH, SmesMenu.HEIGHT);
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

    private int permille() {
        long capacity = this.menu.wideEnergyCapacity();
        return capacity <= 0 ? 0 : (int) Math.min(1000L, this.menu.wideEnergyStored() * 1000L / capacity);
    }

    @Override
    protected void sampleReadout(long tick) {
        if (tick - this.lastSampleTick < 20 && this.lastSampleTick != Long.MIN_VALUE) {
            return;
        }
        this.lastSampleTick = tick;
        System.arraycopy(this.inSamples, 1, this.inSamples, 0, SAMPLES - 1);
        System.arraycopy(this.outSamples, 1, this.outSamples, 0, SAMPLES - 1);
        System.arraycopy(this.storedSamples, 1, this.storedSamples, 0, SAMPLES - 1);
        this.inSamples[SAMPLES - 1] = this.menu.inPerTick();
        this.outSamples[SAMPLES - 1] = this.menu.outPerTick();
        this.storedSamples[SAMPLES - 1] = permille();
        this.sampleCount = Math.min(SAMPLES, this.sampleCount + 1);
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        FailureStage stage = this.menu.stage();
        boolean halved = stage == FailureStage.UNSTABLE;
        StorageScreenKit.ring(extractor, this.font, x + 8, y + 20, permille(),
                halved ? StorageScreenKit.VIOLET_DIM : StorageScreenKit.VIOLET);
        long cap = this.menu.coolantCapacity();
        StorageScreenKit.bar(extractor, x + 60, y + 20, 14, 46,
                cap <= 0 ? 0 : (int) Math.min(1000L, this.menu.coolant() * 1000L / cap), StorageScreenKit.CRYO);
        StorageScreenKit.bar(extractor, x + 80, y + 20, 10, 46, this.menu.warmth(), StorageScreenKit.WARMTH);
        tick(extractor, x + 90, y + 20, this.menu.warningPermille());
        tick(extractor, x + 90, y + 20, this.menu.unstablePermille());

        extractor.text(this.font, Component.translatable("gui.neropower.smes.stored",
                GaugeMath.compact(this.menu.wideEnergyStored()), GaugeMath.compact(this.menu.wideEnergyCapacity())),
                x + 96, y + 21, StorageScreenKit.VALUE, false);
        if (this.menu.formed()) {
            extractor.text(this.font, Component.translatable("gui.neropower.smes.coils", this.menu.coils()),
                    x + 96, y + 31, StorageScreenKit.VALUE, false);
        } else {
            extractor.text(this.font, Component.translatable("gui.neropower.smes.unformed"),
                    x + 96, y + 31, StorageScreenKit.WARNING, false);
        }
        String io = GaugeMath.compact(this.menu.ioLimit());
        extractor.text(this.font, halved ? Component.translatable("gui.neropower.smes.io_halved", io)
                : Component.translatable("gui.neropower.smes.io", io), x + 96, y + 41,
                halved ? StorageScreenKit.UNSTABLE : StorageScreenKit.VALUE, false);
        extractor.text(this.font, Component.translatable(this.menu.quenchBlast()
                ? "gui.neropower.smes.quench.blast" : "gui.neropower.smes.quench.drain"),
                x + 96, y + 51, StorageScreenKit.SUBTLE, false);
        StorageScreenKit.stage(extractor, this.font, x + 8, y + 68, stage);
        if (this.menu.lockoutTicks() > 0 || this.menu.readout() == 2 && this.menu.stallCause() == 15) {
            extractor.text(this.font, Component.translatable("gui.neropower.smes.lockout",
                    GaugeMath.duration(this.menu.lockoutTicks())), x + 110, y + 69, StorageScreenKit.FAILURE, false);
        } else {
            extractor.text(this.font, Component.translatable("gui.neropower.smes.armed"), x + 110, y + 69,
                    StorageScreenKit.STABLE, false);
        }
        graph(extractor, x + 8, y + 90, 140, 24);
        legend(extractor, x + 152, y + 90);
        extractor.text(this.font, stall(), x + 8, y + 116, StorageScreenKit.SUBTLE, false);
    }

    private static void tick(GuiGraphicsExtractor extractor, int x, int y, int permille) {
        int ty = y + 46 - Math.round(46 * Math.max(0, Math.min(1000, permille)) / 1000.0F);
        extractor.fill(x, ty, x + 2, ty + 1, StorageScreenKit.VALUE);
    }

    private void graph(GuiGraphicsExtractor extractor, int x, int y, int w, int h) {
        extractor.fill(x - 1, y - 1, x + w + 1, y + h + 1, StorageScreenKit.EDGE);
        extractor.fill(x, y, x + w, y + h, StorageScreenKit.TROUGH);
        extractor.fill(x, y + h / 2, x + w, y + h / 2 + 1, StorageScreenKit.DIVIDER);
        long max = 1L;
        for (int i = 0; i < SAMPLES; i++) {
            max = Math.max(max, Math.max(this.inSamples[i], this.outSamples[i]));
        }
        for (int i = SAMPLES - this.sampleCount; i < SAMPLES; i++) {
            int px = x + (int) ((long) i * (w - 1) / (SAMPLES - 1));
            dot(extractor, px, y, h, this.inSamples[i] * 1000L / max, StorageScreenKit.ENERGY, true);
            if (i % 5 < 3) { // dashed
                dot(extractor, px, y, h, this.outSamples[i] * 1000L / max, 0xFF55C2F0, true);
            }
            if (i % 3 == 0) { // dotted
                dot(extractor, px, y, h, this.storedSamples[i], StorageScreenKit.VALUE, true);
            }
        }
        extractor.text(this.font, Component.translatable("gui.neropower.smes.graph_max", GaugeMath.compact(max)),
                x + 2, y + 1, StorageScreenKit.SUBTLE, false);
    }

    private static void dot(GuiGraphicsExtractor extractor, int px, int y, int h, long permille, int color, boolean on) {
        if (!on) {
            return;
        }
        int py = y + h - 1 - (int) Math.round((h - 1) * Math.max(0L, Math.min(1000L, permille)) / 1000.0D);
        extractor.fill(px, py, px + 2, py + 1, color);
    }

    private void legend(GuiGraphicsExtractor extractor, int x, int y) {
        int[] colors = {StorageScreenKit.ENERGY, 0xFF55C2F0, StorageScreenKit.VALUE};
        String[] keys = {"in", "out", "stored"};
        for (int i = 0; i < 3; i++) {
            extractor.fill(x, y + i * 8 + 3, x + 12, y + i * 8 + 4, colors[i]);
            extractor.text(this.font, Component.translatable("gui.neropower.smes.legend." + keys[i]),
                    x + 15, y + i * 8, StorageScreenKit.SUBTLE, false);
        }
    }

    private Component stall() {
        long net = this.menu.inPerTick() - this.menu.outPerTick();
        Component running = net >= 0
                ? Component.translatable("gui.neropower.smes.charging", GaugeMath.signedRate(net))
                : Component.translatable("gui.neropower.smes.discharging", GaugeMath.signedRate(net));
        Component idle = Component.translatable("gui.neropower.smes.holding",
                GaugeMath.compact(this.menu.wideEnergyStored()));
        int readout = this.menu.readout();
        int cause = this.menu.stallCause();
        if (readout == 2 && cause == 13) {
            return Component.translatable("gui.neropower.smes.stall.no_coolant");
        }
        if (readout == 2 && cause == 15) {
            return Component.translatable("gui.neropower.smes.stall.locked_out",
                    GaugeMath.duration(this.menu.lockoutTicks()));
        }
        if (readout == 3 && cause == 13) {
            return Component.translatable("gui.neropower.smes.stall.no_coolant_warming");
        }
        if (readout == 3) {
            return Component.translatable(this.menu.stage() == FailureStage.UNSTABLE
                    ? "gui.neropower.smes.stall.halved" : "gui.neropower.smes.stall.warming");
        }
        if (readout == 5) {
            return Component.translatable("gui.neropower.smes.stall.quenching");
        }
        return StorageScreenKit.stallLine(readout, cause, idle, running);
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (over(mx, my, 8, 20, 46, 46) || over(mx, my, 96, 21, 110, 9)) {
            energyTooltip(lines);
        } else if (over(mx, my, 60, 20, 14, 46)) {
            lines.add(Component.translatable("gui.neropower.smes.cryo"));
            lines.add(this.menu.coolant() > 0 ? Component.translatable("fluid.nerotech.cryo_coolant")
                    : GaugeTooltips.tr("tank.empty"));
            lines.add(GaugeTooltips.tr("tank.amount", GaugeMath.grouped(this.menu.coolant()),
                    GaugeMath.grouped(this.menu.coolantCapacity())));
            lines.add(Component.translatable("gui.neropower.smes.cryo.use",
                    (long) this.menu.coils() * this.menu.coolantPerCoil(), this.menu.coolantPerCoil()));
        } else if (over(mx, my, 80, 20, 12, 46)) {
            lines.add(Component.translatable("gui.neropower.smes.warmth"));
            lines.add(Component.literal(GaugeTooltips.percentOf(this.menu.warmth()) + "%"));
            lines.add(Component.translatable("gui.neropower.smes.warmth.hint"));
        } else if (over(mx, my, 96, 31, 110, 9)) {
            lines.add(Component.translatable("gui.neropower.smes.ring"));
            lines.add(Component.translatable("gui.neropower.smes.ring.detail", this.menu.coils(),
                    GaugeMath.compact(this.menu.capacityPerCoil())));
            lines.add(Component.translatable("gui.neropower.smes.ring.shape"));
        } else if (over(mx, my, 96, 41, 110, 9)) {
            lines.add(Component.translatable("gui.neropower.smes.io.title"));
            lines.add(Component.translatable("gui.neropower.smes.io.detail", GaugeMath.compact(this.menu.ioLimit()),
                    GaugeMath.compact(this.menu.ioPerCoil()), GaugeMath.compact(this.menu.maxIo())));
            if (this.menu.stage() == FailureStage.UNSTABLE) {
                lines.add(Component.translatable("gui.neropower.smes.io.halved_note"));
            }
        } else if (over(mx, my, 96, 51, 110, 9)) {
            lines.add(Component.translatable("gui.neropower.smes.quench.title"));
            lines.add(this.menu.quenchBlast() ? Component.translatable("gui.neropower.smes.quench.blast_detail")
                    : Component.translatable("gui.neropower.smes.quench.drain_detail",
                            GaugeTooltips.percentOf(this.menu.quenchLossPermille()),
                            GaugeMath.duration(this.menu.quenchLockoutTicks())));
            lines.add(Component.translatable("gui.neropower.smes.quench.fixed"));
        } else if (over(mx, my, 8, 68, 96, 17)) {
            lines.add(GaugeTooltips.tr("stage"));
            lines.add(Component.translatable("neropower.failure.stage."
                    + this.menu.stage().name().toLowerCase(java.util.Locale.ROOT)));
            lines.add(Component.translatable("gui.neropower.smes.stage.detail",
                    GaugeTooltips.percentOf(this.menu.warmth())));
            lines.add(Component.translatable("gui.neropower.smes.stage.failure"));
        } else if (over(mx, my, 110, 69, 96, 9)) {
            lines.add(Component.translatable("gui.neropower.smes.lockout.title"));
            lines.add(this.menu.lockoutTicks() > 0
                    ? Component.translatable("gui.neropower.smes.lockout.detail",
                            GaugeMath.duration(this.menu.lockoutTicks()))
                    : Component.translatable("gui.neropower.smes.lockout.ready"));
        } else if (over(mx, my, 8, 90, 198, 24)) {
            int col = Math.max(0, Math.min(SAMPLES - 1, (mx - 8) * (SAMPLES - 1) / 139));
            if (mx >= 152) {
                col = SAMPLES - 1;
            }
            lines.add(Component.translatable("gui.neropower.smes.graph"));
            lines.add(Component.translatable("gui.neropower.smes.graph.in", GaugeMath.compact(this.inSamples[col])));
            lines.add(Component.translatable("gui.neropower.smes.graph.out", GaugeMath.compact(this.outSamples[col])));
            lines.add(Component.translatable("gui.neropower.smes.graph.stored",
                    GaugeTooltips.percentOf(this.storedSamples[col])));
            lines.add(Component.translatable("gui.neropower.smes.graph.fresh"));
        } else if (over(mx, my, 8, 116, 232, 9)) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stall());
            GaugeTooltips.stallFix(lines, this.menu.readout(), this.menu.stallCause());
            if (this.menu.readout() == 3 && this.menu.stallCause() == 13) {
                lines.add(Component.translatable("gui.neropower.stall.fix.no_coolant"));
            }
        }
    }
}

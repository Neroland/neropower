package za.co.neroland.neropower.fission.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.failure.FailureStage;
import za.co.neroland.neropower.fission.FissionCoreBlockEntity;
import za.co.neroland.neropower.fission.FissionCoreMenu;
import za.co.neroland.neropower.fission.ReactorMode;
import za.co.neroland.neropower.menu.MenuButtons;

/**
 * The Fission Core screen (tall hull 176 × 222): NeroTech's energy and heat bars, the steam ports'
 * water and steam bars (empty frames in direct mode), six rod slots with burn-up strips, the blanket
 * pair with a breeding arrow, the mode button, a stage block and three status lines with the stall line
 * under them. Everything comes from the menu's synced ints; hovering a line or bar gives its detail.
 */
public class FissionCoreScreen extends PowerMachineScreen<FissionCoreMenu> {

    private static final int LABEL = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;
    private static final int NOMINAL = 0xFF3CB043;
    private static final int WARNING = 0xFFE0B33A;
    private static final int DANGER = 0xFFFF5438;
    private static final int FAILURE = 0xFFFF3824;
    private static final int LOCKED = 0xA0050A10;
    private static final int EDGE = 0xFF05080D;
    private static final int TROUGH = 0xFF0B1119;
    private static final int DIVIDER = 0xFF2A3542;
    private static final int WATER = 0xFF3F76E4;
    private static final int STEAM = 0xFFE8ECEF;
    private static final int BURNUP = 0xFFE0B33A;
    private static final int BREED = 0xFFCC7012;
    private static final int BUTTON = 0xFF1B232E;
    private static final int BUTTON_HOVER = 0xFF2A3542;

    // Panel-relative rectangles (screens/fission_core.md).
    private static final int T1_X = 34;
    private static final int T2_X = 52;
    private static final int T_Y = 20;
    private static final int T_W = 14;
    private static final int T_H = 46;
    private static final int A1_X = 94;
    private static final int A1_Y = 64;
    private static final int B1_X = 8;
    private static final int B1_Y = 70;
    private static final int B1_W = 62;
    private static final int B1_H = 12;
    private static final int L0_Y = 86;
    private static final int ST_X = 76;
    private static final int ST_Y = 79;
    private static final int ST_W = 96;
    private static final int ST_H = 17;
    private static final int L1_Y = 98;
    private static final int L2_Y = 107;
    private static final int S_Y = 116;
    private static final int LINE_W = 160;

    private static final int SLOW_WINDOW_TICKS = 1_200;
    private static final long SLOW_JUMP_RESET = 300L;
    private static final double POISON_DEADBAND_PERCENT_PER_MINUTE = 0.05D;

    private final GaugeMath.RateWindow[] rodWindows = new GaugeMath.RateWindow[FissionCoreBlockEntity.ROD_SLOTS];
    private final GaugeMath.RateWindow poisonWindow = new GaugeMath.RateWindow(SLOW_JUMP_RESET, SLOW_WINDOW_TICKS);
    private final GaugeMath.RateWindow breedWindow = new GaugeMath.RateWindow(SLOW_JUMP_RESET, SLOW_WINDOW_TICKS);
    private final GaugeMath.RateWindow waterWindow = new GaugeMath.RateWindow();
    private final GaugeMath.RateWindow steamWindow = new GaugeMath.RateWindow();

    public FissionCoreScreen(FissionCoreMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, FissionCoreMenu.HEIGHT);
        for (int i = 0; i < this.rodWindows.length; i++) {
            this.rodWindows[i] = new GaugeMath.RateWindow(SLOW_JUMP_RESET, SLOW_WINDOW_TICKS);
        }
    }

    @Override
    protected boolean drawsDefaultWorkBar() {
        return false;
    }

    // --- drawing -----------------------------------------------------------------------------------

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        boolean steam = this.menu.mode() == ReactorMode.STEAM;

        tank(g, x + T1_X, y + T_Y, steam, this.menu.waterMb(), this.menu.waterCapacityMb(), WATER);
        tank(g, x + T2_X, y + T_Y, steam, this.menu.steamMb(), this.menu.steamCapacityMb(), STEAM);

        // Rod slots: shade the locked ones, strip the burn-up on the loaded ones.
        int usable = this.menu.usableRodSlots();
        for (int slot = 0; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
            Slot s = this.menu.slots.get(slot);
            int sx = x + s.x;
            int sy = y + s.y;
            if (slot >= usable) {
                g.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                continue;
            }
            if (s.getItem().isEmpty()) {
                continue;
            }
            int left = 1000 - this.menu.rodBurnup(slot);
            g.fill(sx + 1, sy + 13, sx + 15, sy + 15, TROUGH);
            int w = Math.round(14F * left / 1000F);
            if (w > 0) {
                g.fill(sx + 1, sy + 13, sx + 1 + w, sy + 15, left < 100 ? DANGER : BURNUP);
            }
        }

        // Breeding arrow.
        g.fill(x + A1_X, y + A1_Y, x + A1_X + 16, y + A1_Y + 8, DIVIDER);
        g.fill(x + A1_X + 1, y + A1_Y + 1, x + A1_X + 15, y + A1_Y + 7, TROUGH);
        int fill = Math.round(14F * this.menu.breedPermille() / 1000F);
        if (fill > 0) {
            g.fill(x + A1_X + 1, y + A1_Y + 1, x + A1_X + 1 + fill, y + A1_Y + 7, BREED);
        }

        // Mode button.
        boolean enabled = modeButtonEnabled();
        boolean hover = enabled && inRect(mouseX - x, mouseY - y, B1_X, B1_Y, B1_W, B1_H);
        g.fill(x + B1_X - 1, y + B1_Y - 1, x + B1_X + B1_W + 1, y + B1_Y + B1_H + 1, EDGE);
        g.fill(x + B1_X, y + B1_Y, x + B1_X + B1_W, y + B1_Y + B1_H, hover ? BUTTON_HOVER : BUTTON);
        Component modeLabel = Component.translatable(this.menu.requestedMode().translationKey());
        int tw = this.font.width(modeLabel);
        g.text(this.font, modeLabel, x + B1_X + Math.max(2, (B1_W - tw) / 2), y + B1_Y + 2, enabled ? LABEL : SUBTLE, false);

        // Breed line.
        Component breed = this.menu.blankets() > 0
                ? Component.translatable("gui.neropower.fission_core.breed", this.menu.breedPermille() / 10)
                : Component.translatable("gui.neropower.fission_core.no_blankets");
        g.text(this.font, breed, x + 8, y + L0_Y, this.menu.blankets() > 0 ? LABEL : SUBTLE, false);

        // Stage block.
        FailureStage stage = FailureStage.byCode(this.menu.failureStageCode());
        int stageColor = stageColor(stage);
        g.fill(x + ST_X, y + ST_Y, x + ST_X + ST_W, y + ST_Y + ST_H, DIVIDER);
        g.fill(x + ST_X + 1, y + ST_Y + 1, x + ST_X + ST_W - 1, y + ST_Y + ST_H - 1, TROUGH);
        for (int cell = 0; cell < 4; cell++) {
            int cx = x + ST_X + 2 + cell * 23;
            g.fill(cx, y + ST_Y + 12, cx + 21, y + ST_Y + 15, cell <= stage.code() ? stageColor(FailureStage.byCode(cell)) : DIVIDER);
        }
        g.text(this.font, Component.translatable(stage.translationKey()), x + ST_X + 3, y + ST_Y + 2, stageColor, false);

        // Lines.
        int shell = this.menu.shellSize();
        g.text(this.font, Component.translatable("gui.neropower.fission_core.rods", loadedRods(), usable,
                worstBurnup() / 10), x + 8, y + L1_Y, LABEL, false);
        if (shell > 0) {
            int poison = this.menu.poison();
            int poisonColor = poison > 800 ? DANGER : poison > 500 ? WARNING : LABEL;
            g.text(this.font, Component.translatable("gui.neropower.fission_core.status", shell,
                    this.menu.controlRodPermille() / 10, poison / 10), x + 8, y + L2_Y, poisonColor, false);
        } else {
            g.text(this.font, Component.translatable("gui.neropower.fission_core.unformed"), x + 8, y + L2_Y, WARNING, false);
        }
        g.text(this.font, stallLine(), x + 8, y + S_Y, stallColor(), false);
    }

    private void tank(GuiGraphicsExtractor g, int tx, int ty, boolean on, long amount, long capacity, int color) {
        g.fill(tx - 1, ty - 1, tx + T_W + 1, ty + T_H + 1, on ? EDGE : DIVIDER);
        g.fill(tx, ty, tx + T_W, ty + T_H, TROUGH);
        if (!on || capacity <= 0L) {
            return;
        }
        int h = (int) Math.min(T_H, amount * T_H / capacity);
        if (h > 0) {
            g.fill(tx, ty + T_H - h, tx + T_W, ty + T_H, color);
        }
        g.fill(tx, ty, tx + T_W, ty + 2, color); // always-on cap
    }

    private static int stageColor(FailureStage stage) {
        return switch (stage) {
            case STABLE -> NOMINAL;
            case WARNING -> WARNING;
            case UNSTABLE -> DANGER;
            case FAILURE -> FAILURE;
        };
    }

    private int loadedRods() {
        int n = 0;
        for (int slot = 0; slot < this.menu.usableRodSlots(); slot++) {
            if (!this.menu.slots.get(slot).getItem().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private int worstBurnup() {
        int worst = 0;
        for (int slot = 0; slot < this.menu.usableRodSlots(); slot++) {
            if (!this.menu.slots.get(slot).getItem().isEmpty()) {
                worst = Math.max(worst, this.menu.rodBurnup(slot));
            }
        }
        return worst;
    }

    private Component stallLine() {
        int cause = this.menu.stallCause();
        return switch (cause) {
            case FissionCoreBlockEntity.STALL_UNFORMED -> Component.translatable("gui.neropower.stall.fission.unformed");
            case FissionCoreBlockEntity.STALL_NO_FUEL -> Component.translatable("gui.neropower.stall.fission.no_fuel");
            case FissionCoreBlockEntity.STALL_POISONED -> Component.translatable("gui.neropower.stall.fission.poisoned");
            case FissionCoreBlockEntity.STALL_NO_WATER -> Component.translatable("gui.neropower.stall.fission.no_water");
            case FissionCoreBlockEntity.STALL_STEAM_FULL -> Component.translatable("gui.neropower.stall.fission.steam_full");
            case FissionCoreBlockEntity.STALL_NO_ROOM -> Component.translatable("gui.neropower.stall.fission.no_room");
            case FissionCoreBlockEntity.STALL_SCRAM -> Component.translatable("gui.neropower.stall.fission.scram",
                    GaugeMath.duration(this.menu.scramTicks()));
            default -> this.menu.readout() == FissionCoreBlockEntity.READOUT_WORK
                    ? (this.menu.mode() == ReactorMode.STEAM
                            ? Component.translatable("gui.neropower.stall.fission.boiling", this.menu.rate())
                            : Component.translatable("gui.neropower.stall.fission.generating", this.menu.rate()))
                    : Component.translatable("gui.neropower.stall.fission.idle");
        };
    }

    private int stallColor() {
        int cause = this.menu.stallCause();
        if (cause == FissionCoreBlockEntity.STALL_NONE) {
            return this.menu.readout() == FissionCoreBlockEntity.READOUT_WORK ? NOMINAL : SUBTLE;
        }
        return cause == FissionCoreBlockEntity.STALL_SCRAM ? DANGER : WARNING;
    }

    private boolean modeButtonEnabled() {
        if (this.menu.failureStageCode() >= FailureStage.WARNING.code()) {
            return false;
        }
        return this.menu.requestedMode() == ReactorMode.STEAM || this.menu.steamPorts() > 0;
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // --- sampling and tooltips ---------------------------------------------------------------------

    @Override
    protected void sampleReadout(long tick) {
        for (int i = 0; i < this.rodWindows.length; i++) {
            if (i < this.menu.usableRodSlots() && !this.menu.slots.get(i).getItem().isEmpty()) {
                this.rodWindows[i].sample(tick, this.menu.rodBurnup(i));
            } else {
                this.rodWindows[i].reset();
            }
        }
        this.poisonWindow.sample(tick, this.menu.poison());
        this.breedWindow.sample(tick, this.menu.breedPermille());
        this.waterWindow.sample(tick, this.menu.waterMb());
        this.steamWindow.sample(tick, this.menu.steamMb());
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        if (inRect(mx, my, T1_X, T_Y, T_W, T_H)) {
            tankTooltip(lines, "fission.water", this.menu.waterMb(), this.menu.waterCapacityMb(), this.waterWindow, false);
        } else if (inRect(mx, my, T2_X, T_Y, T_W, T_H)) {
            tankTooltip(lines, "fission.steam", this.menu.steamMb(), this.menu.steamCapacityMb(), this.steamWindow, true);
        } else if (inRect(mx, my, B1_X, B1_Y, B1_W, B1_H)) {
            modeTooltip(lines);
        } else if (inRect(mx, my, A1_X, A1_Y, 16, 8) || overLines(mx, my, 8, L0_Y, 9, 0, 0, 62)) {
            breedTooltip(lines);
        } else if (inRect(mx, my, ST_X, ST_Y, ST_W, ST_H)) {
            stageTooltip(lines);
        } else if (overLines(mx, my, 8, L1_Y, 9, 0, 0, LINE_W) || overEmptyRod(mx, my)) {
            burnupTooltip(lines);
        } else if (overLines(mx, my, 8, L2_Y, 9, 0, 0, LINE_W)) {
            shellTooltip(lines);
        } else if (overEmptySlot(mx, my, FissionCoreBlockEntity.BLANKET_IN)) {
            lines.add(GaugeTooltips.tr("fission.pallurite_slot"));
        } else if (overEmptySlot(mx, my, FissionCoreBlockEntity.MACHINE_SLOTS + 3)) {
            lines.add(GaugeTooltips.tr("fission.economizer_slot"));
        }
    }

    private boolean overEmptyRod(int mx, int my) {
        for (int slot = 0; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
            if (overEmptySlot(mx, my, slot)) {
                return true;
            }
        }
        return false;
    }

    private boolean overEmptySlot(int mx, int my, int index) {
        if (index < 0 || index >= this.menu.slots.size()) {
            return false;
        }
        Slot s = this.menu.slots.get(index);
        return s.getItem().isEmpty() && inRect(mx, my, s.x, s.y, 16, 16);
    }

    private void tankTooltip(List<Component> lines, String key, long amount, long capacity,
            GaugeMath.RateWindow window, boolean steamTank) {
        lines.add(GaugeTooltips.tr(key));
        if (this.menu.mode() != ReactorMode.STEAM) {
            lines.add(GaugeTooltips.tr("fission.steam_only"));
            return;
        }
        if (amount <= 0L) {
            lines.add(GaugeTooltips.tr("fission.tank_empty"));
        }
        lines.add(GaugeTooltips.tr("fission.tank_amount", amount, capacity));
        double perTick = window.perTick();
        if (perTick != 0D) {
            lines.add(GaugeTooltips.tr("fission.tank_rate", Math.round(perTick * GaugeMath.TICKS_PER_SECOND)));
        }
        if (steamTank && capacity > 0L && amount >= capacity) {
            lines.add(GaugeTooltips.tr("fission.steam_full"));
        }
    }

    private void modeTooltip(List<Component> lines) {
        lines.add(Component.translatable(this.menu.requestedMode().translationKey()));
        lines.add(GaugeTooltips.tr("fission.mode_tip"));
        if (this.menu.steamPorts() <= 0 && this.menu.requestedMode() == ReactorMode.DIRECT) {
            lines.add(GaugeTooltips.tr("fission.mode_needs_port"));
        } else if (this.menu.failureStageCode() >= FailureStage.WARNING.code()) {
            lines.add(GaugeTooltips.tr("fission.mode_hot"));
        }
        if (this.menu.requestedMode() == ReactorMode.STEAM && this.menu.mode() == ReactorMode.DIRECT) {
            lines.add(GaugeTooltips.tr("fission.mode_fallback"));
        }
        if (this.menu.cryoFed()) {
            lines.add(GaugeTooltips.tr("fission.cryo", GaugeTooltips.percentOf(this.menu.cryoBandPermille())));
        }
    }

    private void breedTooltip(List<Component> lines) {
        lines.add(GaugeTooltips.tr("fission.breeding"));
        if (this.menu.blankets() <= 0) {
            lines.add(Component.translatable("gui.neropower.fission_core.no_blankets"));
            return;
        }
        int permille = this.menu.breedPermille();
        lines.add(GaugeTooltips.tr("fission.breed_next", GaugeTooltips.percentOf(1000 - permille)));
        double perTick = this.breedWindow.perTick();
        if (perTick > 0D) {
            lines.add(GaugeTooltips.tr("fission.breed_eta", GaugeMath.duration(GaugeMath.ticksRemaining(permille, perTick))));
        }
        lines.add(GaugeTooltips.tr("fission.blankets", this.menu.blanketsCounted(), this.menu.blankets(),
                this.menu.blanketsMax()));
        lines.add(GaugeTooltips.tr("fission.breed_runs"));
    }

    private void stageTooltip(List<Component> lines) {
        lines.add(GaugeTooltips.tr("fission.stage_title"));
        FailureStage stage = FailureStage.byCode(this.menu.failureStageCode());
        lines.add(Component.translatable(stage.translationKey()));
        lines.add(GaugeTooltips.tr("fission.stage_heat", GaugeTooltips.percentOf(Math.round(this.menu.heatFraction() * 1000F))));
        lines.add(GaugeTooltips.tr("fission.stage_failure"));
    }

    private void burnupTooltip(List<Component> lines) {
        lines.add(GaugeTooltips.tr("fission.burnup"));
        int usable = this.menu.usableRodSlots();
        for (int slot = 0; slot < FissionCoreBlockEntity.ROD_SLOTS; slot++) {
            int n = slot + 1;
            if (slot >= usable) {
                lines.add(GaugeTooltips.tr("fission.rod_locked", n));
                continue;
            }
            if (this.menu.slots.get(slot).getItem().isEmpty()) {
                lines.add(GaugeTooltips.tr("fission.rod_empty", n));
                continue;
            }
            int burnup = this.menu.rodBurnup(slot);
            double perTick = this.rodWindows[slot].perTick();
            long left = perTick > 0 ? GaugeMath.ticksRemaining(burnup, perTick) : -1L;
            if (left >= 0) {
                lines.add(GaugeTooltips.tr("fission.rod_burning", n, GaugeTooltips.percentOf(burnup),
                        GaugeTooltips.percentPerMinute(perTick), GaugeMath.duration(left)));
            } else {
                lines.add(GaugeTooltips.tr("fission.rod", n, GaugeTooltips.percentOf(burnup)));
            }
        }
    }

    private void shellTooltip(List<Component> lines) {
        lines.add(GaugeTooltips.tr("fission.shell"));
        int shell = this.menu.shellSize();
        if (shell <= 0) {
            lines.add(Component.translatable("gui.neropower.fission_core.unformed"));
            return;
        }
        lines.add(GaugeTooltips.tr("fission.shell_size", shell, this.menu.usableRodSlots()));
        lines.add(GaugeTooltips.tr("fission.control", GaugeTooltips.percentOf(this.menu.controlRodPermille())));
        lines.add(GaugeTooltips.tr("fission.moderator", GaugeTooltips.percentOf(this.menu.moderatorPermille())));
        if (this.menu.blankets() > 0) {
            lines.add(GaugeTooltips.tr("fission.blanket_cost", this.menu.blankets(),
                    GaugeTooltips.percentOf(this.menu.blanketCostPermille())));
        }
        lines.add(GaugeTooltips.tr("fission.poison_amount", GaugeTooltips.percentOf(this.menu.poison())));
        double perTick = this.poisonWindow.perTick();
        double percentPerMinute = perTick * GaugeMath.TICKS_PER_SECOND * 60D / 10D;
        int trend = GaugeMath.trend(percentPerMinute, POISON_DEADBAND_PERCENT_PER_MINUTE);
        lines.add(trend > 0 ? GaugeTooltips.tr("fission.poison_rising", GaugeTooltips.percentPerMinute(perTick))
                : trend < 0 ? GaugeTooltips.tr("fission.poison_falling", GaugeTooltips.percentPerMinute(perTick))
                : GaugeTooltips.tr("fission.poison_steady"));
    }

    // --- input ---------------------------------------------------------------------------------------

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        double mx = mouseButtonEvent.x();
        double my = mouseButtonEvent.y();
    //?} else {
    /*@Override
    public boolean mouseClicked(double mx, double my, int button) {
    *///?}
        if (modeButtonEnabled() && inRect((int) mx - this.leftPos, (int) my - this.topPos, B1_X, B1_Y, B1_W, B1_H)) {
            if (this.minecraft != null && this.minecraft.gameMode != null) {
                int target = this.menu.requestedMode() == ReactorMode.STEAM ? 0 : 1;
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                        MenuButtons.encode(FissionCoreMenu.ACTION_MODE, target));
            }
            return true;
        }
        //? if >=1.21.9 {
        return super.mouseClicked(mouseButtonEvent, doubleClick);
        //?} else {
        /*return super.mouseClicked(mx, my, button);
        *///?}
    }
}

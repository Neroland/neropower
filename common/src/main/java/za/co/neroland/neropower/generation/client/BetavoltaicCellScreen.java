package za.co.neroland.neropower.generation.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.generation.BetavoltaicCellBlockEntity;
import za.co.neroland.neropower.generation.BetavoltaicCellMenu;
import za.co.neroland.neropower.generation.BetavoltaicCore;
import za.co.neroland.neropower.generation.GenerationReadout;

import static za.co.neroland.neropower.generation.client.GenerationScreenKit.*;

/**
 * Betavoltaic Cell screen (spec {@code betavoltaic_cell}, tall hull 176 × 186, solo energy): the energy
 * bar (NeroTech's energy tooltip), the capsule slot with its life ring, the day line, the output row and
 * the stall line. The ring and the output row share one tooltip provider. Values only from the menu.
 */
public class BetavoltaicCellScreen extends PowerMachineScreen<BetavoltaicCellMenu> {

    /** An in-game day, for the panel's day count (the tooltip gives real time). */
    private static final long DAY_TICKS = 24_000L;

    private static final int[] E = {8, 20, 10, 46};
    private static final int[] G1 = {77, 27, 22, 22};
    private static final int[] L1 = {34, 54, 100, 9};
    private static final int[] R1 = {8, 69, 160, 9};
    private static final int[] S = {8, BetavoltaicCellMenu.HEIGHT - 106, 160, 9};

    public BetavoltaicCellScreen(BetavoltaicCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, BetavoltaicCellMenu.WIDTH, BetavoltaicCellMenu.HEIGHT);
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
    protected long energyDeliveredPerTick() {
        return this.menu.outputNePerTick();
    }

    private float lifeLeft() {
        long max = this.menu.capsuleTicksMax();
        return max <= 0L ? 0F : Math.min(1F, (float) this.menu.capsuleTicksLeft() / max);
    }

    private long daysOf(long ticks) {
        return (ticks + DAY_TICKS - 1L) / DAY_TICKS;
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        BetavoltaicCellMenu m = this.menu;
        gauge(g, x + E[0], y + E[1], E[2], E[3], m.energyFraction(), ENERGY);
        int state = m.capsuleState();
        slotRing(g, x + BetavoltaicCellMenu.SLOT_X, y + BetavoltaicCellMenu.SLOT_Y,
                state == BetavoltaicCore.STATE_LIVE ? lifeLeft() : 0F, ENERGY, state == BetavoltaicCore.STATE_SPENT);

        Component day = dayLine();
        int w = this.font.width(day);
        g.text(this.font, day, x + L1[0] + Math.max(0, (L1[2] - w) / 2), y + L1[1],
                state == BetavoltaicCore.STATE_LIVE ? VALUE : SUBTLE, false);
        g.text(this.font, fit(this.font, outputLine(), R1[2]), x + R1[0], y + R1[1],
                m.readout() == GenerationReadout.WORK ? VALUE : SUBTLE, false);
        g.text(this.font, fit(this.font, stallText(), S[2]), x + S[0], y + S[1], SUBTLE, false);
    }

    private Component dayLine() {
        BetavoltaicCellMenu m = this.menu;
        return switch (m.capsuleState()) {
            case BetavoltaicCore.STATE_LIVE -> {
                long max = m.capsuleTicksMax();
                long used = Math.max(0L, max - m.capsuleTicksLeft());
                long total = Math.max(1L, daysOf(max));
                yield Component.translatable("gui.neropower.betavoltaic.day",
                        Math.min(total, used / DAY_TICKS + 1L), total);
            }
            case BetavoltaicCore.STATE_SPENT -> Component.translatable("gui.neropower.betavoltaic.spent");
            default -> Component.translatable("gui.neropower.betavoltaic.none");
        };
    }

    private Component outputLine() {
        BetavoltaicCellMenu m = this.menu;
        long days = m.capsuleState() == BetavoltaicCore.STATE_LIVE ? daysOf(m.capsuleTicksLeft()) : 0L;
        return Component.translatable("gui.neropower.betavoltaic.output", GaugeMath.signedRate(m.outputNePerTick()),
                Component.translatable("gui.neropower.betavoltaic.days", days));
    }

    private Component stallText() {
        BetavoltaicCellMenu m = this.menu;
        return switch (m.readout()) {
            case GenerationReadout.WORK -> Component.translatable("gui.neropower.betavoltaic.running",
                    GaugeMath.grouped(m.outputNePerTick()));
            case GenerationReadout.STALL -> switch (m.stallCause()) {
                case GenerationReadout.CAUSE_NO_FUEL -> Component.translatable(
                        m.capsuleState() == BetavoltaicCore.STATE_SPENT
                                ? "gui.neropower.betavoltaic.stall.spent" : "gui.neropower.betavoltaic.none");
                default -> Component.translatable("gui.neropower.stall." + stallKey(m.stallCause()));
            };
            default -> Component.translatable("gui.neropower.stall.idle");
        };
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        BetavoltaicCellMenu m = this.menu;
        if (over(mx, my, E[0], E[1], E[2], E[3])) {
            energyTooltip(lines);
            return;
        }
        Slot slot = this.menu.getSlot(BetavoltaicCellBlockEntity.CAPSULE_SLOT);
        boolean onSlot = over(mx, my, BetavoltaicCellMenu.SLOT_X, BetavoltaicCellMenu.SLOT_Y, 16, 16);
        if (onSlot && !slot.hasItem()) {
            lines.add(GaugeTooltips.tr("betavoltaic.slot_empty"));
            return;
        }
        boolean onRing = over(mx, my, G1[0], G1[1], G1[2], G1[3]) && !onSlot;
        if (onRing || over(mx, my, R1[0], R1[1], R1[2], R1[3]) || over(mx, my, L1[0], L1[1], L1[2], L1[3])) {
            lifeTooltip(lines, over(mx, my, R1[0], R1[1], R1[2], R1[3]));
        } else if (over(mx, my, S[0], S[1], S[2], S[3])) {
            String fix = m.stallCause() == GenerationReadout.CAUSE_NO_FUEL
                    ? (m.capsuleState() == BetavoltaicCore.STATE_SPENT ? "betavoltaic_spent" : "betavoltaic_none")
                    : m.stallCause() == GenerationReadout.CAUSE_BUFFER_FULL ? "buffer_full" : null;
            stallTooltip(lines, stallText(), m.readout(), m.stallCause(), fix);
        }
    }

    /** The capsule-life provider shared by the ring and the output row. */
    private void lifeTooltip(List<Component> lines, boolean withOutput) {
        BetavoltaicCellMenu m = this.menu;
        lines.add(GaugeTooltips.tr("betavoltaic.life"));
        if (m.capsuleState() == BetavoltaicCore.STATE_LIVE) {
            lines.add(GaugeTooltips.tr("betavoltaic.life.left", GaugeTooltips.percentOf(Math.round(lifeLeft() * 1000F))));
            lines.add(GaugeTooltips.tr("betavoltaic.life.until", GaugeMath.duration(m.capsuleTicksLeft())));
        } else {
            lines.add(Component.translatable(m.capsuleState() == BetavoltaicCore.STATE_SPENT
                    ? "gui.neropower.betavoltaic.spent" : "gui.neropower.betavoltaic.none"));
        }
        lines.add(GaugeTooltips.tr("betavoltaic.life.flat"));
        if (withOutput) {
            lines.add(GaugeTooltips.tr("betavoltaic.output", GaugeMath.grouped(m.outputNePerTick())));
        }
    }
}

package za.co.neroland.neropower.generation.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.generation.FuelCellCore;
import za.co.neroland.neropower.generation.FuelCellMenu;
import za.co.neroland.neropower.generation.GenerationReadout;

import static za.co.neroland.neropower.generation.client.GenerationScreenKit.*;

/**
 * Hydrogen Fuel Cell screen (spec {@code fuel_cell}, tall hull 176 × 186, solo energy): the energy bar
 * (answered by NeroTech's energy tooltip), hydrogen-in, oxygen-in and water-out tank bars, the output
 * line, the oxygen chip, the rate word, the recipe row and the stall line. Values only from the menu.
 */
public class FuelCellScreen extends PowerMachineScreen<FuelCellMenu> {

    private static final int[] E = {8, 20, 10, 46};
    private static final int[] T1 = {24, 20, 14, 46};
    private static final int[] T2 = {42, 20, 14, 46};
    private static final int[] T3 = {60, 20, 14, 46};
    private static final int[] L1 = {80, 21, 54, 9};
    private static final int[] C1 = {80, 33, 54, 11};
    private static final int[] L2 = {80, 47, 54, 9};
    private static final int[] R1 = {8, 69, 160, 9};
    private static final int[] S = {8, FuelCellMenu.HEIGHT - 106, 160, 9};

    private static final int CHIP_AIR = 0xFFE0B33A;

    public FuelCellScreen(FuelCellMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, FuelCellMenu.WIDTH, FuelCellMenu.HEIGHT);
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

    @Override
    protected void sampleReadout(long tick) {
        sampleTank(0, this.menu.hydrogenMb());
        sampleTank(1, this.menu.oxygenMb());
        sampleTank(2, this.menu.waterMb());
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        FuelCellMenu m = this.menu;
        gauge(g, x + E[0], y + E[1], E[2], E[3], m.energyFraction(), ENERGY);
        tank(g, x + T1[0], y + T1[1], T1[2], T1[3], m.hydrogenMb(), m.hydrogenCapacityMb(), HYDROGEN);
        tank(g, x + T2[0], y + T2[1], T2[2], T2[3], m.oxygenMb(), m.oxygenCapacityMb(), OXYGEN);
        tank(g, x + T3[0], y + T3[1], T3[2], T3[3], m.waterMb(), m.waterCapacityMb(), WATER);

        boolean working = m.readout() == GenerationReadout.WORK;
        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.fuel_cell.output",
                GaugeMath.signedRate(m.outputNePerTick())), L1[2]), x + L1[0], y + L1[1],
                working ? VALUE : SUBTLE, false);
        int mode = m.oxygenMode();
        chip(g, this.font, x + C1[0], y + C1[1], C1[2], chipText(mode),
                mode == FuelCellCore.MODE_FED ? OXYGEN : mode == FuelCellCore.MODE_AIR ? CHIP_AIR : SUBTLE);
        g.text(this.font, fit(this.font, rateWord(), L2[2]), x + L2[0], y + L2[1], working ? VALUE : SUBTLE, false);
        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.fuel_cell.recipe",
                GaugeMath.grouped(m.ticksPerUnit()), GaugeMath.grouped(m.nePerUnit()),
                GaugeMath.grouped(m.waterPerUnitMb())), R1[2]), x + R1[0], y + R1[1], SUBTLE, false);
        g.text(this.font, fit(this.font, stallText(), S[2]), x + S[0], y + S[1], SUBTLE, false);
    }

    private static Component chipText(int mode) {
        return Component.translatable(switch (mode) {
            case FuelCellCore.MODE_FED -> "gui.neropower.fuel_cell.o2_fed";
            case FuelCellCore.MODE_AIR -> "gui.neropower.fuel_cell.on_air";
            default -> "gui.neropower.fuel_cell.no_oxygen";
        });
    }

    private Component rateWord() {
        if (this.menu.readout() != GenerationReadout.WORK) {
            return Component.translatable("gui.neropower.fuel_cell.stopped");
        }
        return Component.translatable(this.menu.oxygenMode() == FuelCellCore.MODE_AIR
                ? "gui.neropower.fuel_cell.half_rate" : "gui.neropower.fuel_cell.full_rate");
    }

    private Component stallText() {
        FuelCellMenu m = this.menu;
        return switch (m.readout()) {
            case GenerationReadout.WORK -> m.oxygenMode() == FuelCellCore.MODE_AIR
                    ? Component.translatable("gui.neropower.fuel_cell.running_air", GaugeMath.grouped(m.outputNePerTick()))
                    : Component.translatable("gui.neropower.fuel_cell.running", GaugeMath.grouped(m.outputNePerTick()));
            case GenerationReadout.STALL -> switch (m.stallCause()) {
                case GenerationReadout.CAUSE_NO_FUEL -> Component.translatable("gui.neropower.fuel_cell.stall.no_hydrogen");
                case GenerationReadout.CAUSE_NO_OXYGEN -> Component.translatable("gui.neropower.fuel_cell.stall.no_oxygen");
                case GenerationReadout.CAUSE_OUTPUT_FULL -> Component.translatable("gui.neropower.fuel_cell.stall.water_full");
                default -> Component.translatable("gui.neropower.stall." + stallKey(m.stallCause()));
            };
            default -> Component.translatable("gui.neropower.stall.idle");
        };
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        FuelCellMenu m = this.menu;
        if (over(mx, my, E[0], E[1], E[2], E[3])) {
            energyTooltip(lines);
        } else if (over(mx, my, T1[0], T1[1], T1[2], T1[3])) {
            tankTooltip(lines, GaugeTooltips.tr("fuel_cell.hydrogen_in"), GaugeTooltips.tr("fuel_cell.hydrogen"),
                    m.hydrogenMb(), m.hydrogenCapacityMb(), tankWindow(0).perSecond(), m.gasUnitMb());
        } else if (over(mx, my, T2[0], T2[1], T2[2], T2[3])) {
            tankTooltip(lines, GaugeTooltips.tr("fuel_cell.oxygen_in"), GaugeTooltips.tr("fuel_cell.oxygen"),
                    m.oxygenMb(), m.oxygenCapacityMb(), tankWindow(1).perSecond(), m.gasUnitMb());
            lines.add(GaugeTooltips.tr("fuel_cell.oxygen_ratio"));
        } else if (over(mx, my, T3[0], T3[1], T3[2], T3[3])) {
            tankTooltip(lines, GaugeTooltips.tr("fuel_cell.water_out"), Component.translatable("block.minecraft.water"),
                    m.waterMb(), m.waterCapacityMb(), tankWindow(2).perSecond(), 0);
        } else if (over(mx, my, L1[0], L1[1], L1[2], L1[3]) || over(mx, my, L2[0], L2[1], L2[2], L2[3])) {
            lines.add(GaugeTooltips.tr("fuel_cell.output"));
            lines.add(GaugeTooltips.tr("fuel_cell.output.now", GaugeMath.grouped(m.outputNePerTick()),
                    GaugeMath.grouped(m.ratedNePerTick())));
        } else if (over(mx, my, C1[0], C1[1], C1[2], C1[3])) {
            lines.add(GaugeTooltips.tr("fuel_cell.oxygen_mode"));
            lines.add(GaugeTooltips.tr(switch (m.oxygenMode()) {
                case FuelCellCore.MODE_FED -> "fuel_cell.oxygen_mode.fed";
                case FuelCellCore.MODE_AIR -> "fuel_cell.oxygen_mode.air";
                default -> "fuel_cell.oxygen_mode.none";
            }));
        } else if (over(mx, my, R1[0], R1[1], R1[2], R1[3])) {
            lines.add(GaugeTooltips.tr("fuel_cell"));
            lines.add(GaugeTooltips.tr("fuel_cell.per_unit", GaugeMath.grouped(m.nePerUnit()),
                    GaugeMath.grouped(m.waterPerUnitMb())));
            lines.add(GaugeTooltips.tr("fuel_cell.net_sink"));
        } else if (over(mx, my, S[0], S[1], S[2], S[3])) {
            String fix = switch (m.stallCause()) {
                case GenerationReadout.CAUSE_NO_FUEL -> "fuel_cell_no_hydrogen";
                case GenerationReadout.CAUSE_NO_OXYGEN -> "fuel_cell_no_oxygen";
                case GenerationReadout.CAUSE_OUTPUT_FULL -> "fuel_cell_water_full";
                case GenerationReadout.CAUSE_BUFFER_FULL -> "buffer_full";
                default -> null;
            };
            stallTooltip(lines, stallText(), m.readout(), m.stallCause(), fix);
        }
    }
}

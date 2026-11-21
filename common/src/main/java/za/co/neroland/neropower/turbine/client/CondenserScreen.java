package za.co.neroland.neropower.turbine.client;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import za.co.neroland.neropower.client.GaugeMath;
import za.co.neroland.neropower.client.GaugeTooltips;
import za.co.neroland.neropower.client.PowerMachineScreen;
import za.co.neroland.neropower.turbine.CondenserMenu;
import za.co.neroland.neropower.turbine.TurbineReadout;

import static za.co.neroland.neropower.turbine.client.TurbineScreenKit.*;

/**
 * Steam Condenser screen (tall hull 176 x 186, spec {@code condenser}): exhaust-in, water-out and
 * cryo-in tank bars, the three heat-sink lines, the rate row and the stall line. No default gauges
 * (the condenser has no energy buffer a player needs). Values only from the menu's synced ints.
 */
public class CondenserScreen extends PowerMachineScreen<CondenserMenu> {

    private static final int[] T1 = {8, 20, 14, 46};
    private static final int[] T2 = {26, 20, 14, 46};
    private static final int[] T3 = {44, 20, 14, 46};
    private static final int[] L1 = {64, 21, 70, 9};
    private static final int[] L2 = {64, 33, 70, 9};
    private static final int[] L3 = {64, 45, 70, 9};
    private static final int[] R1 = {8, 69, 160, 9};
    private static final int[] S = {8, 80, 160, 9};

    public CondenserScreen(CondenserMenu menu, Inventory playerInventory, Component title) {
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

    @Override
    protected void sampleReadout(long tick) {
        sampleTank(0, this.menu.exhaustMb());
        sampleTank(1, this.menu.waterMb());
        sampleTank(2, this.menu.cryoMb());
    }

    @Override
    protected void extractReadout(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        CondenserMenu m = this.menu;
        boolean noSink = m.readout() == TurbineReadout.STALL && m.stallCause() == 6;
        tank(g, x + T1[0], y + T1[1], T1[2], T1[3], m.exhaustMb(), m.exhaustCapacityMb(), EXHAUST);
        tank(g, x + T2[0], y + T2[1], T2[2], T2[3], m.waterMb(), m.waterCapacityMb(), WATER);
        tank(g, x + T3[0], y + T3[1], T3[2], T3[3], m.cryoMb(), m.cryoCapacityMb(), CRYO);

        g.text(this.font, Component.translatable("gui.neropower.condenser.air",
                GaugeTooltips.percentOf(m.airFactorPermille())), x + L1[0], y + L1[1],
                m.airFactorPermille() > 0 && !noSink ? VALUE : SUBTLE, false);
        g.text(this.font, Component.translatable("gui.neropower.condenser.radiators", m.radiators()),
                x + L2[0], y + L2[1], m.radiators() > 0 && !noSink ? VALUE : SUBTLE, false);
        boolean cryo = m.cryoMb() > 0L;
        g.text(this.font, cryo
                ? Component.translatable("gui.neropower.condenser.cryo", GaugeMath.grouped(m.cryoMbPerTick()))
                : Component.translatable("gui.neropower.condenser.cryo_none"),
                x + L3[0], y + L3[1], cryo && !noSink ? VALUE : SUBTLE, false);
        g.text(this.font, fit(this.font, Component.translatable("gui.neropower.condenser.rate",
                GaugeMath.grouped(m.rateMbPerTick()), GaugeMath.grouped(m.possibleMbPerTick())), R1[2]),
                x + R1[0], y + R1[1], m.readout() == TurbineReadout.WORK ? VALUE : SUBTLE, false);
        g.text(this.font, fit(this.font, stallText(), S[2]), x + S[0], y + S[1], SUBTLE, false);
    }

    private Component stallText() {
        CondenserMenu m = this.menu;
        return switch (m.readout()) {
            case TurbineReadout.WORK -> Component.translatable("gui.neropower.condenser.running",
                    GaugeMath.grouped(m.rateMbPerTick()));
            case TurbineReadout.STALL -> switch (m.stallCause()) {
                case 3 -> Component.translatable("gui.neropower.condenser.stall.no_exhaust");
                case 7 -> Component.translatable("gui.neropower.condenser.stall.water_full");
                default -> Component.translatable("gui.neropower.stall." + stallKey(m.stallCause()));
            };
            default -> Component.translatable("gui.neropower.stall.idle");
        };
    }

    @Override
    protected void readoutTooltip(List<Component> lines, int mx, int my) {
        CondenserMenu m = this.menu;
        if (over(mx, my, T1[0], T1[1], T1[2], T1[3])) {
            tankTooltip(lines, GaugeTooltips.tr("condenser.exhaust_in"),
                    Component.translatable("fluid.neropower.exhaust_steam"), m.exhaustMb(), m.exhaustCapacityMb(),
                    tankWindow(0).perSecond());
        } else if (over(mx, my, T2[0], T2[1], T2[2], T2[3])) {
            tankTooltip(lines, GaugeTooltips.tr("condenser.water_out"), Component.translatable("block.minecraft.water"),
                    m.waterMb(), m.waterCapacityMb(), tankWindow(1).perSecond());
            if (m.waterCapacityMb() > 0L && m.waterMb() >= m.waterCapacityMb()) {
                lines.add(GaugeTooltips.tr("condenser.water_full"));
            }
        } else if (over(mx, my, T3[0], T3[1], T3[2], T3[3])) {
            tankTooltip(lines, GaugeTooltips.tr("condenser.cryo_in"), GaugeTooltips.tr("condenser.cryo_medium"),
                    m.cryoMb(), m.cryoCapacityMb(), tankWindow(2).perSecond());
            lines.add(GaugeTooltips.tr("condenser.cryo_ratio", GaugeMath.grouped(m.steamPerCryoMb())));
        } else if (over(mx, my, L1[0], L1[1], L1[2], L1[3])) {
            lines.add(GaugeTooltips.tr("condenser.air"));
            lines.add(GaugeTooltips.tr("condenser.air.factor", GaugeTooltips.percentOf(m.airFactorPermille())));
        } else if (over(mx, my, L2[0], L2[1], L2[2], L2[3])) {
            lines.add(GaugeTooltips.tr("condenser.radiators"));
            lines.add(GaugeTooltips.tr("condenser.radiators.count", m.radiators(),
                    GaugeMath.grouped(m.radiatorMbPerTick())));
        } else if (over(mx, my, L3[0], L3[1], L3[2], L3[3])) {
            lines.add(GaugeTooltips.tr("condenser.cryo"));
            lines.add(m.cryoMb() > 0L
                    ? GaugeTooltips.tr("condenser.cryo.using", GaugeMath.grouped(m.cryoMbPerTick()),
                            GaugeMath.grouped((long) m.cryoMbPerTick() * m.steamPerCryoMb()))
                    : GaugeTooltips.tr("condenser.cryo.none"));
        } else if (over(mx, my, R1[0], R1[1], R1[2], R1[3])) {
            lines.add(GaugeTooltips.tr("condenser.rate"));
            lines.add(GaugeTooltips.tr("condenser.rate.now", GaugeMath.grouped(m.rateMbPerTick()),
                    GaugeMath.grouped(m.possibleMbPerTick())));
            lines.add(GaugeTooltips.tr("condenser.rate.ratio"));
        } else if (over(mx, my, S[0], S[1], S[2], S[3])) {
            lines.add(GaugeTooltips.tr("status"));
            lines.add(stallText());
            if (m.readout() == TurbineReadout.STALL) {
                String fix = switch (m.stallCause()) {
                    case 3 -> "condenser_no_exhaust";
                    case 6 -> "condenser_no_heat_sink";
                    case 7 -> "condenser_water_full";
                    default -> null;
                };
                if (fix != null) {
                    lines.add(Component.translatable("gui.neropower.stall.fix." + fix));
                }
            }
        }
    }
}

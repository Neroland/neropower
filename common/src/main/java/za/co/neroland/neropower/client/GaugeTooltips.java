package za.co.neroland.neropower.client;

import java.util.List;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * The gauge tooltips for every NeroPower machine screen: each gauge names itself, gives amount /
 * capacity with units, and its rate. Builders append lines to a list; NeroTech's {@code MachineScreen}
 * emits them (the single emitter). Numbers come from {@link GaugeMath}; every word is an English source lang
 * key under {@code gui.neropower.gauge.}. Machine-scoped numbers only, no player data.
 */
public final class GaugeTooltips {

    /** Heat change per second treated as steady (sync jitter, ambient drift). */
    public static final double HEAT_DEADBAND_PER_SECOND = 0.5D;

    private GaugeTooltips() {
    }

    /** A {@code gui.neropower.gauge.<key>} line. */
    public static Component tr(String key, Object... args) {
        return Component.translatable("gui.neropower.gauge." + key, args);
    }

    // --- energy -----------------------------------------------------------------------------------

    /**
     * Energy: name, stored / capacity in NE, and the rate. With a known draw ({@code usedPerTick > 0})
     * the net change splits into "in" and "used"; otherwise the net change alone. {@code deliveredPerTick}
     * (what the machine hands on) adds its own line when non-negative.
     */
    public static void energy(List<Component> out, long stored, long capacity, double netPerTick,
            long usedPerTick, long deliveredPerTick) {
        out.add(tr("energy"));
        if (capacity <= 0) {
            out.add(tr("energy.no_buffer"));
            return;
        }
        out.add(tr("energy.amount", GaugeMath.compact(stored), GaugeMath.compact(capacity),
                GaugeMath.percent(stored, capacity)));
        if (usedPerTick > 0) {
            double in = Math.max(0D, netPerTick + usedPerTick);
            out.add(tr("energy.flow", GaugeMath.signedRate(in), GaugeMath.signedRate(-usedPerTick)));
        } else {
            out.add(tr("energy.net", GaugeMath.signedRate(netPerTick)));
        }
        if (deliveredPerTick >= 0) {
            out.add(tr("energy.delivered", GaugeMath.rate(deliveredPerTick)));
        }
        if (stored >= capacity) {
            out.add(tr("energy.full"));
        }
    }

    // --- heat -------------------------------------------------------------------------------------

    /**
     * Heat: name, value / capacity (and %), the safe band, the trend, and optionally the ambient and the
     * heat a source supplies ({@code null} to omit either).
     */
    public static void heat(List<Component> out, long heat, long capacity, long safeLimit, double perSecond,
            @Nullable Integer ambient, @Nullable Integer supplied) {
        out.add(tr("heat"));
        out.add(tr("heat.amount", GaugeMath.grouped(heat), GaugeMath.grouped(Math.max(0L, capacity)),
                GaugeMath.percent(heat, capacity)));
        if (safeLimit > 0) {
            out.add(heat >= safeLimit ? tr("heat.over", GaugeMath.grouped(safeLimit))
                    : tr("heat.safe", GaugeMath.grouped(safeLimit)));
        }
        int trend = GaugeMath.trend(perSecond, HEAT_DEADBAND_PER_SECOND);
        out.add(trend > 0 ? tr("heat.heating", GaugeMath.rate(perSecond))
                : trend < 0 ? tr("heat.cooling", GaugeMath.rate(perSecond)) : tr("heat.steady"));
        if (ambient != null) {
            out.add(tr("heat.ambient", GaugeMath.grouped(ambient)));
        }
        if (supplied != null && supplied > 0) {
            out.add(tr("heat.available", GaugeMath.grouped(supplied)));
        }
    }

    // --- progress ---------------------------------------------------------------------------------

    /**
     * Progress: name, percent, time left at the current speed (a falling gauge reads as fuel burning
     * down), the stall cause when one is given, and the operation rate when positive.
     */
    public static void progress(List<Component> out, boolean working, int permille, double permillePerTick,
            @Nullable Component stallCause, int opsPerMinute) {
        out.add(tr("progress"));
        if (!working) {
            out.add(stallCause != null ? stallCause : tr("progress.idle"));
            if (opsPerMinute > 0) {
                out.add(tr("progress.ops", GaugeMath.grouped(opsPerMinute)));
            }
            return;
        }
        out.add(tr("progress.percent", Math.max(0, Math.min(100, Math.round(permille / 10F)))));
        if (stallCause != null) {
            out.add(stallCause);
        } else {
            long ticks = GaugeMath.ticksRemaining(permille, permillePerTick);
            if (ticks >= 0) {
                out.add(tr(permillePerTick < 0 ? "progress.burn_left" : "progress.left", GaugeMath.duration(ticks)));
            }
        }
        if (opsPerMinute > 0) {
            out.add(tr("progress.ops", GaugeMath.grouped(opsPerMinute)));
        }
    }

    // --- permille meters (burn-up, poison, decay) -------------------------------------------------

    /** A permille value as a whole-percent string ({@code 42}). */
    public static String percentOf(int permille) {
        return Integer.toString(Math.max(0, Math.min(100, Math.round(permille / 10F))));
    }

    /** A permille-per-tick rate as percent per minute ({@code 2.5}). */
    public static String percentPerMinute(double permillePerTick) {
        return GaugeMath.rate(permillePerTick * GaugeMath.TICKS_PER_SECOND * 60D / 10D);
    }

    // --- stall line ---------------------------------------------------------------------------------

    /** The shared stall-cause table (gauge kit 5.2), by {@code stallCause} 0..15. */
    private static final String[] STALL_KEYS = {"none", "not_formed", "no_fuel", "no_steam", "no_water",
        "exhaust_blocked", "no_heat_sink", "output_full", "buffer_full", "no_power", "no_lens", "no_link",
        "beam_blocked", "no_coolant", "nothing_to_do", "locked_out"};

    /**
     * The stall line's fix ({@code gui.neropower.stall.fix.<cause>}), appended when the readout is STALL
     * (2) with a cause; nothing otherwise.
     */
    public static void stallFix(List<Component> out, int readout, int cause) {
        if (readout == 2 && cause > 0 && cause < STALL_KEYS.length) {
            out.add(Component.translatable("gui.neropower.stall.fix." + STALL_KEYS[cause]));
            out.add(Component.translatable("gui.neropower.guide.see_guide").withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    }

    // Emission is NeroTech's: MachineScreen is the single tooltip emitter for every NeroPower screen.
}

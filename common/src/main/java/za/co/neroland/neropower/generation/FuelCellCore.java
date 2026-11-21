package za.co.neroland.neropower.generation;

import java.util.Set;

/**
 * The Hydrogen Fuel Cell's rules, free of Minecraft types so they are unit-tested. One unit of hydrogen
 * ({@link #GAS_UNIT_MB} mB) starts a charge: with oxygen fed it takes half a unit of oxygen and runs
 * {@code ticksPerUnit} ticks; with no oxygen in the tank but breathable air here
 * ({@code EARTHLIKE} / {@code NITROGEN_OXYGEN}, from NeroTech's {@code PlanetApi.atmosphereAt}) it
 * takes no oxygen and runs twice as long for the same NE — half rate; with neither it does not start.
 * The charge's NE is spread exactly over its ticks, and the water is made when the charge starts. A
 * full energy buffer pauses a charge; it is never wasted. Persistence: {@link #progress()},
 * {@link #length()}, {@link #total()}, {@link #mode()}.
 */
public final class FuelCellCore {

    /** One balance unit of gas (NeroTech's unit). */
    public static final int GAS_UNIT_MB = 100;
    /** Oxygen per hydrogen unit: one unit of oxygen for every two of hydrogen. */
    public static final int OXYGEN_PER_UNIT_MB = GAS_UNIT_MB / 2;

    public static final int MODE_FED = 0;
    public static final int MODE_AIR = 1;
    public static final int MODE_NONE = 2;

    /** Atmospheres the cell can breathe (never NeroTech's deprecated {@code breathable()}). */
    private static final Set<String> BREATHABLE = Set.of("EARTHLIKE", "NITROGEN_OXYGEN");

    /** Ticks left in the running charge (0 = none). */
    private int progress;
    /** Ticks the running charge lasts in all. */
    private int length;
    /** NE the running charge gives in all. */
    private int total;
    /** Oxygen mode of the running charge, or of the last check when idle. */
    private int mode = MODE_NONE;

    /** What one tick asks the block entity to do. */
    public record Step(long drainHydrogen, long drainOxygen, long fillWater, int generate, boolean working,
            int cause, int mode) {
    }

    /** Whether the cell may breathe this atmosphere (by NeroTech {@code Atmosphere} enum name; null = no). */
    public static boolean breathable(String atmosphere) {
        return atmosphere != null && BREATHABLE.contains(atmosphere);
    }

    /** Oxygen mode for a would-be charge. */
    public static int modeFor(long oxygenMb, boolean breathableAir) {
        if (oxygenMb >= OXYGEN_PER_UNIT_MB) {
            return MODE_FED;
        }
        return breathableAir ? MODE_AIR : MODE_NONE;
    }

    /** Ticks one charge lasts in a mode (air doubles it; none is 0). */
    public static int chargeTicks(int mode, int ticksPerUnit) {
        int t = Math.max(1, ticksPerUnit);
        return switch (mode) {
            case MODE_FED -> t;
            case MODE_AIR -> t * 2;
            default -> 0;
        };
    }

    /** NE paid on tick {@code index} (0-based) of a {@code ticks}-long charge worth {@code total}: sums exactly. */
    public static int tickOutput(int total, int ticks, int index) {
        if (ticks <= 0 || index < 0 || index >= ticks) {
            return 0;
        }
        return (int) ((long) total * (index + 1) / ticks - (long) total * index / ticks);
    }

    /**
     * One tick.
     *
     * @param hydrogenMb    hydrogen in the tank
     * @param oxygenMb      oxygen in the tank
     * @param breathableAir whether the air here can be breathed
     * @param waterRoomMb   free space in the water tank
     * @param bufferRoom    free NE in the energy buffer
     */
    public Step tick(long hydrogenMb, long oxygenMb, boolean breathableAir, long waterRoomMb, long bufferRoom,
            int ticksPerUnit, int nePerUnit, int waterPerUnitMb) {
        if (this.progress <= 0) {
            int nextMode = modeFor(oxygenMb, breathableAir);
            this.mode = nextMode;
            if (hydrogenMb < GAS_UNIT_MB) {
                return new Step(0, 0, 0, 0, false, GenerationReadout.CAUSE_NO_FUEL, nextMode);
            }
            if (nextMode == MODE_NONE) {
                return new Step(0, 0, 0, 0, false, GenerationReadout.CAUSE_NO_OXYGEN, nextMode);
            }
            if (waterRoomMb < waterPerUnitMb) {
                return new Step(0, 0, 0, 0, false, GenerationReadout.CAUSE_OUTPUT_FULL, nextMode);
            }
            if (bufferRoom <= 0L) {
                return new Step(0, 0, 0, 0, false, GenerationReadout.CAUSE_BUFFER_FULL, nextMode);
            }
            this.length = chargeTicks(nextMode, ticksPerUnit);
            this.progress = this.length;
            this.total = Math.max(0, nePerUnit);
            int ne = payTick(bufferRoom);
            return new Step(GAS_UNIT_MB, nextMode == MODE_FED ? OXYGEN_PER_UNIT_MB : 0, Math.max(0, waterPerUnitMb),
                    ne, true, GenerationReadout.CAUSE_NONE, nextMode);
        }
        if (bufferRoom <= 0L || bufferRoom < nextPayment()) {
            return new Step(0, 0, 0, 0, false, GenerationReadout.CAUSE_BUFFER_FULL, this.mode);
        }
        int ne = payTick(bufferRoom);
        return new Step(0, 0, 0, ne, true, GenerationReadout.CAUSE_NONE, this.mode);
    }

    private int nextPayment() {
        return tickOutput(this.total, this.length, this.length - this.progress);
    }

    private int payTick(long bufferRoom) {
        int ne = nextPayment();
        if (ne > bufferRoom) {
            return 0;
        }
        this.progress--;
        return ne;
    }

    /** NE per tick of the running charge (rounded), 0 when idle. */
    public int outputNePerTick() {
        return this.progress > 0 && this.length > 0 ? Math.round((float) this.total / this.length) : 0;
    }

    public int progress() {
        return this.progress;
    }

    public int length() {
        return this.length;
    }

    public int total() {
        return this.total;
    }

    public int mode() {
        return this.mode;
    }

    /** Restore a saved charge (values clamped). */
    public void load(int progress, int length, int total, int mode) {
        this.length = Math.max(0, length);
        this.progress = Math.max(0, Math.min(progress, this.length));
        this.total = Math.max(0, total);
        this.mode = mode >= MODE_FED && mode <= MODE_NONE ? mode : MODE_NONE;
    }
}

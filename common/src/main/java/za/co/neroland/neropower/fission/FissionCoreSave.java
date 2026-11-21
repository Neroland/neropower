package za.co.neroland.neropower.fission;

import za.co.neroland.neropower.data.SaveKeys;

/**
 * The fission core's own saved fields (NeroTech's base and NeroPower's machine base write theirs
 * separately). Keys are the 0.3.x keys, unchanged: {@code ShellSize}, {@code RodCount},
 * {@code BurnupAcc}, {@code Poison}, {@code Stalled}, {@code ScramTicks}. New keys are additive only.
 */
public record FissionCoreSave(int shellSize, int rodCount, long burnupAccumulator, int poison, boolean stalled,
        int scramTicks) {

    /** Read with the 0.3.x clamps: burn-up never negative, SCRAM within 0..{@code SCRAM_TICKS}. */
    public static FissionCoreSave read(SaveKeys.Reader in) {
        return new FissionCoreSave(
                in.getInt("ShellSize", 0),
                in.getInt("RodCount", 0),
                Math.max(0L, in.getLong("BurnupAcc", 0L)),
                in.getInt("Poison", 0),
                in.getBoolean("Stalled", false),
                Math.max(0, Math.min(FissionCoreBlockEntity.SCRAM_TICKS, in.getInt("ScramTicks", 0))));
    }

    public void write(SaveKeys.Writer out) {
        out.putInt("ShellSize", this.shellSize);
        out.putInt("RodCount", this.rodCount);
        out.putLong("BurnupAcc", this.burnupAccumulator);
        out.putInt("Poison", this.poison);
        out.putBoolean("Stalled", this.stalled);
        out.putInt("ScramTicks", this.scramTicks);
    }
}

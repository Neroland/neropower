package za.co.neroland.neropower.machine;

import org.jetbrains.annotations.Nullable;

/**
 * The seam through which the client plays NeroPower's in-world machine sounds from each block entity's
 * synced readout and block state (no extra packets). Common code holds no client class: the client installs
 * a {@link Handler} during client setup and {@link NeroPowerMachineBlock#getTicker} gives client-side
 * machines a ticker that calls it, after NeroTech's own client ticker. On a dedicated server nothing is
 * installed. Machine-scoped state only; no player data.
 */
public final class PowerClientHook {

    /** Client-side effects for NeroPower machines (installed once by the client). */
    public interface Handler {

        /** Once per client tick for each loaded NeroPower machine. Integer compares; cheap. */
        void tick(NeroPowerMachineBlockEntity machine);
    }

    @Nullable
    private static volatile Handler handler;

    private PowerClientHook() {
    }

    /** Client setup: install the effects handler (idempotent). */
    public static void install(Handler effects) {
        handler = effects;
    }

    /** The installed handler, or null (always null on a dedicated server). */
    @Nullable
    public static Handler handler() {
        return handler;
    }
}

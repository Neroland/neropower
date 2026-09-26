package za.co.neroland.neropower.beam;

/**
 * The Configurator link decision ({@link BeamLinking}'s second click), Minecraft-free so every
 * branch is unit-tested. The caller gathers the facts — nothing here reads a level, config or player:
 * <ul>
 *   <li><b>Dimension</b> — same dimension, or a cross-dimension link only with
 *       {@code beamCrossDimension} on, onto an <i>orbital</i> receiver, in a <i>space</i> dimension.</li>
 *   <li><b>Loaded</b> — both endpoints are loaded block entities of the right kind (never
 *       force-loaded).</li>
 *   <li><b>Authorisation</b> — the acting player passes the protection seam's {@code mayInteract}
 *       at <i>both</i> endpoints.</li>
 * </ul>
 * Re-aiming an already-linked source additionally needs rewire authority (link owner / gamemaster),
 * which {@link BeamLinking} checks after an {@link Decision#OK}.
 */
public final class BeamLinkRules {

    /** Why a link was refused, in the order the checks run; {@link #OK} links. */
    public enum Decision {
        OK,
        /** Cross-dimension without the toggle, or not onto an orbital receiver in a space dimension. */
        CROSS_DIMENSION,
        /** The pending source (or the target) is no longer a loaded beam endpoint. */
        SOURCE_GONE,
        /** The player may not interact with one of the two endpoints. */
        DENIED
    }

    private BeamLinkRules() {
    }

    /** Whether the two ends' dimensions permit a link at all. */
    public static boolean dimensionAllowed(boolean sameDimension, boolean crossDimEnabled, boolean targetOrbital,
            boolean targetInSpace) {
        return sameDimension || (crossDimEnabled && targetOrbital && targetInSpace);
    }

    /**
     * The first reason to refuse, or {@link Decision#OK}.
     *
     * @param sourceLoaded       the pending source is a loaded transmitter / relay
     * @param targetLoaded       the clicked target is a loaded beam endpoint that accepts a beam
     * @param mayInteractSource  protection allows the player at the source
     * @param mayInteractTarget  protection allows the player at the target
     * @param sameDimension      both ends are in one dimension
     * @param crossDimEnabled    {@code beamCrossDimension}
     * @param targetOrbital      the target is an orbital receiver
     * @param targetInSpace      the target's dimension is a Core space dimension
     */
    public static Decision decide(boolean sourceLoaded, boolean targetLoaded, boolean mayInteractSource,
            boolean mayInteractTarget, boolean sameDimension, boolean crossDimEnabled, boolean targetOrbital,
            boolean targetInSpace) {
        if (!dimensionAllowed(sameDimension, crossDimEnabled, targetOrbital, targetInSpace)) {
            return Decision.CROSS_DIMENSION;
        }
        if (!sourceLoaded || !targetLoaded) {
            return Decision.SOURCE_GONE;
        }
        if (!mayInteractSource || !mayInteractTarget) {
            return Decision.DENIED;
        }
        return Decision.OK;
    }

    /** {@link #decide} reduced to yes / no. */
    public static boolean allowed(boolean sourceLoaded, boolean targetLoaded, boolean mayInteractSource,
            boolean mayInteractTarget, boolean sameDimension, boolean crossDimEnabled, boolean targetOrbital,
            boolean targetInSpace) {
        return decide(sourceLoaded, targetLoaded, mayInteractSource, mayInteractTarget, sameDimension,
                crossDimEnabled, targetOrbital, targetInSpace) == Decision.OK;
    }
}

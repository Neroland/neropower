package za.co.neroland.neropower.beam;

/**
 * A beam source with several outgoing links and <b>no recorded owner</b> (Beam Splitter, Phased Array
 * Emitter; decision 008 annex). {@link BeamLinking} adds links to it with the Configurator: the acting
 * player must pass the protection check at both ends at link time, which is the only authorisation;
 * links are removed from the block's screen. Same dimension only.
 */
public interface MultiBeamSource {

    /** Append a link; false when full or already linked. */
    boolean addLink(BeamTarget target);

    /** Range (blocks) a new link may span. */
    int linkRange();
}

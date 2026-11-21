package za.co.neroland.neropower.backend;

import net.minecraft.core.BlockPos;

/**
 * A loaded waste block that can be a radiation source: the Dry Cask while it holds spent rods or
 * fission residue, and a Spent Fuel Rack while its failure ladder sits at FAILURE. The back end only
 * reports; the contamination code (radiation package) decides what a source adds to its chunk and
 * keeps the in-memory source set the Geiger Counter reads. Emitters announce themselves through
 * {@link BackEndHooks#emitters()} when they first tick on the server and when they are removed or
 * unloaded.
 *
 * <p>World data only: a position and two numbers, never an owner or a player.
 */
public interface RadiationEmitter {

    /** Where the source is. */
    BlockPos emitterPos();

    /** Whether the source emits right now. */
    boolean emitting();

    /**
     * Faces of the block not covered by a {@code #neropower:radiation_barrier} block (0..6). A source
     * with 0 unshielded faces is fully enclosed and emits nothing outside.
     */
    int unshieldedFaces();
}

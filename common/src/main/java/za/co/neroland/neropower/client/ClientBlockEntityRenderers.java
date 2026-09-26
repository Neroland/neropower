package za.co.neroland.neropower.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.client.render.BatteryCellRenderer;
import za.co.neroland.neropower.client.render.BeamEndpointRenderer;
import za.co.neroland.neropower.client.render.FissionCoreRenderer;
import za.co.neroland.neropower.client.render.RtgRenderer;
import za.co.neroland.neropower.client.render.StirlingRenderer;
import za.co.neroland.neropower.environmental.EnvironmentalContent;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.storage.StorageContent;

/**
 * Cross-loader block-entity-renderer wiring (NeroTech's Sink seam, itself Nerospace's). The renderer
 * set is identical on every loader, so it lives here once and each loader passes its own registration
 * function ({@link Sink}) — NeoForge's and Forge's {@code EntityRenderersEvent.RegisterRenderers}
 * ({@code registerBlockEntityRenderer}), Fabric's {@code BlockEntityRendererRegistry.register}.
 * Common stays loader-import-free, and NeroPower's client never links NeroTech's client classes.
 */
public final class ClientBlockEntityRenderers {

    /** A loader's BER-registration entry point. */
    public interface Sink {
        <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
    }

    private ClientBlockEntityRenderers() {
    }

    public static void registerAll(Sink sink) {
        // Fission Core: pulsing core glow behind the front panel, rod-port indicator, alarm strobe.
        sink.register(FissionContent.FISSION_CORE_TYPE.get(), context -> new FissionCoreRenderer());
        // Battery Cells: the charge strip along the window foot (one BE type serves all three tiers).
        sink.register(StorageContent.BATTERY_CELL.get(), context -> new BatteryCellRenderer());
        // Beam endpoints: the aimed, spinning dish on every one; the ray from transmitters and relays.
        sink.register(BeamContent.BEAM_TRANSMITTER_BE.get(), context -> new BeamEndpointRenderer());
        sink.register(BeamContent.BEAM_RECEIVER_BE.get(), context -> new BeamEndpointRenderer());
        sink.register(BeamContent.BEAM_RELAY_BE.get(), context -> new BeamEndpointRenderer());
        sink.register(BeamContent.ORBITAL_RECEIVER_BE.get(), context -> new BeamEndpointRenderer());
        // RTG: decay-glow vanes between the fins, fading with the pellet.
        sink.register(EnvironmentalContent.RTG_TYPE.get(), context -> new RtgRenderer());
        // Stirling: the flywheel in the front cut-out, turning with the heat drawn.
        sink.register(EnvironmentalContent.STIRLING_TYPE.get(), context -> new StirlingRenderer());
    }
}

package za.co.neroland.neropower.client;

import java.util.function.Supplier;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
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
        //? if >=1.21.9 {
        <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider);
        //?} else {
        /*<T extends BlockEntity> void register(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider);
        *///?}
    }

    private ClientBlockEntityRenderers() {
    }

    /**
     * Registers one renderer. Before Minecraft 1.21.9 there is no render-state renderer API, so the
     * renderer (written against the client.render.legacy look-alikes there) is wrapped in an adapter.
     */
    private static <T extends BlockEntity, S extends BlockEntityRenderState> void add(Sink sink,
            BlockEntityType<? extends T> type, Supplier<? extends BlockEntityRenderer<T, S>> renderer) {
        //? if >=1.21.9 {
        sink.register(type, context -> renderer.get());
        //?} else {
        /*sink.register(type, context -> new za.co.neroland.neropower.client.render.legacy.LegacyBlockEntityRenderer<T, S>(renderer.get()));
        *///?}
    }

    public static void registerAll(Sink sink) {
        // Fission Core: pulsing core glow behind the front panel, rod-port indicator, alarm strobe.
        add(sink, FissionContent.FISSION_CORE_TYPE.get(), FissionCoreRenderer::new);
        // Battery Cells: the charge strip along the window foot (one BE type serves all three tiers).
        add(sink, StorageContent.BATTERY_CELL.get(), BatteryCellRenderer::new);
        // Beam endpoints: the aimed, spinning dish on every one; the ray from transmitters and relays.
        add(sink, BeamContent.BEAM_TRANSMITTER_BE.get(), BeamEndpointRenderer::new);
        add(sink, BeamContent.BEAM_RECEIVER_BE.get(), BeamEndpointRenderer::new);
        add(sink, BeamContent.BEAM_RELAY_BE.get(), BeamEndpointRenderer::new);
        add(sink, BeamContent.ORBITAL_RECEIVER_BE.get(), BeamEndpointRenderer::new);
        // RTG: decay-glow vanes between the fins, fading with the pellet.
        add(sink, EnvironmentalContent.RTG_TYPE.get(), RtgRenderer::new);
        // Stirling: the flywheel in the front cut-out, turning with the heat drawn.
        add(sink, EnvironmentalContent.STIRLING_TYPE.get(), StirlingRenderer::new);
    }
}

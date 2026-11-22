package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import za.co.neroland.neropower.client.ClientBlockEntityRenderers;
import za.co.neroland.neropower.fission.client.FissionCoreScreen;
import za.co.neroland.neropower.storage.client.BatteryCellScreen;
import za.co.neroland.neropower.storage.client.BankControllerScreen;
import za.co.neroland.neropower.beam.client.BeamScreen;
import za.co.neroland.neropower.environmental.client.RtgScreen;
import za.co.neroland.neropower.environmental.client.StirlingScreen;
import za.co.neroland.neropower.fission.FissionContent;
import za.co.neroland.neropower.storage.StorageContent;
import za.co.neroland.neropower.beam.BeamContent;
import za.co.neroland.neropower.environmental.EnvironmentalContent;

import za.co.neroland.neropower.NeroPowerCommon;

/** Fabric client entry point for NeroPower — registers the machine screens + block-entity renderers. */
public final class NeroPowerFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroPowerCommon.LOGGER.info("[NeroPower] Fabric client bootstrap");
        MenuScreens.register(FissionContent.FISSION_CORE_MENU.get(), FissionCoreScreen::new);
        MenuScreens.register(StorageContent.BATTERY_CELL_MENU.get(), BatteryCellScreen::new);
        MenuScreens.register(StorageContent.BATTERY_BANK_CONTROLLER_MENU.get(), BankControllerScreen::new);
        MenuScreens.register(BeamContent.BEAM_MENU.get(), BeamScreen::new);
        MenuScreens.register(EnvironmentalContent.RTG_MENU.get(), RtgScreen::new);
        MenuScreens.register(EnvironmentalContent.STIRLING_MENU.get(), StirlingScreen::new);

        za.co.neroland.neropower.client.NeroPowerClient.registerScreens(
                new za.co.neroland.neropower.client.NeroPowerClient.ScreenSink() {
                    @Override
                    public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
                            U extends net.minecraft.client.gui.screens.Screen
                                    & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
                            net.minecraft.world.inventory.MenuType<? extends M> type,
                            net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> constructor) {
                        MenuScreens.register(type, constructor);
                    }
                });

        registerFluidRendering();
        register040();

        // Machine BERs through the shared cross-loader seam (NeroTech / Nerospace pattern).
        ClientBlockEntityRenderers.registerAll(new ClientBlockEntityRenderers.Sink() {
            //? if >=1.21.9 {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                    BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }
            //?} else {
            /*@Override
            public <T extends BlockEntity> void register(
                    BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                BlockEntityRendererRegistry.register(type, provider);
            }
            *///?}
        });
    }

    /**
     * Sprites for NeroPower's fluids. They are never placed, so the sprites only show in tank and pipe
     * GUIs, but an unregistered fluid would draw the missing texture there. One model per fluid.
     */
    /**
     * NeroPower 0.4.0 client wiring: particle providers, render layers for the cutout turbine parts and the
     * translucent shielding glass, the Geiger HUD element, the client tick and the item-model properties.
     */
    private static void register040() {
        //? if >=26.1 {
        za.co.neroland.neropower.client.particle.PowerParticles.registerAll((type, factory) ->
                net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance()
                        .register(type, sprites -> factory.apply(sprites)));
        //?} else {
        /*za.co.neroland.neropower.client.particle.PowerParticles.registerAll((type, factory) ->
                net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry.getInstance()
                        .register(type, sprites -> factory.apply(sprites)));
        *///?}
        net.minecraft.world.level.block.Block[] cutout = {
                za.co.neroland.neropower.registry.content.TurbineContent.TURBINE_SHROUD.get(),
                za.co.neroland.neropower.registry.content.TurbineContent.TURBINE_SPINDLE.get(),
                za.co.neroland.neropower.registry.content.TurbineContent.TURBINE_STATOR.get()};
        net.minecraft.world.level.block.Block glass =
                za.co.neroland.neropower.registry.content.RadiationContent.SHIELDING_GLASS.get();
        //? if >=26.1 {
        // From 26.1 the chunk layer follows the sprites' transparency: nothing to register.
        java.util.Objects.requireNonNull(cutout);
        java.util.Objects.requireNonNull(glass);
        //?} else if >=1.21.6 {
        /*for (net.minecraft.world.level.block.Block b : cutout) {
            net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(b,
                    net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT);
        }
        net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap.putBlock(glass,
                net.minecraft.client.renderer.chunk.ChunkSectionLayer.TRANSLUCENT);
        *///?}
        //? if >=1.21.6 {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.attachElementBefore(
                net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements.CHAT,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "geiger_hud"),
                (graphics, delta) -> za.co.neroland.neropower.client.NeroPowerClient.hud(graphics,
                        delta.getGameTimeDeltaPartialTick(false)));
        //?} else {
        /*for (net.minecraft.world.level.block.Block b : cutout) {
            net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(b,
                    net.minecraft.client.renderer.RenderType.cutout());
        }
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(glass,
                net.minecraft.client.renderer.RenderType.translucent());
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, delta) -> za.co.neroland.neropower.client.NeroPowerClient.hud(graphics,
                        delta.getGameTimeDeltaPartialTick(false)));
        *///?}
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                mc -> za.co.neroland.neropower.client.NeroPowerClient.clientTick());
        za.co.neroland.neropower.client.NeroPowerClient.init();
    }

    private static void registerFluidRendering() {
        for (za.co.neroland.neropower.fluid.FluidSpec spec : za.co.neroland.neropower.fluid.FluidSpec.values()) {
            net.minecraft.resources.Identifier still = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    NeroPowerCommon.MOD_ID, spec.stillTexture());
            net.minecraft.resources.Identifier flow = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    NeroPowerCommon.MOD_ID, spec.flowTexture());
            net.minecraft.world.level.material.Fluid fluid = za.co.neroland.neropower.fluid.NeroPowerFluids.get(spec);
            //? if >=26.1 {
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(fluid,
                    new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                            new net.minecraft.client.resources.model.sprite.Material(still),
                            new net.minecraft.client.resources.model.sprite.Material(flow),
                            new net.minecraft.client.resources.model.sprite.Material(still),
                            net.minecraft.client.color.block.BlockTintSources.constant(spec.tint())));
            //?} else {
            /*net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.register(fluid,
                    new net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler(still, flow, spec.tint()));
            *///?}
        }
    }
}

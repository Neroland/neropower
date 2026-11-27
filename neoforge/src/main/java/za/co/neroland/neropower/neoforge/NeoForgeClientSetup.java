package za.co.neroland.neropower.neoforge;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=1.20.4 {
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//?}
//? if >=26.1 {
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
//?} else if >=1.21 {
/*import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
*///?}

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

/** NeoForge client-only wiring (machine screens + block-entity renderers). Loaded only behind Dist.CLIENT. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        //? if >=1.20.4 {
        modEventBus.addListener((RegisterMenuScreensEvent event) -> registerScreens(new za.co.neroland.neropower.client.NeroPowerClient.ScreenSink() {
            @Override
            public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
                    U extends net.minecraft.client.gui.screens.Screen
                            & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
                    net.minecraft.world.inventory.MenuType<? extends M> type,
                    net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> constructor) {
                event.register(type, constructor);
            }
        }));
        //?} else {
        /*// NeoForge 20.2 / 20.3 have no RegisterMenuScreensEvent: register on the client-setup work queue.
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) -> event.enqueueWork(() ->
                registerScreens(new za.co.neroland.neropower.client.NeroPowerClient.ScreenSink() {
            @Override
            public <M extends net.minecraft.world.inventory.AbstractContainerMenu,
                    U extends net.minecraft.client.gui.screens.Screen
                            & net.minecraft.client.gui.screens.inventory.MenuAccess<M>> void register(
                    net.minecraft.world.inventory.MenuType<? extends M> type,
                    net.minecraft.client.gui.screens.MenuScreens.ScreenConstructor<M, U> constructor) {
                net.minecraft.client.gui.screens.MenuScreens.register(type, constructor);
            }
        })));
        *///?}
        modEventBus.addListener(NeoForgeClientSetup::onRegisterEntityRenderers);
        // 0.4.0: particle providers, the Geiger HUD layer (under chat), the client tick, item-model properties.
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) ->
                za.co.neroland.neropower.client.particle.PowerParticles.registerAll((type, factory) ->
                        event.registerSpriteSet(type, factory::apply)));
        //? if >=1.20.5 {
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent event) ->
                event.registerBelow(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CHAT,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                za.co.neroland.neropower.NeroPowerCommon.MOD_ID, "geiger_hud"),
                        (graphics, delta) -> za.co.neroland.neropower.client.NeroPowerClient.hud(graphics,
                                delta.getGameTimeDeltaPartialTick(false))));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientTickEvent.Post event) ->
                        za.co.neroland.neropower.client.NeroPowerClient.clientTick());
        //?} else {
        /*modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent event) ->
                event.registerBelow(net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id(),
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(
                                za.co.neroland.neropower.NeroPowerCommon.MOD_ID, "geiger_hud"),
                        (gui, graphics, delta, width, height) ->
                                za.co.neroland.neropower.client.NeroPowerClient.hud(graphics, delta)));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.event.TickEvent.ClientTickEvent event) -> {
                    if (event.phase == net.neoforged.neoforge.event.TickEvent.Phase.END) {
                        za.co.neroland.neropower.client.NeroPowerClient.clientTick();
                    }
                });
        *///?}
        za.co.neroland.neropower.client.NeroPowerClient.init();
        //? if >=26.1 {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterFluidModels);
        //?} else if >=1.21 {
        /*modEventBus.addListener(NeoForgeClientSetup::onRegisterClientExtensions);
        *///?}
    }

    /**
     * Sprites for NeroPower's fluids. They are never placed, so the sprites only show in tank and pipe
     * GUIs, but an unregistered fluid would draw the missing texture there. One model per fluid.
     */
    //? if >=26.1 {
    private static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
        for (za.co.neroland.neropower.fluid.FluidSpec spec : za.co.neroland.neropower.fluid.FluidSpec.values()) {
            net.minecraft.client.resources.model.sprite.Material still = new net.minecraft.client.resources.model.sprite.Material(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.stillTexture()));
            net.minecraft.client.resources.model.sprite.Material flow = new net.minecraft.client.resources.model.sprite.Material(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.flowTexture()));
            event.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(still, flow, still,
                            net.neoforged.neoforge.client.fluid.FluidTintSources.constant(spec.tint())),
                    za.co.neroland.neropower.fluid.NeroPowerFluids.get(spec));
        }
    }
    //?} else if >=1.21 {
    /*private static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        NeoForgeFluidTypes.all().forEach((spec, type) -> event.registerFluidType(NeoForgeFluidTypes.extensions(spec), type.get()));
    }
    *///?}

    /** Machine BERs through the shared cross-loader seam (NeroTech / Nerospace pattern). */
    private static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        ClientBlockEntityRenderers.registerAll(new ClientBlockEntityRenderers.Sink() {
            //? if >=1.21.9 {
            @Override
            public <T extends BlockEntity, S extends BlockEntityRenderState> void register(
                    BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
            //?} else {
            /*@Override
            public <T extends BlockEntity> void register(
                    BlockEntityType<? extends T> type, BlockEntityRendererProvider<T> provider) {
                event.registerBlockEntityRenderer(type, provider);
            }
            *///?}
        });
    }

    private static void registerScreens(za.co.neroland.neropower.client.NeroPowerClient.ScreenSink sink) {
        sink.register(FissionContent.FISSION_CORE_MENU.get(), FissionCoreScreen::new);
        sink.register(StorageContent.BATTERY_CELL_MENU.get(), BatteryCellScreen::new);
        sink.register(StorageContent.BATTERY_BANK_CONTROLLER_MENU.get(), BankControllerScreen::new);
        sink.register(BeamContent.BEAM_MENU.get(), BeamScreen::new);
        sink.register(EnvironmentalContent.RTG_MENU.get(), RtgScreen::new);
        sink.register(EnvironmentalContent.STIRLING_MENU.get(), StirlingScreen::new);
        za.co.neroland.neropower.client.NeroPowerClient.registerScreens(sink);
    }
}

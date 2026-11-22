package za.co.neroland.neropower.forge;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
//? if >=1.21.6 {
import net.minecraftforge.eventbus.api.bus.BusGroup;
//?} else {
/*import net.minecraftforge.eventbus.api.IEventBus;
*///?}
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

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

/** Forge client-only wiring (machine screens + block-entity renderers). */
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    //? if >=1.21.9 {
    public static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onClientSetup);
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        //? if >=26.1 {
        net.minecraftforge.client.event.ModelEvent.BakeFluidModels.BUS.addListener(ForgeClientSetup::onBakeFluidModels);
        //?}
        net.minecraftforge.client.event.RegisterParticleProvidersEvent.BUS.addListener(ForgeClientSetup::onRegisterParticles);
        net.minecraftforge.client.event.AddGuiOverlayLayersEvent.BUS.addListener(ForgeClientSetup::onAddGuiLayers);
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(event ->
                za.co.neroland.neropower.client.NeroPowerClient.clientTick());
        za.co.neroland.neropower.client.NeroPowerClient.init();
    }
    //?}
    //? if >=1.21.6 && <1.21.9 {
    /*public static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onClientSetup);
        EntityRenderersEvent.RegisterRenderers.getBus(modBusGroup).addListener(ForgeClientSetup::onRegisterEntityRenderers);
        net.minecraftforge.client.event.RegisterParticleProvidersEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onRegisterParticles);
        net.minecraftforge.client.event.AddGuiOverlayLayersEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onAddGuiLayers);
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(event ->
                za.co.neroland.neropower.client.NeroPowerClient.clientTick());
        za.co.neroland.neropower.client.NeroPowerClient.init();
    }
    *///?}
    //? if <1.21.6 {
    /*public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientSetup::onClientSetup);
        modEventBus.addListener(ForgeClientSetup::onRegisterEntityRenderers);
        modEventBus.addListener(ForgeClientSetup::onRegisterParticles);
        modEventBus.addListener(ForgeClientSetup::onAddGuiLayers);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.event.TickEvent.ClientTickEvent.Post event) ->
                        za.co.neroland.neropower.client.NeroPowerClient.clientTick());
        za.co.neroland.neropower.client.NeroPowerClient.init();
    }
    *///?}

    /** 0.4.0 particle providers (sprite sets from {@code particles/<id>.json}). */
    private static void onRegisterParticles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        za.co.neroland.neropower.client.particle.PowerParticles.registerAll(
                (type, factory) -> event.registerSpriteSet(type, factory::apply));
    }

    /** The Geiger Counter HUD plate (drawn only while a counter is held). */
    private static void onAddGuiLayers(net.minecraftforge.client.event.AddGuiOverlayLayersEvent event) {
        event.getLayeredDraw().add(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                        za.co.neroland.neropower.NeroPowerCommon.MOD_ID, "geiger_hud"),
                (graphics, delta) -> za.co.neroland.neropower.client.NeroPowerClient.hud(graphics,
                        delta.getGameTimeDeltaPartialTick(false)));
    }

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

    /**
     * From 26.1 Forge takes a fluid's sprites from a baked fluid model, registered once per fluid
     * (NeroPower's fluids are never placed, so each has one model). Older lines use the types'
     * {@code initializeClient} extensions.
     */
    //? if >=26.1 {
    private static void onBakeFluidModels(net.minecraftforge.client.event.ModelEvent.BakeFluidModels event) {
        for (za.co.neroland.neropower.fluid.FluidSpec spec : za.co.neroland.neropower.fluid.FluidSpec.values()) {
            net.minecraft.client.resources.model.sprite.Material still = new net.minecraft.client.resources.model.sprite.Material(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.stillTexture()));
            net.minecraft.client.resources.model.sprite.Material flow = new net.minecraft.client.resources.model.sprite.Material(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(za.co.neroland.neropower.NeroPowerCommon.MOD_ID, spec.flowTexture()));
            net.minecraft.client.renderer.block.FluidModel model = new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                    still, flow, still, net.minecraft.client.color.block.BlockTintSources.constant(spec.tint()))
                    .bake(event.materials(), () -> za.co.neroland.neropower.NeroPowerCommon.MOD_ID + ":" + spec.id());
            event.register(za.co.neroland.neropower.fluid.NeroPowerFluids.get(spec), model);
        }
    }
    //?}

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ForgeClientSetup::registerScreens);
    }

    private static void registerScreens() {
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
    }
}

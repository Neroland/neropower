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

        registerFluidRendering();

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

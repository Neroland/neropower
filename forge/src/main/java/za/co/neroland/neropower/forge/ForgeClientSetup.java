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
    }
    //?}
    //? if >=1.21.6 && <1.21.9 {
    /*public static void init(BusGroup modBusGroup) {
        FMLClientSetupEvent.getBus(modBusGroup).addListener(ForgeClientSetup::onClientSetup);
        EntityRenderersEvent.RegisterRenderers.getBus(modBusGroup).addListener(ForgeClientSetup::onRegisterEntityRenderers);
    }
    *///?}
    //? if <1.21.6 {
    /*public static void init(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientSetup::onClientSetup);
        modEventBus.addListener(ForgeClientSetup::onRegisterEntityRenderers);
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
    }
}

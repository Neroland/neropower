package za.co.neroland.neropower.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import za.co.neroland.neropower.backend.client.DryCaskScreen;
import za.co.neroland.neropower.backend.client.SpentFuelPoolScreen;
import za.co.neroland.neropower.backend.client.VitrifierScreen;
import za.co.neroland.neropower.beam.client.BeamSplitterScreen;
import za.co.neroland.neropower.beam.client.PhasedArrayEmitterScreen;
import za.co.neroland.neropower.client.hud.GeigerHud;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.fission.client.ReactorControlComputerScreen;
import za.co.neroland.neropower.generation.client.BetavoltaicCellScreen;
import za.co.neroland.neropower.generation.client.FuelCellScreen;
import za.co.neroland.neropower.grid.client.CircuitBreakerScreen;
import za.co.neroland.neropower.grid.client.LoadBankScreen;
import za.co.neroland.neropower.grid.client.PowerMonitorScreen;
import za.co.neroland.neropower.radiation.client.GeigerReadModel;
import za.co.neroland.neropower.registry.content.BackEndContent;
import za.co.neroland.neropower.registry.content.FissionContent;
import za.co.neroland.neropower.registry.content.GenerationContent;
import za.co.neroland.neropower.registry.content.GridContent;
import za.co.neroland.neropower.registry.content.StorageContent;
import za.co.neroland.neropower.registry.content.TurbineContent;
import za.co.neroland.neropower.storage.client.MomentumWheelScreen;
import za.co.neroland.neropower.storage.client.SmesScreen;
import za.co.neroland.neropower.storage.client.SurgeBankScreen;
import za.co.neroland.neropower.turbine.client.CondenserScreen;
import za.co.neroland.neropower.turbine.client.TurbineHelmScreen;

/**
 * Loader-neutral client wiring for NeroPower 0.4.0, called from each loader's client entry point: the
 * sixteen new screens (through the loader's {@link ScreenSink}), the client tick (Geiger read model and the
 * HUD corner option), the Geiger HUD layer and the item-model properties. The 0.3.x screens stay bound in
 * the loader classes as before.
 */
public final class NeroPowerClient {

    /** A loader's menu-screen registration. */
    public interface ScreenSink {
        <M extends AbstractContainerMenu, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M> type,
                MenuScreens.ScreenConstructor<M, U> constructor);
    }

    private static String cornerText;

    private NeroPowerClient() {
    }

    /** Client setup that needs no event: item-model properties. */
    public static void init() {
        PowerItemProperties.register();
    }

    /** The 0.4.0 screens. */
    public static void registerScreens(ScreenSink sink) {
        sink.register(FissionContent.REACTOR_CONTROL_COMPUTER_MENU.get(), ReactorControlComputerScreen::new);
        sink.register(TurbineContent.TURBINE_HELM_MENU.get(), TurbineHelmScreen::new);
        sink.register(TurbineContent.CONDENSER_MENU.get(), CondenserScreen::new);
        sink.register(BackEndContent.SPENT_FUEL_POOL_MENU.get(), SpentFuelPoolScreen::new);
        sink.register(BackEndContent.VITRIFIER_MENU.get(), VitrifierScreen::new);
        sink.register(BackEndContent.DRY_CASK_MENU.get(), DryCaskScreen::new);
        sink.register(GenerationContent.FUEL_CELL_MENU.get(), FuelCellScreen::new);
        sink.register(GenerationContent.BETAVOLTAIC_CELL_MENU.get(), BetavoltaicCellScreen::new);
        sink.register(StorageContent.SURGE_BANK_MENU.get(), SurgeBankScreen::new);
        sink.register(StorageContent.MOMENTUM_WHEEL_MENU.get(), MomentumWheelScreen::new);
        sink.register(StorageContent.SMES_CONTROLLER_MENU.get(), SmesScreen::new);
        sink.register(GridContent.BEAM_SPLITTER_MENU.get(), BeamSplitterScreen::new);
        sink.register(GridContent.PHASED_ARRAY_EMITTER_MENU.get(), PhasedArrayEmitterScreen::new);
        sink.register(GridContent.CIRCUIT_BREAKER_MENU.get(), CircuitBreakerScreen::new);
        sink.register(GridContent.LOAD_BANK_MENU.get(), LoadBankScreen::new);
        sink.register(GridContent.POWER_MONITOR_MENU.get(), PowerMonitorScreen::new);
    }

    /** End of every client tick. */
    public static void clientTick() {
        String corner = NeroPowerConfig.geigerHudCorner();
        if (corner != null && !corner.equals(cornerText)) {
            cornerText = corner;
            GeigerHud.setCorner(GeigerHud.Corner.parse(corner));
        }
        GeigerReadModel.clientTick();
    }

    /** The Geiger HUD layer, once per HUD frame. */
    public static void hud(GuiGraphicsExtractor graphics, float partialTick) {
        GeigerHud.extract(graphics, partialTick);
    }
}

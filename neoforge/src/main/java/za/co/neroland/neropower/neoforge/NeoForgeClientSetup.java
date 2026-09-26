package za.co.neroland.neropower.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

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

/**
 * NeoForge client-only wiring (machine screens, later block-entity renderers). Loaded only behind
 * Dist.CLIENT. Empty until Stage 4 registers the first {@code MachineScreen} subclass.
 */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::onRegisterScreens);
    }

    private static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(FissionContent.FISSION_CORE_MENU.get(), FissionCoreScreen::new);
        event.register(StorageContent.BATTERY_CELL_MENU.get(), BatteryCellScreen::new);
        event.register(StorageContent.BATTERY_BANK_CONTROLLER_MENU.get(), BankControllerScreen::new);
        event.register(BeamContent.BEAM_MENU.get(), BeamScreen::new);
        event.register(EnvironmentalContent.RTG_MENU.get(), RtgScreen::new);
        event.register(EnvironmentalContent.STIRLING_MENU.get(), StirlingScreen::new);
    }
}

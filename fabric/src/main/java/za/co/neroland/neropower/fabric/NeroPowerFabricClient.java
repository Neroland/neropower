package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

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

/**
 * Fabric client entry point for NeroPower — registers the machine screens (and later block-entity
 * renderers). Empty until Stage 4 registers the first {@code MachineScreen} subclass.
 */
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
    }
}

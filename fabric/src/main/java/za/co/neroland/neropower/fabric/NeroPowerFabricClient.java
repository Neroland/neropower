package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ClientModInitializer;

import za.co.neroland.neropower.NeroPowerCommon;

/** Fabric client entry point for NeroPower. */
public final class NeroPowerFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        NeroPowerCommon.LOGGER.info("[NeroPower] Fabric client bootstrap");
    }
}

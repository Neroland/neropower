package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ModInitializer;

import za.co.neroland.neropower.NeroPowerCommon;

/** Fabric entry point for NeroPower. */
public final class NeroPowerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroPowerCommon.LOGGER.info("[NeroPower] Fabric bootstrap");
        NeroPowerCommon.init();
    }
}

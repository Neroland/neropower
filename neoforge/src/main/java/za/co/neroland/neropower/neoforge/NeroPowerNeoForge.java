package za.co.neroland.neropower.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import za.co.neroland.neropower.NeroPowerCommon;

/** NeoForge entry point for NeroPower. */
@Mod(NeroPowerCommon.MOD_ID)
public final class NeroPowerNeoForge {

    public NeroPowerNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroPowerCommon.LOGGER.info("[NeroPower] NeoForge bootstrap");
        NeroPowerCommon.init();
    }
}

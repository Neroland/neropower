package za.co.neroland.neropower.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import za.co.neroland.neropower.NeroPowerCommon;

/** MinecraftForge entry point for NeroPower. */
@Mod(NeroPowerCommon.MOD_ID)
public final class NeroPowerForge {

    public NeroPowerForge(FMLJavaModLoadingContext context) {
        NeroPowerCommon.LOGGER.info("[NeroPower] Forge bootstrap");
        NeroPowerCommon.init();
    }
}

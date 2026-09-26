package za.co.neroland.neropower.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fission.ScorchTicker;
import za.co.neroland.neropower.registry.ForgeRegistrationFactory;

/**
 * MinecraftForge entry point for NeroPower.
 *
 * <p>No capability wiring lives here on purpose: NeroTech's {@code ForgeCapabilities} attaches its
 * energy / item / gas / fluid providers to every block entity that is an
 * {@code instanceof NeroTechMachineBlockEntity} at creation time — which every NeroPower machine
 * is, through {@code NeroPowerMachineBlockEntity}. The {@code MachineTypeRegistry} registration in
 * {@code NeroPowerCommon.init()} is still made (it keeps the add-on loader-agnostic), but Forge
 * needs nothing from it. The creative tab, like every other registry entry, goes through the
 * {@code RegistrationProvider} seam (a {@code DeferredRegister} flushed to the mod bus group below).
 */
@Mod(NeroPowerCommon.MOD_ID)
public final class NeroPowerForge {

    public NeroPowerForge(FMLJavaModLoadingContext context) {
        NeroPowerCommon.LOGGER.info("[NeroPower] Forge bootstrap");
        BusGroup modBusGroup = context.getModBusGroup();
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam and registers
        // the machine types with NeroTech; attach the registers to NeroPower's mod bus group.
        NeroPowerCommon.init();
        ForgeRegistrationFactory.registerAll(modBusGroup);
        // Scorch zones (fission failure aftermath) expire and hurt on the server tick.
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> ScorchTicker.tick(event.server()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientSetup.init(modBusGroup);
        }
    }
}

package za.co.neroland.neropower.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.command.NeroPowerCommands;
import za.co.neroland.neropower.fission.ScorchTicker;
import za.co.neroland.neropower.registry.NeoForgeRegistrationFactory;

/**
 * NeoForge entry point for NeroPower.
 *
 * <p>No capability wiring lives here on purpose: {@code NeroPowerCommon.init()} registers every
 * NeroPower machine type with NeroTech's public {@code MachineTypeRegistry} from this constructor,
 * and NeroTech's own {@code RegisterCapabilitiesEvent} handler — which fires after <b>every</b> mod
 * constructor — reads that registry and exposes the energy / item surfaces for NeroTech's and
 * NeroPower's machines alike. Registering them again here would double-attach the same handlers.
 * The creative tab, like every other registry entry, goes through the {@code RegistrationProvider}
 * seam (a {@code DeferredRegister} flushed to the mod bus below).
 */
@Mod(NeroPowerCommon.MOD_ID)
public final class NeroPowerNeoForge {

    public NeroPowerNeoForge(IEventBus modEventBus, ModContainer modContainer) {
        NeroPowerCommon.LOGGER.info("[NeroPower] NeoForge bootstrap");
        // Shared init builds the DeferredRegisters via the RegistrationProvider seam and registers
        // the machine types with NeroTech; attach the registers to NeroPower's mod event bus.
        NeroPowerCommon.init();
        NeoForgeRegistrationFactory.registerAll(modEventBus);
        // Scorch zones (fission failure aftermath) expire and hurt on the server tick.
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> ScorchTicker.tick(event.getServer()));
        // Creative-only debug commands (/neropower gallery); shared brigadier tree in common.
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) ->
                NeroPowerCommands.register(event.getDispatcher()));
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            NeoForgeClientSetup.init(modEventBus);
        }
    }
}

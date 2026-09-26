package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.fission.ScorchTicker;

/**
 * Fabric entry point for NeroPower. Registration is eager (the {@code RegistrationProvider} seam
 * calls {@code Registry.register} directly), including the creative tab.
 *
 * <p>No capability wiring lives here on purpose: NeroTech's Fabric initializer subscribes to its
 * public {@code MachineTypeRegistry} with replaying listeners, so the machine types
 * {@code NeroPowerCommon.init()} registers are put on Core's energy lookup and the Fabric item
 * storage the moment they land — whether this initializer runs before or after NeroTech's.
 * Registering them again here would double-register the same lookups.
 */
public final class NeroPowerFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        NeroPowerCommon.LOGGER.info("[NeroPower] Fabric bootstrap");
        NeroPowerCommon.init();
        // Scorch zones (fission failure aftermath) expire and hurt on the server tick.
        ServerTickEvents.END_SERVER_TICK.register(ScorchTicker::tick);
    }
}

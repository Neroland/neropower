package za.co.neroland.neropower.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.command.NeroPowerCommands;
import za.co.neroland.neropower.fission.ScorchTicker;
import za.co.neroland.neropower.progression.QuestHooks;

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

    /**
     * Names, temperature and gaseousness of NeroPower's fluids in other mods' tank and pipe screens
     * (Fabric's counterpart to the FluidTypes NeoForge and Forge register).
     */
    private static void registerFluidAttributes() {
        for (za.co.neroland.neropower.fluid.FluidSpec spec : za.co.neroland.neropower.fluid.FluidSpec.values()) {
            net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.register(
                    za.co.neroland.neropower.fluid.NeroPowerFluids.get(spec),
                    new net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler() {
                        @Override
                        public net.minecraft.network.chat.Component getName(
                                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                            return net.minecraft.network.chat.Component.translatable(spec.translationKey());
                        }

                        @Override
                        public int getTemperature(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                            return spec.temperature();
                        }

                        @Override
                        public boolean isLighterThanAir(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant variant) {
                            return spec.gaseous();
                        }
                    });
        }
    }

    @Override
    public void onInitialize() {
        NeroPowerCommon.LOGGER.info("[NeroPower] Fabric bootstrap");
        NeroPowerCommon.init();
        registerFluidAttributes();
        // Scorch zones (fission failure aftermath) expire and hurt on the server tick.
        ServerTickEvents.END_SERVER_TICK.register(ScorchTicker::tick);
        // Quest hook delivery (pending hook gates on login) rides the same tick.
        ServerTickEvents.END_SERVER_TICK.register(QuestHooks::tick);
        // Creative-only debug commands (/neropower gallery); shared brigadier tree in common.
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                NeroPowerCommands.register(dispatcher));
    }
}

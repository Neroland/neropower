package za.co.neroland.neropower.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower 0.4.0's sound events (files under {@code assets/neropower/sounds/<group>/<name>.ogg}, entries in
 * {@code sounds.json}, subtitles {@code subtitles.neropower.<group>.<name>}). The three radiation events
 * ({@code geiger.click}, {@code geiger.chatter}, {@code decontaminant.spray}) are registered by
 * {@code registry.content.RadiationContent} and are not repeated here.
 */
public final class ModSounds {

    public static final RegistrationProvider<SoundEvent> SOUNDS =
            RegistrationProvider.get(Registries.SOUND_EVENT, NeroPowerCommon.MOD_ID);

    // --- steam plant ---
    public static final RegistryEntry<SoundEvent> TURBINE_SPOOL = sound("turbine.spool");
    public static final RegistryEntry<SoundEvent> TURBINE_RUN = sound("turbine.run");
    public static final RegistryEntry<SoundEvent> TURBINE_COAST = sound("turbine.coast");
    public static final RegistryEntry<SoundEvent> TURBINE_OVERSPEED = sound("turbine.overspeed");
    public static final RegistryEntry<SoundEvent> TURBINE_TRIP = sound("turbine.trip");
    public static final RegistryEntry<SoundEvent> STEAM_VENT = sound("steam.vent");
    public static final RegistryEntry<SoundEvent> CONDENSER_DRIP = sound("condenser.drip");
    // --- fission ---
    public static final RegistryEntry<SoundEvent> REACTOR_STEAM_MODE = sound("reactor.steam_mode");
    public static final RegistryEntry<SoundEvent> CONTROL_COMPUTER_ALARM = sound("control_computer.alarm");
    public static final RegistryEntry<SoundEvent> SCRAM_KEY_TURN = sound("scram_key.turn");
    // --- back end and generation ---
    public static final RegistryEntry<SoundEvent> VITRIFIER_POUR = sound("vitrifier.pour");
    public static final RegistryEntry<SoundEvent> CASK_SEAL = sound("cask.seal");
    public static final RegistryEntry<SoundEvent> POOL_BOIL = sound("pool.boil");
    public static final RegistryEntry<SoundEvent> FUEL_CELL_HISS = sound("fuel_cell.hiss");
    // --- storage ---
    public static final RegistryEntry<SoundEvent> SMES_HUM = sound("smes.hum");
    public static final RegistryEntry<SoundEvent> SMES_WARN = sound("smes.warn");
    public static final RegistryEntry<SoundEvent> SMES_QUENCH = sound("smes.quench");
    public static final RegistryEntry<SoundEvent> MOMENTUM_WHEEL_SPIN = sound("momentum_wheel.spin");
    public static final RegistryEntry<SoundEvent> SURGE_BANK_SNAP = sound("surge_bank.snap");
    // --- beam and grid ---
    public static final RegistryEntry<SoundEvent> BREAKER_TRIP = sound("breaker.trip");
    public static final RegistryEntry<SoundEvent> BREAKER_RESET = sound("breaker.reset");
    public static final RegistryEntry<SoundEvent> BREAKER_STRAIN = sound("breaker.strain");
    public static final RegistryEntry<SoundEvent> LOAD_BANK_SIZZLE = sound("load_bank.sizzle");
    public static final RegistryEntry<SoundEvent> BEAM_SPLIT = sound("beam.split");
    public static final RegistryEntry<SoundEvent> PHASED_ARRAY_STEP = sound("phased_array.step");

    private static RegistryEntry<SoundEvent> sound(String path) {
        Identifier id = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, path);
        return SOUNDS.register(path, key -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}

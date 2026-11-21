package za.co.neroland.neropower.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.registry.RegistrationProvider.RegistryEntry;

/**
 * NeroPower's particle types (definitions {@code assets/neropower/particles/<id>.json}, sprites
 * {@code textures/particle/<id>_<n>.png}). Each loader binds the client providers through
 * {@code client.particle.PowerParticles}; servers spawn them with {@code ServerLevel.sendParticles}.
 */
public final class ModParticles {

    public static final RegistrationProvider<ParticleType<?>> PARTICLE_TYPES =
            RegistrationProvider.get(Registries.PARTICLE_TYPE, NeroPowerCommon.MOD_ID);

    /** Short steam puff: turbine vent ports, the condenser, the reactor steam port. */
    public static final RegistryEntry<SimpleParticleType> STEAM_PUFF = register("steam_puff");
    /** Taller, longer steam plume: turbine exhaust and vent. */
    public static final RegistryEntry<SimpleParticleType> STEAM_PLUME = register("steam_plume");
    /** Storage Ring quench vapour (every fourth coil). */
    public static final RegistryEntry<SimpleParticleType> QUENCH_VAPOUR = register("quench_vapour");
    /** Spent Fuel Rack bubbles while it boils or is starved of water. */
    public static final RegistryEntry<SimpleParticleType> POOL_BUBBLE = register("pool_bubble");
    /** Radiation motes: contaminated chunks and hot rods on the ground. */
    public static final RegistryEntry<SimpleParticleType> RAD_MOTE = register("rad_mote");

    /** Every type id, in registration order. */
    public static final String[] IDS = {"steam_puff", "steam_plume", "quench_vapour", "pool_bubble", "rad_mote"};

    private static RegistryEntry<SimpleParticleType> register(String path) {
        // The constructor is protected outside NeoForge's access transformer: an empty subclass works everywhere.
        return PARTICLE_TYPES.register(path, key -> new SimpleParticleType(false) {
        });
    }

    private ModParticles() {
    }

    /** Force class-load so the static registrations run (eager on Fabric). */
    public static void init() {
    }
}

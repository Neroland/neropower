package za.co.neroland.neropower.client.particle;

import java.util.function.Function;

import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

import za.co.neroland.neropower.registry.ModParticles;

/**
 * Cross-loader particle-provider wiring: the provider set lives here once and each loader passes its own
 * registration ({@link Sink}): NeoForge and Forge {@code RegisterParticleProvidersEvent.registerSpriteSet},
 * Fabric's particle provider registry.
 */
public final class PowerParticles {

    /** A loader's sprite-set provider registration. */
    public interface Sink {
        void register(SimpleParticleType type, Function<SpriteSet, ParticleProvider<SimpleParticleType>> factory);
    }

    public static void registerAll(Sink sink) {
        sink.register(ModParticles.STEAM_PUFF.get(), s -> new PowerParticle.Provider(s, PowerParticle.Kind.STEAM_PUFF));
        sink.register(ModParticles.STEAM_PLUME.get(), s -> new PowerParticle.Provider(s, PowerParticle.Kind.STEAM_PLUME));
        sink.register(ModParticles.QUENCH_VAPOUR.get(),
                s -> new PowerParticle.Provider(s, PowerParticle.Kind.QUENCH_VAPOUR));
        sink.register(ModParticles.POOL_BUBBLE.get(), s -> new PowerParticle.Provider(s, PowerParticle.Kind.POOL_BUBBLE));
        sink.register(ModParticles.RAD_MOTE.get(), s -> new PowerParticle.Provider(s, PowerParticle.Kind.RAD_MOTE));
    }

    private PowerParticles() {
    }
}

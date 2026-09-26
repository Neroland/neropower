package za.co.neroland.neropower.registry;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import za.co.neroland.neropower.platform.Services;

/**
 * Cross-loader registration seam — the MultiLoader-Template alternative to
 * Architectury's {@code DeferredRegister} (which has not ported to 26.x).
 *
 * <p>Common code obtains a provider for a vanilla registry and registers
 * factories that receive the entry's {@link ResourceKey} — so the value can set
 * its own id ({@code Properties.setId(key)}, mandatory since 1.21.2). Each loader
 * ships one {@link Factory} implementation, resolved via {@link Services}
 * ({@link java.util.ServiceLoader}):
 * <ul>
 *   <li>NeoForge / Forge wrap a {@code DeferredRegister} (attached to NeroPower's
 *       mod bus by the loader entry point);</li>
 *   <li>Fabric calls {@code Registry.register} eagerly.</li>
 * </ul>
 *
 * <p>NeroPower ships its own copy of this seam rather than reusing Core's or
 * NeroTech's: their factories accumulate registers in a static list flushed to
 * <i>their</i> mod bus during <i>their</i> construction, before NeroPower's
 * {@code init()} runs.
 */
public interface RegistrationProvider<T> {

    static <T> RegistrationProvider<T> get(ResourceKey<? extends Registry<T>> registryKey, String modId) {
        return Factory.INSTANCE.create(registryKey, modId);
    }

    <I extends T> RegistryEntry<I> register(String name, Function<ResourceKey<T>, I> factory);

    /** A registered entry: a {@link Supplier} of the value plus its id. */
    interface RegistryEntry<R> extends Supplier<R> {
        @Override
        R get();

        Identifier id();
    }

    /** Loader-provided bridge to the platform registry. */
    interface Factory {
        Factory INSTANCE = Services.load(Factory.class);

        <T> RegistrationProvider<T> create(ResourceKey<? extends Registry<T>> registryKey, String modId);
    }
}

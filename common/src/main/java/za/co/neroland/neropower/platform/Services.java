package za.co.neroland.neropower.platform;

import java.util.ServiceLoader;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Loads loader-specific service implementations via {@link ServiceLoader} — the
 * lightweight, dependency-free alternative to Architectury's {@code @ExpectPlatform}.
 *
 * <p>Common code resolves a per-loader implementation from the {@code META-INF/services}
 * entry shipped by each loader module (Fabric / Forge / NeoForge). NeroPower keeps its
 * own seam (mirroring NeroTech) rather than reusing Core's or NeroTech's: their NeoForge /
 * Forge registration factories flush {@code DeferredRegister}s to <i>their</i> mod bus
 * during <i>their</i> construction — NeroPower must attach to NeroPower's mod bus at the
 * right time. Currently the only service is the registration factory; grow as needed.
 */
public final class Services {

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loaded = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "No implementation found for service " + clazz.getName()));
        NeroPowerCommon.LOGGER.debug("Loaded service {} -> {}",
                clazz.getSimpleName(), loaded.getClass().getName());
        return loaded;
    }
}

package za.co.neroland.neropower.balance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.neropower.backend.BackEndConfig;
import za.co.neroland.neropower.beam.BeamConfig;
import za.co.neroland.neropower.config.NeroPowerConfig;
import za.co.neroland.neropower.environmental.EnvironmentalConfig;
import za.co.neroland.neropower.fission.FissionConfig;
import za.co.neroland.neropower.generation.GenerationConfig;
import za.co.neroland.neropower.grid.GridConfig;
import za.co.neroland.neropower.radiation.RadiationConfig;
import za.co.neroland.neropower.storage.StorageConfig;
import za.co.neroland.neropower.turbine.TurbineConfig;

/**
 * Test-only access to Core's {@link ConfigValue} internals: its range sanitiser (to read a key's bounds,
 * which Core does not expose) and its package-private {@code loadFrom} / {@code resetToDefault} (to set a
 * key the way a hand-edited {@code neropower.properties} would, then put it back).
 */
final class ConfigProbe {

    private ConfigProbe() {
    }

    /** Class-load every family config so its keys join the shared schema, then list them. */
    static List<ConfigValue<?>> allValues() {
        FissionConfig.init();
        StorageConfig.init();
        BeamConfig.init();
        EnvironmentalConfig.init();
        RadiationConfig.init();
        TurbineConfig.init();
        BackEndConfig.init();
        GenerationConfig.init();
        GridConfig.init();
        return new ArrayList<>(NeroPowerConfig.schema().values());
    }

    static ConfigValue<?> value(String key) {
        allValues();
        ConfigValue<?> v = NeroPowerConfig.schema().value(key);
        if (v == null) {
            throw new AssertionError("no config key " + key);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    static <T> T sanitize(ConfigValue<T> value, T raw) {
        try {
            Field f = ConfigValue.class.getDeclaredField("sanitizer");
            f.setAccessible(true);
            return ((UnaryOperator<T>) f.get(value)).apply(raw);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Core's ConfigValue changed shape", e);
        }
    }

    /** Load {@code raw} into {@code key} as the config file would. */
    static void set(String key, String raw) {
        invoke(value(key), "loadFrom", raw);
    }

    static void reset(String key) {
        invoke(value(key), "resetToDefault", null);
    }

    static void invoke(ConfigValue<?> value, String method, String arg) {
        try {
            Method m = arg == null ? ConfigValue.class.getDeclaredMethod(method)
                    : ConfigValue.class.getDeclaredMethod(method, String.class);
            m.setAccessible(true);
            if (arg == null) {
                m.invoke(value);
            } else {
                m.invoke(value, arg);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Core's ConfigValue changed shape", e);
        }
    }
}

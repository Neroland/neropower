package za.co.neroland.neropower.data;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Adapts a block entity's {@link ValueInput} / {@link ValueOutput} to {@link SaveKeys}. */
public final class SaveAdapters {

    private SaveAdapters() {
    }

    public static SaveKeys.Reader reader(ValueInput input) {
        return new SaveKeys.Reader() {
            @Override
            public int getInt(String key, int fallback) {
                return input.getIntOr(key, fallback);
            }

            @Override
            public long getLong(String key, long fallback) {
                return input.getLongOr(key, fallback);
            }

            @Override
            public boolean getBoolean(String key, boolean fallback) {
                return input.getBooleanOr(key, fallback);
            }

            @Override
            public String getString(String key, String fallback) {
                return input.getStringOr(key, fallback);
            }
        };
    }

    public static SaveKeys.Writer writer(ValueOutput output) {
        return new SaveKeys.Writer() {
            @Override
            public void putInt(String key, int value) {
                output.putInt(key, value);
            }

            @Override
            public void putLong(String key, long value) {
                output.putLong(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                output.putBoolean(key, value);
            }

            @Override
            public void putString(String key, String value) {
                output.putString(key, value);
            }
        };
    }
}

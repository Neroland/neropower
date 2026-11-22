package za.co.neroland.neropower.data;

import java.util.Map;
import java.util.UUID;

/**
 * Plain key/value views of a block entity's saved data, so NeroPower's save formats can be read and
 * written by pure code (and tested against fixtures without starting Minecraft). Block entities adapt
 * their {@code ValueInput} / {@code ValueOutput} through {@link SaveAdapters}.
 */
public final class SaveKeys {

    private SaveKeys() {
    }

    /** Reads keys with defaults for absent ones. */
    public interface Reader {
        int getInt(String key, int fallback);

        long getLong(String key, long fallback);

        boolean getBoolean(String key, boolean fallback);

        String getString(String key, String fallback);
    }

    /** Writes keys. */
    public interface Writer {
        void putInt(String key, int value);

        void putLong(String key, long value);

        void putBoolean(String key, boolean value);

        void putString(String key, String value);
    }

    /** A reader over plain Java values (tests and fixtures): Number, Boolean / byte, String. */
    public static Reader of(Map<String, ?> values) {
        return new Reader() {
            @Override
            public int getInt(String key, int fallback) {
                return values.get(key) instanceof Number n ? n.intValue() : fallback;
            }

            @Override
            public long getLong(String key, long fallback) {
                return values.get(key) instanceof Number n ? n.longValue() : fallback;
            }

            @Override
            public boolean getBoolean(String key, boolean fallback) {
                Object v = values.get(key);
                if (v instanceof Boolean b) {
                    return b;
                }
                return v instanceof Number n ? n.intValue() != 0 : fallback;
            }

            @Override
            public String getString(String key, String fallback) {
                return values.get(key) instanceof String s ? s : fallback;
            }
        };
    }

    /** A writer into a plain map (tests). */
    public static Writer into(Map<String, Object> values) {
        return new Writer() {
            @Override
            public void putInt(String key, int value) {
                values.put(key, value);
            }

            @Override
            public void putLong(String key, long value) {
                values.put(key, value);
            }

            @Override
            public void putBoolean(String key, boolean value) {
                values.put(key, value);
            }

            @Override
            public void putString(String key, String value) {
                values.put(key, value);
            }
        };
    }

    // --- the beam link owner: the only player identifier NeroPower records --------------------------

    /** NBT keys holding the link owner's UUID halves (world save only, never synced to clients). */
    public static final String LINK_OWNER_MOST = "LinkOwnerMost";
    public static final String LINK_OWNER_LEAST = "LinkOwnerLeast";

    /** The saved link owner, or null when none (both halves 0). */
    public static UUID readLinkOwner(Reader in) {
        long most = in.getLong(LINK_OWNER_MOST, 0L);
        long least = in.getLong(LINK_OWNER_LEAST, 0L);
        return most == 0L && least == 0L ? null : new UUID(most, least);
    }

    /**
     * NBT key of the UTC epoch day the current link was made (non-personal on its own). Erasure compares it
     * with the tombstone day, so a player's links made after their erasure keep their owner. Absent in
     * pre-0.4.0 saves: reads as {@link NeroPowerErasureState#UNKNOWN_LINK_DAY} (older than any erasure).
     */
    public static final String LINK_DAY = "LinkDay";

    /** The saved link day, or {@link NeroPowerErasureState#UNKNOWN_LINK_DAY}. */
    public static long readLinkDay(Reader in) {
        return in.getLong(LINK_DAY, NeroPowerErasureState.UNKNOWN_LINK_DAY);
    }

    /** Write the link day. */
    public static void writeLinkDay(Writer out, long day) {
        out.putLong(LINK_DAY, day);
    }

    /** Write the link owner (zeros when none). */
    public static void writeLinkOwner(Writer out, UUID owner) {
        out.putLong(LINK_OWNER_MOST, owner == null ? 0L : owner.getMostSignificantBits());
        out.putLong(LINK_OWNER_LEAST, owner == null ? 0L : owner.getLeastSignificantBits());
    }
}

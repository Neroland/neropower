package za.co.neroland.neropower.radiation;

/**
 * The radiation rules, Minecraft-free so each is unit-tested: chunk keys, the effect tiers and the
 * 25 % per shielding piece reduction, source emission (an enclosed source emits nothing), bursts, the
 * dosimeter's running total, the exposure bands and the Geiger click rate. Exposure unit: the
 * <b>pall</b> (pl), one point of chunk level (0..1 000).
 */
public final class RadiationMath {

    /** Weakness from this chunk level. */
    public static final int WEAKNESS_AT = 200;
    /** Hunger as well from this level. */
    public static final int HUNGER_AT = 500;
    /** Radiation damage as well from this level. */
    public static final int DAMAGE_AT = 800;
    /** Each worn shielding piece removes this share (permille) of the level an entity feels. */
    public static final int PIECE_PERMILLE = 250;
    /** Effect duration, long enough to bridge two passes at the slowest interval. */
    public static final int EFFECT_TICKS = 60;
    /** Damage per pass at {@link #DAMAGE_AT} or above. */
    public static final float DAMAGE = 1.0F;
    /** Highest dosimeter reading. */
    public static final int DOSIMETER_CAP = 1_000_000;
    /** Ticks between dosimeter readings. */
    public static final int DOSIMETER_INTERVAL = 40;
    /** Ticks between Geiger updates. */
    public static final int GEIGER_INTERVAL = 20;
    /** Ticks between dropped-item emission passes. */
    public static final int DROPPED_INTERVAL = 100;
    /** Blocks around the holder in which the Geiger counts registered sources. */
    public static final int GEIGER_SOURCE_RANGE = 16;
    /** Highest source count the Geiger reports ("99+"). */
    public static final int GEIGER_SOURCE_CAP = 99;

    private RadiationMath() {
    }

    /** The most dropped rods and residue may add to one chunk in one pass, however many lie there. */
    public static final int DROPPED_CHUNK_CAP = 40;

    /** What the dropped items in one chunk add this pass: their sum, capped at {@link #DROPPED_CHUNK_CAP}. */
    public static int droppedCap(int sum) {
        return Math.max(0, Math.min(DROPPED_CHUNK_CAP, sum));
    }

    /** {@code ChunkPos.toLong()} for chunk coordinates (same packing, no Minecraft class needed). */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** The chunk key of a block position. */
    public static long chunkKeyOfBlock(int blockX, int blockZ) {
        return chunkKey(blockX >> 4, blockZ >> 4);
    }

    public static int chunkX(long key) {
        return (int) key;
    }

    public static int chunkZ(long key) {
        return (int) (key >>> 32);
    }

    /** The level an entity wearing {@code pieces} shielding pieces feels: 25 % off per piece, 4 = none. */
    public static int effectiveLevel(int level, int pieces) {
        int worn = Math.max(0, Math.min(4, pieces));
        return (int) ((long) Math.max(0, level) * (1000 - worn * PIECE_PERMILLE) / 1000L);
    }

    /**
     * The effect tier for one entity: 0 none, 1 Weakness, 2 + Hunger, 3 + damage. Radiation off means
     * tier 0 whatever the level.
     */
    public static int effectTier(boolean enabled, int chunkLevel, int shieldingPieces) {
        if (!enabled) {
            return 0;
        }
        int felt = effectiveLevel(chunkLevel, shieldingPieces);
        if (felt >= DAMAGE_AT) {
            return 3;
        }
        if (felt >= HUNGER_AT) {
            return 2;
        }
        return felt >= WEAKNESS_AT ? 1 : 0;
    }

    /** What a source adds per emission pass: {@code perFace} for every open face; 0 open faces = nothing. */
    public static int emission(boolean emitting, int unshieldedFaces, int perFace) {
        if (!emitting) {
            return 0;
        }
        return Math.max(0, Math.min(6, unshieldedFaces)) * Math.max(0, perFace);
    }

    /** The share of a reactor burst each of the eight neighbouring chunks gets (a third of the centre). */
    public static int neighbourBurst(int burst) {
        return Math.max(0, burst) / 3;
    }

    /** A badge's reading after one interval in a chunk at {@code level}: + level / 10, capped. */
    public static int dosimeterAdd(int reading, int level) {
        long next = (long) Math.max(0, reading) + Math.max(0, level) / 10;
        return (int) Math.min(DOSIMETER_CAP, next);
    }

    /** The badge's strip window step 0..4 (under 1k, 10k, 100k, 500k, then saturated). */
    public static int dosimeterStep(int reading) {
        if (reading < 1_000) {
            return 0;
        }
        if (reading < 10_000) {
            return 1;
        }
        if (reading < 100_000) {
            return 2;
        }
        return reading < 500_000 ? 3 : 4;
    }

    /** Exposure band 0..4 (Clear, Trace, Raised, High, Severe) at 10 / 200 / 500 / 800 pl. */
    public static int band(int level) {
        if (level >= DAMAGE_AT) {
            return 4;
        }
        if (level >= HUNGER_AT) {
            return 3;
        }
        if (level >= WEAKNESS_AT) {
            return 2;
        }
        return level >= 10 ? 1 : 0;
    }

    /** The Geiger Counter's item-model frame 0..2 by band (Clear/Trace, Raised, High/Severe). */
    public static int geigerFrame(int level) {
        int band = band(level);
        return band <= 1 ? 0 : band == 2 ? 1 : 2;
    }

    /** Clicks per second at {@code level}: 0.2 x 100^(level / 1000) (0.2 at 0, 2 at 500, 20 at 1 000). */
    public static double clickRate(int level) {
        int clamped = Math.max(0, Math.min(ContaminationMap.MAX_LEVEL, level));
        return 0.2D * Math.pow(100.0D, clamped / 1000.0D);
    }
}

package za.co.neroland.neropower.radiation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import za.co.neroland.nerolandcore.data.SavedDataCompat;
import za.co.neroland.nerolandcore.data.SavedDataRecovery;

import za.co.neroland.neropower.NeroPowerCommon;

/**
 * Chunk contamination for one dimension, saved in that dimension's data storage as
 * {@code neropower_contamination} and obtained <b>only</b> through Core's
 * {@link SavedDataRecovery#get}, so a corrupt file is moved aside instead of crashing the server.
 * World data: chunk position and level, no owner, no player, nothing else. Serialised as
 * {@code Chunks: [{P: long, L: short}]}. The logic lives in {@link ContaminationMap}.
 */
public final class ContaminationState extends SavedData {

    public static final Identifier ID = Identifier.fromNamespaceAndPath(NeroPowerCommon.MOD_ID, "contamination");

    /** Stable label for the recovery guard's logs and backup file name (no identifiers). */
    public static final String RECOVERY_NAME = "neropower:contamination";

    public static final SavedDataType<ContaminationState> TYPE =
            SavedDataCompat.type(ID, ContaminationState::new, codec());

    private record Entry(long chunk, short level) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.LONG.fieldOf("P").forGetter(Entry::chunk),
                Codec.SHORT.fieldOf("L").forGetter(Entry::level)
        ).apply(inst, Entry::new));
    }

    private final ContaminationMap map = new ContaminationMap();

    public ContaminationState() {
    }

    /** This dimension's store, through Core's recovery guard. */
    public static ContaminationState get(ServerLevel level) {
        return SavedDataRecovery.get(level, TYPE, ContaminationState::new, RECOVERY_NAME);
    }

    /** The map (read-only use; mutate through this store so it is marked dirty). */
    public ContaminationMap map() {
        return this.map;
    }

    /** Add contamination to a chunk, then prune to the cap. Returns the chunk's new level. */
    public int add(long chunk, int amount, int cap) {
        int now = this.map.add(chunk, amount);
        this.map.prune(cap);
        setDirty();
        return now;
    }

    /** One decay tick; marks dirty when anything changed. */
    public void decayTick(long gameTime, int permille, int budget) {
        if (this.map.decayTick(gameTime, permille, budget) > 0) {
            setDirty();
        }
    }

    /** Remove the listed chunks (gallery clear). */
    public int clear(Iterable<Long> chunks) {
        int removed = this.map.clear(chunks);
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    // --- persistence ------------------------------------------------------------------

    private static Codec<ContaminationState> codec() {
        return RecordCodecBuilder.create(inst -> inst.group(
                Entry.CODEC.listOf().optionalFieldOf("Chunks", List.of()).forGetter(ContaminationState::entries)
        ).apply(inst, ContaminationState::fromEntries));
    }

    private List<Entry> entries() {
        List<Entry> out = new ArrayList<>(this.map.size());
        this.map.snapshot().forEach((chunk, level) -> out.add(new Entry(chunk, level)));
        return out;
    }

    private static ContaminationState fromEntries(List<Entry> entries) {
        ContaminationState state = new ContaminationState();
        Map<Long, Short> loaded = new TreeMap<>();
        for (Entry e : entries) {
            loaded.put(e.chunk(), e.level());
        }
        state.map.load(loaded);
        return state;
    }
}

package za.co.neroland.neropower.radiation;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

//? if >=1.20.5 {
import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
//?}

/**
 * What the server tells one Geiger Counter: the nine chunk levels around its holder,
 * north-west to south-east in rows, the count of registered sources within 16 blocks (0..99) and
 * whether radiation runs on this server. It rides the held stack as the
 * {@code neropower:geiger_reading} component, so it reaches only the holder's client through vanilla
 * inventory sync and needs no packet. No position, owner or player: the holder's own chunk is the
 * centre cell, relative only. The server strips it from any counter not in a hand.
 */
public record GeigerReading(List<Integer> levels, int sources, boolean enabled) {

    /** The reading while radiation is off on the server. */
    public static final GeigerReading OFF = new GeigerReading(List.of(), 0, false);

    public static final Codec<GeigerReading> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.listOf().optionalFieldOf("levels", List.of()).forGetter(GeigerReading::levels),
            Codec.INT.optionalFieldOf("sources", 0).forGetter(GeigerReading::sources),
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(GeigerReading::enabled)
    ).apply(inst, GeigerReading::new));

    //? if >=1.20.5 {
    public static final StreamCodec<ByteBuf, GeigerReading> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(9)), GeigerReading::levels,
            ByteBufCodecs.VAR_INT, GeigerReading::sources,
            ByteBufCodecs.BOOL, GeigerReading::enabled,
            GeigerReading::new);
    //?}

    public GeigerReading {
        levels = List.copyOf(levels);
        sources = Math.max(0, Math.min(RadiationMath.GEIGER_SOURCE_CAP, sources));
    }

    /** The level of the holder's own chunk (the centre cell), 0 without data. */
    public int centre() {
        return this.levels.size() == 9 ? this.levels.get(4) : 0;
    }

    /** Cell {@code index} 0..8 (row order NW..SE), 0 without data. */
    public int cell(int index) {
        return index >= 0 && index < this.levels.size() ? this.levels.get(index) : 0;
    }

    /** Whether nine levels are present. */
    public boolean hasData() {
        return this.enabled && this.levels.size() == 9;
    }
}

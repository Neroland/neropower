package za.co.neroland.neropower.backend;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Seams the waste back end calls and other packages fill in. Every hook defaults to doing nothing, so
 * the back end works on its own and the radiation and progression code plug in without the back end
 * depending on them.
 *
 * <ul>
 *   <li>{@link #setBursts(BurstSink)} — the contamination code receives a Spent Fuel Rack FAILURE
 *       (decision: {@code radiationPoolFailureBurst} on the rack's own chunk).</li>
 *   <li>{@link #setEmitters(EmitterRegistry)} — the contamination code keeps its in-memory source set
 *       from these load / unload calls (casks, failed racks).</li>
 *   <li>{@link #setVitrified(VitrifiedListener)} — the progression code hears a finished
 *       vitrification (the {@code first_vitrified} record).</li>
 * </ul>
 *
 * Nothing passed through these hooks names a player; positions are world data.
 */
public final class BackEndHooks {

    /** Receives a one-off radiation burst from a back-end block. */
    @FunctionalInterface
    public interface BurstSink {
        /** A Spent Fuel Rack at {@code pos} entered FAILURE. */
        void poolFailure(ServerLevel level, BlockPos pos);
    }

    /** Tracks the loaded {@link RadiationEmitter}s. */
    public interface EmitterRegistry {
        /** {@code emitter} is loaded and ticking in {@code level}. */
        void add(Level level, RadiationEmitter emitter);

        /** {@code emitter} was removed or unloaded. */
        void remove(Level level, RadiationEmitter emitter);
    }

    /** Hears a finished vitrification. */
    @FunctionalInterface
    public interface VitrifiedListener {
        /**
         * One Vitrified Waste was made at {@code pos}.
         *
         * @param machine the Vitrifier, for operator credit through NeroTech's operator seam
         */
        void vitrified(ServerLevel level, BlockPos pos, VitrifierBlockEntity machine);
    }

    private static final BurstSink NO_BURSTS = (level, pos) -> {
    };

    private static final EmitterRegistry NO_EMITTERS = new EmitterRegistry() {
        @Override
        public void add(Level level, RadiationEmitter emitter) {
        }

        @Override
        public void remove(Level level, RadiationEmitter emitter) {
        }
    };

    private static final VitrifiedListener NO_LISTENER = (level, pos, machine) -> {
    };

    private static volatile BurstSink bursts = NO_BURSTS;
    private static volatile EmitterRegistry emitters = NO_EMITTERS;
    private static volatile VitrifiedListener vitrified = NO_LISTENER;

    private BackEndHooks() {
    }

    public static BurstSink bursts() {
        return bursts;
    }

    public static EmitterRegistry emitters() {
        return emitters;
    }

    public static VitrifiedListener vitrified() {
        return vitrified;
    }

    /** Install the burst sink; {@code null} restores the no-op. */
    public static void setBursts(@Nullable BurstSink sink) {
        bursts = sink == null ? NO_BURSTS : sink;
    }

    /** Install the emitter registry; {@code null} restores the no-op. */
    public static void setEmitters(@Nullable EmitterRegistry registry) {
        emitters = registry == null ? NO_EMITTERS : registry;
    }

    /** Install the vitrification listener; {@code null} restores the no-op. */
    public static void setVitrified(@Nullable VitrifiedListener listener) {
        vitrified = listener == null ? NO_LISTENER : listener;
    }

    /** Whether the installed hooks are the defaults (test helper). */
    static boolean defaults() {
        return Objects.equals(bursts, NO_BURSTS) && Objects.equals(emitters, NO_EMITTERS)
                && Objects.equals(vitrified, NO_LISTENER);
    }
}

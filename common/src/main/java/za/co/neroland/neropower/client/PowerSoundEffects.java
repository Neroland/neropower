package za.co.neroland.neropower.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.neropower.backend.DryCaskBlockEntity;
import za.co.neroland.neropower.backend.VitrifierBlockEntity;
import za.co.neroland.neropower.beam.BeamSplitterBlockEntity;
import za.co.neroland.neropower.beam.PhasedArrayEmitterBlockEntity;
import za.co.neroland.neropower.fission.ReactorControlComputerBlockEntity;
import za.co.neroland.neropower.generation.FuelCellBlockEntity;
import za.co.neroland.neropower.grid.CircuitBreakerBlockEntity;
import za.co.neroland.neropower.grid.LoadBankBlock;
import za.co.neroland.neropower.grid.LoadBankBlockEntity;
import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;
import za.co.neroland.neropower.machine.PowerClientHook;
import za.co.neroland.neropower.registry.ModSounds;
import za.co.neroland.neropower.storage.MomentumWheelBlockEntity;
import za.co.neroland.neropower.storage.SmesControllerBlockEntity;
import za.co.neroland.neropower.storage.SurgeBankBlock;
import za.co.neroland.neropower.storage.SurgeBankBlockEntity;
import za.co.neroland.neropower.turbine.CondenserBlockEntity;
import za.co.neroland.neropower.turbine.TurbineHelmBlockEntity;

/**
 * Client only. NeroPower's in-world machine sounds, driven entirely by what each block entity already
 * syncs (readout, buckets, block state), compared once per client tick: no packet exists for any of it and
 * the server plays none of these.
 *
 * <ul>
 * <li><b>Loops</b> are tickable sound instances bound to the block entity: they follow its state (pitch by
 * a bucket where the art asks for it) and stop themselves when the state ends, the block entity is removed
 * or unloaded, or the player walks off. At most {@value #VOICES_PER_CHUNK} loop voices per chunk.</li>
 * <li><b>One-shots</b> play once per synced change: turbine spool / coast / trip, the Vitrifier's finished
 * log, a Dry Cask sealing, the Surge Bank losing a can, the Phased Array retargeting. Nothing plays on
 * first sight (chunk load, rejoin) for a state already held.</li>
 * </ul>
 *
 * <p>Warnings come first in the list: the turbine's trip and overspeed, the Reactor Control Computer's alarm
 * (while its {@code alarm} block state is lit) and the Storage Ring's warming loop. Machine-scoped state
 * only; no player data.</p>
 */
public final class PowerSoundEffects implements PowerClientHook.Handler {

    /** Most looping voices one chunk may hold at once. */
    static final int VOICES_PER_CHUNK = 3;
    /** Sounds start within 16 blocks and stop beyond 20. */
    private static final double HEAR_SQR = 16.0D * 16.0D;
    private static final double STOP_SQR = 20.0D * 20.0D;
    private static final float LOOP_VOLUME = 0.6F;
    private static final float ONE_SHOT_VOLUME = 0.8F;

    /** One loop a machine can run: its sound, while it runs, and its pitch and volume now. */
    private record LoopSpec(String id, SoundEvent sound, Predicate<NeroPowerMachineBlockEntity> whileOn,
            ToDoubleFunction<NeroPowerMachineBlockEntity> pitch, ToDoubleFunction<NeroPowerMachineBlockEntity> volume) {
    }

    /** Client-side memory per machine (entries go with the block entity). */
    private static final class Track {
        boolean seen;
        int a;
        int b;
        int c;
        final Map<String, Loop> loops = new HashMap<>(2);
    }

    private final Map<NeroPowerMachineBlockEntity, Track> tracks = new WeakHashMap<>();
    private final Map<Long, List<Loop>> voices = new HashMap<>();

    /** Client setup: install the handler. */
    public static void install() {
        PowerClientHook.install(new PowerSoundEffects());
    }

    @Override
    public void tick(NeroPowerMachineBlockEntity machine) {
        Level level = machine.getLevel();
        if (level == null || !level.isClientSide() || machine.isRemoved()) {
            return;
        }
        List<LoopSpec> loops = loopsFor(machine);
        boolean oneShots = hasOneShots(machine);
        if (loops.isEmpty() && !oneShots) {
            return;
        }
        BlockPos pos = machine.getBlockPos();
        Player player = Minecraft.getInstance().player;
        double distance = player == null ? Double.MAX_VALUE
                : player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        boolean hear = distance <= HEAR_SQR;
        Track track = this.tracks.computeIfAbsent(machine, key -> new Track());
        if (oneShots) {
            oneShots(machine, track, level, pos, hear);
        }
        if (!hear) {
            return;
        }
        for (LoopSpec spec : loops) {
            if (!spec.whileOn().test(machine)) {
                continue;
            }
            Loop playing = track.loops.get(spec.id());
            if (playing == null || playing.isStopped()
                    || !Minecraft.getInstance().getSoundManager().isActive(playing)) {
                startLoop(machine, spec, track, pos);
            }
        }
    }

    // --- one-shots --------------------------------------------------------------------------------

    private static boolean hasOneShots(NeroPowerMachineBlockEntity m) {
        return m instanceof TurbineHelmBlockEntity || m instanceof VitrifierBlockEntity
                || m instanceof DryCaskBlockEntity || m instanceof SurgeBankBlockEntity
                || m instanceof PhasedArrayEmitterBlockEntity;
    }

    /** Compare this tick's synced values with the last ones and play what changed (once each). */
    private static void oneShots(NeroPowerMachineBlockEntity m, Track t, Level level, BlockPos pos, boolean hear) {
        int a;
        int b = 0;
        int c = 0;
        BlockState state = m.getBlockState();
        if (m instanceof TurbineHelmBlockEntity turbine) {
            a = turbine.renderReadout();
            b = turbine.tripped() ? 1 : 0;
            c = turbine.rpmBucket();
        } else if (m instanceof VitrifierBlockEntity) {
            a = m.renderPulse();
        } else if (m instanceof DryCaskBlockEntity) {
            a = state.hasProperty(NeroPowerMachineBlock.STATUS)
                    && state.getValue(NeroPowerMachineBlock.STATUS) == NeroPowerMachineBlock.VisualStatus.TRIPPED ? 1 : 0;
        } else if (m instanceof SurgeBankBlockEntity) {
            a = state.hasProperty(SurgeBankBlock.CANS) ? state.getValue(SurgeBankBlock.CANS) : 0;
        } else {
            a = ((PhasedArrayEmitterBlockEntity) m).targetIndex();
        }
        if (!t.seen) {
            t.seen = true; // first sight records only
        } else if (hear) {
            if (m instanceof TurbineHelmBlockEntity) {
                if (b == 1 && t.b == 0) {
                    play(level, pos, ModSounds.TURBINE_TRIP.get(), 1.0F);
                } else if (a == TurbineReadoutCodes.WORK && t.a != TurbineReadoutCodes.WORK
                        && t.c < TurbineReadoutCodes.SPOOL_BELOW_BUCKET) {
                    play(level, pos, ModSounds.TURBINE_SPOOL.get(), 1.0F);
                } else if (t.a == TurbineReadoutCodes.WORK && a != TurbineReadoutCodes.WORK
                        && a != TurbineReadoutCodes.HOT && b == 0 && c > 0) {
                    play(level, pos, ModSounds.TURBINE_COAST.get(), 1.0F);
                }
            } else if (m instanceof VitrifierBlockEntity) {
                if (a != t.a) {
                    play(level, pos, ModSounds.VITRIFIER_POUR.get(), 1.0F);
                }
            } else if (m instanceof DryCaskBlockEntity) {
                if (a == 0 && t.a == 1) {
                    play(level, pos, ModSounds.CASK_SEAL.get(), 1.0F);
                }
            } else if (m instanceof SurgeBankBlockEntity) {
                if (a < t.a) {
                    play(level, pos, ModSounds.SURGE_BANK_SNAP.get(), 0.9F + 0.05F * a);
                }
            } else if (a != t.a) {
                play(level, pos, ModSounds.PHASED_ARRAY_STEP.get(), 1.0F);
            }
        }
        t.a = a;
        t.b = b;
        t.c = c;
    }

    /** Readout codes the turbine shares with the screens and renderers (0 rest, 1 work, 3 hot). */
    private static final class TurbineReadoutCodes {
        static final int WORK = 1;
        static final int HOT = 3;
        /** A start from below 1 800 rpm (bucket 18) spools up; a brief stall at speed does not. */
        static final int SPOOL_BELOW_BUCKET = 18;
    }

    private static void play(Level level, BlockPos pos, SoundEvent sound, float pitch) {
        level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, sound, SoundSource.BLOCKS,
                ONE_SHOT_VOLUME, pitch, false);
    }

    // --- loops ------------------------------------------------------------------------------------

    private static final List<LoopSpec> NONE = List.of();

    private static List<LoopSpec> loopsFor(NeroPowerMachineBlockEntity m) {
        if (m instanceof TurbineHelmBlockEntity) {
            return List.of(
                    new LoopSpec("overspeed", ModSounds.TURBINE_OVERSPEED.get(),
                            x -> ((TurbineHelmBlockEntity) x).renderReadout() == TurbineReadoutCodes.HOT,
                            x -> 1.0D, x -> 1.0D),
                    new LoopSpec("run", ModSounds.TURBINE_RUN.get(),
                            x -> ((TurbineHelmBlockEntity) x).rpmBucket() > 0 && !((TurbineHelmBlockEntity) x).tripped(),
                            x -> 0.6D + 0.65D * Math.min(24, ((TurbineHelmBlockEntity) x).rpmBucket()) / 24.0D,
                            x -> LOOP_VOLUME));
        }
        if (m instanceof ReactorControlComputerBlockEntity) {
            return List.of(new LoopSpec("alarm", ModSounds.CONTROL_COMPUTER_ALARM.get(),
                    PowerSoundEffects::alarmLit, x -> 1.0D, x -> 1.0D));
        }
        if (m instanceof SmesControllerBlockEntity) {
            return List.of(
                    new LoopSpec("warn", ModSounds.SMES_WARN.get(),
                            x -> ((SmesControllerBlockEntity) x).readoutCode() == 3, x -> 1.0D, x -> 0.9D),
                    new LoopSpec("hum", ModSounds.SMES_HUM.get(),
                            x -> {
                                int r = ((SmesControllerBlockEntity) x).readoutCode();
                                return (r == 0 || r == 1) && ((SmesControllerBlockEntity) x).chargeBucket() > 0;
                            },
                            x -> 0.8D + 0.4D * ((SmesControllerBlockEntity) x).chargeBucket() / 16.0D,
                            x -> LOOP_VOLUME));
        }
        if (m instanceof MomentumWheelBlockEntity) {
            return List.of(new LoopSpec("spin", ModSounds.MOMENTUM_WHEEL_SPIN.get(),
                    x -> ((MomentumWheelBlockEntity) x).chargeBucket() > 0,
                    x -> 0.5D + ((MomentumWheelBlockEntity) x).chargeBucket() / 20.0D, x -> LOOP_VOLUME));
        }
        if (m instanceof CircuitBreakerBlockEntity) {
            return List.of(new LoopSpec("strain", ModSounds.BREAKER_STRAIN.get(),
                    x -> ((CircuitBreakerBlockEntity) x).readoutCode() == 3, x -> 1.0D,
                    x -> 0.3D + 0.15D * Math.max(2, Math.min(4, ((CircuitBreakerBlockEntity) x).loadStep()))));
        }
        if (m instanceof LoadBankBlockEntity) {
            return List.of(new LoopSpec("sizzle", ModSounds.LOAD_BANK_SIZZLE.get(),
                    x -> blockInt(x, LoadBankBlock.HEAT) >= 2, x -> 1.0D,
                    x -> 0.3D + 0.15D * blockInt(x, LoadBankBlock.HEAT)));
        }
        if (m instanceof BeamSplitterBlockEntity) {
            return List.of(new LoopSpec("split", ModSounds.BEAM_SPLIT.get(),
                    x -> ((BeamSplitterBlockEntity) x).readoutCode() == 1, x -> 1.0D, x -> 0.5D));
        }
        if (m instanceof FuelCellBlockEntity) {
            return List.of(new LoopSpec("hiss", ModSounds.FUEL_CELL_HISS.get(),
                    NeroPowerMachineBlockEntity::renderActive, x -> 1.0D, x -> 0.5D));
        }
        if (m instanceof CondenserBlockEntity) {
            return List.of(new LoopSpec("drip", ModSounds.CONDENSER_DRIP.get(),
                    x -> ((CondenserBlockEntity) x).renderReadout() == 1, x -> 1.0D, x -> 0.5D));
        }
        return NONE;
    }

    private static boolean alarmLit(NeroPowerMachineBlockEntity m) {
        BlockState state = m.getBlockState();
        return state.hasProperty(NeroPowerMachineBlock.ALARM) && state.getValue(NeroPowerMachineBlock.ALARM);
    }

    private static int blockInt(NeroPowerMachineBlockEntity m,
            net.minecraft.world.level.block.state.properties.IntegerProperty property) {
        BlockState state = m.getBlockState();
        return state.hasProperty(property) ? state.getValue(property) : 0;
    }

    private void startLoop(NeroPowerMachineBlockEntity machine, LoopSpec spec, Track track, BlockPos pos) {
        long chunk = ((long) (pos.getX() >> 4) << 32) | ((pos.getZ() >> 4) & 0xFFFFFFFFL);
        List<Loop> inChunk = this.voices.computeIfAbsent(chunk, key -> new ArrayList<>(VOICES_PER_CHUNK));
        inChunk.removeIf(sound -> sound.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(sound));
        if (inChunk.size() >= VOICES_PER_CHUNK) {
            return; // the chunk is full: this machine stays quiet until a voice frees up
        }
        Loop sound = new Loop(spec, machine);
        track.loops.put(spec.id(), sound);
        inChunk.add(sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** A machine loop bound to its block entity: follows its state and stops with it. */
    private static final class Loop extends AbstractTickableSoundInstance {

        private final LoopSpec spec;
        @Nullable
        private NeroPowerMachineBlockEntity machine;

        Loop(LoopSpec spec, NeroPowerMachineBlockEntity machine) {
            super(spec.sound(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.spec = spec;
            this.machine = machine;
            BlockPos pos = machine.getBlockPos();
            this.x = pos.getX() + 0.5D;
            this.y = pos.getY() + 0.5D;
            this.z = pos.getZ() + 0.5D;
            this.looping = true;
            this.delay = 0;
            this.volume = (float) spec.volume().applyAsDouble(machine);
            this.pitch = (float) spec.pitch().applyAsDouble(machine);
        }

        @Override
        public void tick() {
            NeroPowerMachineBlockEntity m = this.machine;
            Player player = Minecraft.getInstance().player;
            if (m == null || m.isRemoved() || m.getLevel() == null
                    || m.getLevel().getBlockEntity(m.getBlockPos()) != m || !this.spec.whileOn().test(m)
                    || player == null || player.distanceToSqr(this.x, this.y, this.z) > STOP_SQR) {
                this.machine = null; // let the block entity go
                stop();
                return;
            }
            this.volume = (float) this.spec.volume().applyAsDouble(m);
            this.pitch = (float) this.spec.pitch().applyAsDouble(m);
        }
    }
}

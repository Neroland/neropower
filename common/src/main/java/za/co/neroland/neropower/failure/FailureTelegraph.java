package za.co.neroland.neropower.failure;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.link.LinkAlert;
import za.co.neroland.nerolandcore.link.LinkAlerts;

import za.co.neroland.nerotech.api.MachineFailureEvents;

import za.co.neroland.neropower.NeroPowerCommon;
import za.co.neroland.neropower.machine.NeroPowerMachineBlock;
import za.co.neroland.neropower.machine.NeroPowerMachineBlockEntity;

/**
 * Makes a failure-stage transition visible, server side, in every channel NeroPower has:
 * <ol>
 *   <li>NeroTech's ecosystem bus — {@link MachineFailureEvents#fire} with the stage entered (rising)
 *       or left (falling), so NeroEvents / NeroLink / another mod's interlock hears it;</li>
 *   <li>the block's {@link NeroPowerMachineBlock#ALARM} state, when the block declares it, for the
 *       model / renderer;</li>
 *   <li>a sound and a particle burst at the machine — escalating with the stage, one soft note on
 *       recovery;</li>
 *   <li>a Core link alert to the recorded owner: WARN on entering UNSTABLE, CRITICAL on FAILURE.</li>
 * </ol>
 *
 * <p>Privacy (POPIA/GDPR): the owner UUID is used only to address the alert — it never enters the
 * event scope, a log line or a packet.
 */
public final class FailureTelegraph {

    /** The link-alert module id NeroPower's alerts carry (owned by the Stage 8 link module later). */
    public static final String MODULE_ID = NeroPowerCommon.MOD_ID;

    private FailureTelegraph() {
    }

    /** Announce {@code from → to} for {@code be}. A no-op on the client or before the machine is in a level. */
    public static void announce(NeroPowerMachineBlockEntity be, FailureStage from, FailureStage to) {
        Level level = be.getLevel();
        if (from == to || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        boolean rising = to.code() > from.code();

        // (a) The ecosystem bus: NeroTech's convention is "stage entered, rising=true" on the way up
        // and "stage left, rising=false" on the way down; STABLE (code 0) is never published.
        FailureStage published = rising ? to : from;
        MachineFailureEvents.fire(be.machineId(), serverLevel.dimension().identifier().toString(), pos,
                published.code(), rising);

        // (b) The alarm block state, for blocks that declare it.
        BlockState state = be.getBlockState();
        if (state.hasProperty(NeroPowerMachineBlock.ALARM)) {
            boolean alarm = to != FailureStage.STABLE;
            if (state.getValue(NeroPowerMachineBlock.ALARM) != alarm) {
                serverLevel.setBlock(pos, state.setValue(NeroPowerMachineBlock.ALARM, alarm), Block.UPDATE_ALL);
            }
        }

        // (c) Sound + particles at the machine.
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.1D;
        double z = pos.getZ() + 0.5D;
        if (rising) {
            switch (to) {
                case WARNING -> {
                    serverLevel.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 0.8F);
                    serverLevel.sendParticles(ParticleTypes.SMOKE, x, y, z, 12, 0.3D, 0.2D, 0.3D, 0.02D);
                }
                case UNSTABLE -> {
                    serverLevel.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
                    serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 24, 0.4D, 0.3D, 0.4D, 0.05D);
                }
                case FAILURE -> {
                    serverLevel.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.5F, 0.5F);
                    serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                default -> {
                    // STABLE is never entered by rising.
                }
            }
        } else if (to == FailureStage.STABLE) {
            serverLevel.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }

        // (d) The owner's NeroLink alert on the two stages an operator must act on.
        if (rising && (to == FailureStage.UNSTABLE || to == FailureStage.FAILURE)) {
            be.owner().ifPresent(owner -> alertOwner(serverLevel.getServer(), owner, be.machineId(), pos, to));
        }
    }

    private static void alertOwner(MinecraftServer server, UUID owner, String machineId, BlockPos pos,
            FailureStage stage) {
        boolean failed = stage == FailureStage.FAILURE;
        LinkAlert.Severity severity = failed ? LinkAlert.Severity.CRITICAL : LinkAlert.Severity.WARN;
        String text = (failed
                ? "Your NeroPower machine has failed at "
                : "Your NeroPower machine is unstable at ")
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + machineId + ").";
        LinkAlerts.get(server).raise(server, owner, LinkAlert.raise(
                "failure_" + stage.name().toLowerCase(java.util.Locale.ROOT) + "_" + pos.asLong(),
                MODULE_ID, severity, text));
    }
}

package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.beam.PhasedArrayEmitterBlockEntity;
import za.co.neroland.neropower.grid.GridReadout;

/**
 * Phased Array Emitter BER: the phase wave over the sixteen panel tiles, and the stepped beam.
 *
 * <p><b>Wave</b> (arc violet, the field colour of the superconducting tier): while working, a diagonal
 * runs across the tiles toward the served receiver: toward the side it lies on, and upward if it is
 * higher. Starved of power, one corner tile ticks. When the aimed target's line of sight is blocked,
 * the wave runs backwards once and stops. It is one quad showing a frame of the eight-frame strip.
 *
 * <p><b>Beam</b>: an amber core in an arc-violet sheath, one beam only, toward the receiver being
 * served. When the emitter steps to its next target the new beam fades in over three ticks. The client
 * is told a direction in sixteenths, never a position, so the beam is drawn along that direction until
 * the first block in the way (found on the client, looked up again once a second) or 64 blocks. A
 * blocked target shows a dim beam ending in a flare at the block in the way.
 *
 * <p>Reads only {@code targetIndex()}, {@code directionX/Y/Z()}, {@code blocked()}, {@code readoutCode()}
 * and {@code stallCause()}. Beam cuts are the shared ones ({@link BeamEndpointRenderer#rayExtract}).
 * The wave and lamp are drawn to 48 blocks. With animations off the wave is one still frame and the
 * beam does not travel.
 */
public class PhasedArrayRenderer
        implements BlockEntityRenderer<PhasedArrayEmitterBlockEntity, PhasedArrayRenderer.State> {

    private static final Identifier WAVE_TEX = PowerRenderHelper.texture("phased_array_emitter_face");

    /** The lamp socket on the foot front (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(10.5F, 0.0F, 1.5F, 12.5F, 2.0F, 2.0F);

    /** The panel (model x 2..14, y 4..16), just proud of the tiles at z 2; sheet texels 4..28 by 0..24. */
    private static final float PANEL_X0 = 2.0F / 16.0F;
    private static final float PANEL_X1 = 14.0F / 16.0F;
    private static final float PANEL_Y0 = 4.0F / 16.0F;
    private static final float PANEL_Y1 = 16.0F / 16.0F;
    private static final float PANEL_Z = 2.0F / 16.0F - 0.004F;
    private static final float U_LEFT = 4.0F / 32.0F;
    private static final float U_RIGHT = 28.0F / 32.0F;
    private static final float V_SPAN = 24.0F / 32.0F;
    private static final int FRAMES = 8;
    private static final int FRAME_TICKS = 2;
    private static final int STILL_FRAME = 3;
    /** Starved: the corner tile shows for 4 ticks in every 20. */
    private static final int TICK_CYCLE = 20;
    private static final int TICK_ON = 4;

    /** Beam origin: the panel centre, 3/8 of a block in front of the block centre. */
    private static final float ORIGIN_Y = 10.0F / 16.0F;
    private static final float ORIGIN_OUT = 6.0F / 16.0F;
    private static final float FADE_TICKS = 3.0F;
    private static final long SIGHT_TICKS = 20L;
    private static final float BLOCKED_GAIN = 0.5F;
    private static final int FAR = 64;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: wave frame and mirroring, the beam, the scope line and the lamp. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        int frame = -1;
        boolean flipU;
        boolean flipV;
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        final BeamEndpointRenderer.Ray ray = new BeamEndpointRenderer.Ray();
        final BeamScopeOverlay.Scope scope = new BeamScopeOverlay.Scope();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The beam's segment along the synced direction, cut to 64 blocks. NeoForge-only frustum hook. */
    public AABB getRenderBoundingBox(PhasedArrayEmitterBlockEntity emitter) {
        return BeamEndpointRenderer.segmentBox(emitter.getBlockPos(),
                emitter.directionX() * 4.0, emitter.directionY() * 4.0, emitter.directionZ() * 4.0,
                BeamEndpointRenderer.MAX_DRAWN);
    }

    @Override
    public void extractRenderState(PhasedArrayEmitterBlockEntity emitter, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(emitter, state, partialTick, cameraPos, breakProgress);
        Level level = emitter.getLevel();
        Direction facing = emitter.getBlockState().getValue(NeroTechMachineBlock.FACING);
        state.facing = facing;
        BlockPos pos = emitter.getBlockPos();
        double distance = Math.sqrt(pos.distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.visible = distance <= REDUCED_DETAIL;
        int readout = emitter.readoutCode();
        PowerRenderHelper.lampExtract(state.lamp, readout, false, level, partialTick);
        state.frame = -1;
        state.ray.off();
        state.scope.off();
        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null) {
            return;
        }
        boolean animate = state.lamp.animate;
        long tick = level.getGameTime();
        int dirX = emitter.directionX();
        int dirY = emitter.directionY();
        int dirZ = emitter.directionZ();
        boolean aimedAt = dirX != 0 || dirY != 0 || dirZ != 0;
        boolean blocked = emitter.blocked() && aimedAt;
        boolean working = readout == PowerRenderHelper.READOUT_WORK;

        // displayPos holds the last aim seen, displayLastTick when it changed, prevDisplayPos the beam length.
        int key = (emitter.targetIndex() & 7) | (blocked ? 8 : 0) | ((dirX + 16) << 4) | ((dirY + 16) << 10)
                | ((dirZ + 16) << 16);
        boolean changed = false;
        if (!emitter.displayInit) {
            emitter.displayInit = true;
            emitter.displayPos = key;
            emitter.displayLastTick = tick - 1000L;
            changed = true;
        } else if ((int) emitter.displayPos != key) {
            emitter.displayPos = key;
            emitter.displayLastTick = tick;
            changed = true;
        }
        float age = Math.max(0.0F, (tick - emitter.displayLastTick) + partialTick);

        // The wave. Viewer's left on the panel is (-nz, 0, nx) for an outward normal (nx, 0, nz).
        float lateral = dirX * -facing.getStepZ() + dirZ * facing.getStepX();
        state.flipU = lateral > 0.0F;
        state.flipV = dirY > 0;
        if (!animate) {
            state.frame = working ? STILL_FRAME : -1;
        } else if (blocked) {
            int step = (int) (age / FRAME_TICKS);
            state.frame = step < FRAMES ? FRAMES - 1 - step : -1;
        } else if (working) {
            state.frame = (int) ((tick / FRAME_TICKS) % FRAMES);
        } else if (readout == PowerRenderHelper.READOUT_STALL
                && emitter.stallCause() == GridReadout.CAUSE_NO_POWER) {
            state.frame = tick % TICK_CYCLE < TICK_ON ? 0 : -1;
            state.flipU = false;
            state.flipV = false;
        }

        if (!aimedAt) {
            return;
        }
        boolean aimed = BeamScopeOverlay.aimed(pos);
        if (!working && !blocked && !aimed) {
            return;
        }
        // Where the beam ends: the first block along the aim, looked up when the aim changes and once a second.
        int farX = pos.getX() + Math.round(dirX * FAR / 16.0F);
        int farY = pos.getY() + Math.round(dirY * FAR / 16.0F);
        int farZ = pos.getZ() + Math.round(dirZ * FAR / 16.0F);
        BlockPos.MutableBlockPos hit = null;
        if (changed || aimed || tick % SIGHT_TICKS == 0L) {
            hit = new BlockPos.MutableBlockPos();
            emitter.prevDisplayPos = BeamEndpointRenderer.sight(level, pos, farX, farY, farZ, hit);
        }
        float reach = (float) emitter.prevDisplayPos;
        float length = reach >= 0.0F ? reach : FAR;
        float ox = 0.5F + facing.getStepX() * ORIGIN_OUT;
        float oz = 0.5F + facing.getStepZ() * ORIGIN_OUT;
        if (working || blocked) {
            float fade = animate ? Mth.clamp(age / FADE_TICKS, 0.0F, 1.0F) : 1.0F;
            state.ray.gain = (blocked ? BLOCKED_GAIN : 1.0F) * fade;
            state.ray.coreRgb = PowerRenderHelper.powerColor(0.7F);
            state.ray.sheathRgb = PowerRenderHelper.ARC_LIGHT_RGB;
            BeamEndpointRenderer.rayExtract(state.ray, pos, ox, ORIGIN_Y, oz, dirX, dirY, dirZ, length,
                    blocked && reach >= 0.0F, cameraPos, level, partialTick, animate);
        }
        if (aimed) {
            // Clear: white to the first block (the receiver). Blocked: red from the block in the way onward.
            float total = blocked && reach >= 0.0F ? Math.min(FAR, reach + 4.0F) : length;
            BeamScopeOverlay.extract(state.scope, pos, ox, ORIGIN_Y, oz, dirX, dirY, dirZ, total,
                    blocked ? reach : -1.0F, blocked && reach >= 0.0F ? hit : null);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        // The beam and scope line are in block space, not turned with the facing.
        BeamEndpointRenderer.raySubmit(state.ray, poseStack, collector);
        BeamScopeOverlay.submit(state.scope, poseStack, collector);
        if (!state.visible) {
            return;
        }
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);
        if (state.frame >= 0) {
            // x0 is the viewer's right on a north-facing panel; mirror by swapping the UV ends.
            float uRight = state.flipU ? U_LEFT : U_RIGHT;
            float uLeft = state.flipU ? U_RIGHT : U_LEFT;
            float top = state.frame / (float) FRAMES;
            float bottom = (state.frame + V_SPAN) / FRAMES;
            float vTop = state.flipV ? bottom : top;
            float vBottom = state.flipV ? top : bottom;
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(WAVE_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT,
                            PowerRenderHelper.ARC_LIGHT_RGB, 0, 0, -1,
                            PANEL_X0, PANEL_Y0, PANEL_Z, uRight, vBottom,
                            PANEL_X0, PANEL_Y1, PANEL_Z, uRight, vTop,
                            PANEL_X1, PANEL_Y1, PANEL_Z, uLeft, vTop,
                            PANEL_X1, PANEL_Y0, PANEL_Z, uLeft, vBottom));
        }
        poseStack.popPose();
    }
}

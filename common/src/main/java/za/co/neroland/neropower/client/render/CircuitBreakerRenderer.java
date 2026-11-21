package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerotech.machine.NeroTechMachineBlock;

import za.co.neroland.neropower.grid.CircuitBreakerBlock;
import za.co.neroland.neropower.grid.CircuitBreakerBlockEntity;

/**
 * Circuit Breaker BER: the status lamp, and the warning beat of the flag window. The handle and the
 * flag are the block model ({@code tripped}); this renderer only darkens the amber window on the off
 * beat as the load climbs: load step 2 beats once in two seconds, step 3 once a second, step 4 twice a
 * second. A tripped breaker shows its flag and the lamp's own trip rhythm, nothing more.
 *
 * <p>Reads only {@code loadStep()}, {@code readoutCode()} and the block state. Within 24 blocks the
 * window beats; to 48 only the lamp shows; beyond that nothing. With animations off the window stays lit.
 */
public class CircuitBreakerRenderer
        implements BlockEntityRenderer<CircuitBreakerBlockEntity, CircuitBreakerRenderer.State> {

    private static final Identifier WINDOW_TEX = PowerRenderHelper.texture("circuit_breaker_window");

    /** The lamp socket on the body front (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(11.5F, 8.0F, 0.5F, 13.5F, 10.0F, 1.0F);

    /** The flag window on the body front: front-sheet texels 11..21 by 13..21, on the face at z 1. */
    private static final float WIN_X0 = 5.0F / 16.0F;
    private static final float WIN_X1 = 10.5F / 16.0F;
    private static final float WIN_Y0 = 5.0F / 16.0F;
    private static final float WIN_Y1 = 9.5F / 16.0F;
    private static final float WIN_Z = 1.0F / 16.0F - 0.004F;
    /** The unlit window is the top-left 11 by 9 texels of its sprite. */
    private static final float WIN_U = 11.0F / 32.0F;
    private static final float WIN_V = 9.0F / 32.0F;
    private static final double FULL_DETAIL = 24.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: lamp, and whether the window is on its off beat this frame. */
    public static class State extends BlockEntityRenderState {
        boolean visible;
        boolean windowOff;
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The block itself. NeoForge-only frustum hook, inert non-override on Fabric/Forge. */
    public AABB getRenderBoundingBox(CircuitBreakerBlockEntity breaker) {
        return new AABB(breaker.getBlockPos());
    }

    @Override
    public void extractRenderState(CircuitBreakerBlockEntity breaker, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(breaker, state, partialTick, cameraPos, breakProgress);
        Level level = breaker.getLevel();
        BlockState blockState = breaker.getBlockState();
        state.facing = blockState.getValue(NeroTechMachineBlock.FACING);
        double distance = Math.sqrt(breaker.getBlockPos().distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        state.visible = distance <= REDUCED_DETAIL;
        PowerRenderHelper.lampExtract(state.lamp, breaker.readoutCode(), false, level, partialTick);
        state.windowOff = false;
        if (level == null || !state.lamp.animate || distance > FULL_DETAIL) {
            return;
        }
        boolean tripped = blockState.hasProperty(CircuitBreakerBlock.TRIPPED)
                && blockState.getValue(CircuitBreakerBlock.TRIPPED);
        int step = Mth.clamp(breaker.loadStep(), 0, 4);
        if (tripped || step < 2) {
            return;
        }
        int cycle = step == 2 ? 40 : step == 3 ? 20 : 10;
        state.windowOff = level.getGameTime() % cycle >= cycle / 2;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (!state.visible) {
            return;
        }
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);
        if (state.windowOff) {
            collector.order(1).submitCustomGeometry(poseStack, RenderTypes.entityCutout(WINDOW_TEX),
                    (pose, c) -> PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT, 0xFFFFFF, 0, 0, -1,
                            WIN_X0, WIN_Y0, WIN_Z, WIN_U, WIN_V,
                            WIN_X0, WIN_Y1, WIN_Z, WIN_U, 0.0F,
                            WIN_X1, WIN_Y1, WIN_Z, 0.0F, 0.0F,
                            WIN_X1, WIN_Y0, WIN_Z, 0.0F, WIN_V));
        }
        poseStack.popPose();
    }
}

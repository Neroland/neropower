package za.co.neroland.neropower.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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

import za.co.neroland.neropower.storage.SmesControllerBlockEntity;

/**
 * Storage Ring Controller BER. On the controller: the status lamp, the square gauge (sixteen sockets
 * filling clockwise with the charge bucket, arc violet) and the lockout flag over the gauge window. On
 * the formed ring, from the synced ring offsets: a violet field ribbon along the coils' inner edge whose
 * brightness is the charge and whose travel is the I/O bucket (it stands still when nothing flows),
 * rime on the coils' backs that recedes clockwise from the controller as the ring warms, and a thin
 * seam along the ring's outer foot that says "formed" from outside (ember idle, amber working, the
 * warning colours when hot or quenching, nothing when unformed).
 *
 * <p>Arc violet appears only on the field: the ribbon is a 4-pixel band and the gauge lights half of
 * each 2x2 socket, both well under 8 % of a face. Quenching: the ribbon turns white and beats with the
 * lamp; locked out: no ribbon, the flag shows.
 *
 * <p>Sync discipline: extraction reads only the bucketed fields ({@code chargeBucket}, {@code ioBucket},
 * {@code warmthBucket}, {@code lockout}, {@code ringOffsets}, {@code readoutCode}). Ribbon travel is
 * integrated once per game tick on the client. Detail tiers by camera distance to the ring's centre:
 * to 24 blocks ribbon and rime, to 48 the ribbon at half segments, beyond that nothing; the seam stops
 * at 32. Budget at the largest ring (56 coils): 56 ribbon quads, 28 rime quads, 4 seam quads.
 */
public class SmesControllerRenderer
        implements BlockEntityRenderer<SmesControllerBlockEntity, SmesControllerRenderer.State> {

    private static final Identifier FIELD_TEX = PowerRenderHelper.texture("smes_controller_field");
    private static final Identifier FROST_TEX = PowerRenderHelper.texture("smes_controller_frost");

    /** The lamp socket on the bezel (the model's {@code status_socket} element). */
    private static final PowerRenderHelper.LampSocket SOCKET =
            PowerRenderHelper.LampSocket.model(13.0F, 8.0F, -0.5F, 15.0F, 10.0F, 0.0F);

    // Gauge layout on the 32-texel front sheet (tools/painters/storage.py: gauge_cells()).
    private static final int GAUGE_X = 9;
    private static final int GAUGE_Y = 11;
    private static final int GAUGE_PITCH = 3;
    private static final int GAUGE_CELLS = 16;
    private static final float GAUGE_Z = -0.004F;
    private static final float FLAG_Z = -0.006F;
    private static final float FLAG_INNER_Z = -0.008F;

    /** The coil tube: centred on the block, 8 pixels across, model y 5..13. */
    private static final float TUBE_HALF = 4.0F / 16.0F;
    private static final float RIBBON_OFFSET = TUBE_HALF + 0.3F / 16.0F;
    private static final float RIBBON_Y = 9.0F / 16.0F;
    private static final float RIBBON_HALF_HEIGHT = 2.0F / 16.0F;
    /** The band of the field sheet that holds the ribbon (rows 9..23 of 32). */
    private static final float RIBBON_V0 = 9.0F / 32.0F;
    private static final float RIBBON_V1 = 23.0F / 32.0F;
    private static final float FROST_Y = 13.05F / 16.0F;
    /** The cradle's outer face sits 2 pixels inside the block edge; the seam is its lowest strip. */
    private static final float SEAM_INSET = 2.0F / 16.0F - 0.005F;
    private static final float SEAM_Y = 1.5F / 16.0F;
    private static final float SEAM_HALF_HEIGHT = 0.5F / 16.0F;
    /** Ribbon pulse train: one crest every eight coils; coils per tick for each I/O bucket. */
    private static final float WAVELENGTH = 8.0F;
    private static final float TRAVEL_PER_BUCKET = 0.12F;

    private static final double FULL_DETAIL = 24.0D;
    private static final double SEAM_DETAIL = 32.0D;
    private static final double REDUCED_DETAIL = 48.0D;

    /** Render state: every value resolved at extraction. */
    public static class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        final PowerRenderHelper.Lamp lamp = new PowerRenderHelper.Lamp();
        boolean near;
        boolean formed;
        boolean ringFull;
        boolean ringReduced;
        boolean seam;
        int minDx;
        int minDz;
        int maxDx;
        int maxDz;
        int charge;
        int warmth;
        boolean lockout;
        boolean flicker;
        float phase;
        long tick;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** The formed ring's bounds (the block alone when unformed), padded by one. NeoForge-only frustum hook. */
    public AABB getRenderBoundingBox(SmesControllerBlockEntity controller) {
        BlockPos pos = controller.getBlockPos();
        int[] ring = controller.ringOffsets();
        return new AABB(pos.getX() + ring[0] - 1.0, pos.getY() - 1.0, pos.getZ() + ring[1] - 1.0,
                pos.getX() + ring[2] + 2.0, pos.getY() + 2.0, pos.getZ() + ring[3] + 2.0);
    }

    @Override
    public void extractRenderState(SmesControllerBlockEntity controller, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(controller, state, partialTick, cameraPos, breakProgress);
        Level level = controller.getLevel();
        BlockPos pos = controller.getBlockPos();
        state.facing = controller.getBlockState().getValue(NeroTechMachineBlock.FACING);
        int[] ring = controller.ringOffsets();
        state.minDx = ring[0];
        state.minDz = ring[1];
        state.maxDx = ring[2];
        state.maxDz = ring[3];
        // A formed ring is at least 5 on a side and has the controller on its rim.
        state.formed = ring[2] - ring[0] >= 4 && ring[3] - ring[1] >= 4
                && (ring[0] == 0 || ring[2] == 0 || ring[1] == 0 || ring[3] == 0);
        state.charge = Mth.clamp(controller.chargeBucket(), 0, GAUGE_CELLS);
        state.warmth = Mth.clamp(controller.warmthBucket(), 0, 10);
        state.lockout = controller.lockout();
        int readout = controller.readoutCode();
        PowerRenderHelper.lampExtract(state.lamp, readout, state.warmth >= 10, level, partialTick);

        double own = Math.sqrt(pos.distToCenterSqr(cameraPos.x, cameraPos.y, cameraPos.z));
        double cx = pos.getX() + (ring[0] + ring[2] + 1) / 2.0D;
        double cz = pos.getZ() + (ring[1] + ring[3] + 1) / 2.0D;
        double dx = cameraPos.x - cx;
        double dy = cameraPos.y - (pos.getY() + 0.5D);
        double dz = cameraPos.z - cz;
        double toRing = Math.sqrt(dx * dx + dy * dy + dz * dz);
        state.near = own <= REDUCED_DETAIL;
        state.ringFull = state.formed && toRing <= FULL_DETAIL;
        state.ringReduced = state.formed && !state.ringFull && toRing <= REDUCED_DETAIL;
        state.seam = state.formed && toRing <= SEAM_DETAIL;
        state.flicker = state.lamp.animate && readout == PowerRenderHelper.READOUT_HOT;

        // Explicit null check (not folded into a flag) so ecj's null-flow analysis can track it.
        if (level == null || !state.lamp.animate) {
            state.tick = 0L;
            state.phase = (float) controller.displayPos; // the ribbon stands where it was
            return;
        }
        long tick = level.getGameTime();
        state.tick = tick;
        float speed = Mth.clamp(controller.ioBucket(), 0, 4) * TRAVEL_PER_BUCKET;
        if (!controller.displayInit) {
            controller.displayPos = controller.prevDisplayPos = 0.0D;
            controller.displayInit = true;
            controller.displayLastTick = tick;
        } else if (tick != controller.displayLastTick) {
            controller.displayLastTick = tick;
            controller.prevDisplayPos = controller.displayPos;
            controller.displayPos = (controller.displayPos + speed) % WAVELENGTH;
        }
        double prev = controller.prevDisplayPos;
        double cur = controller.displayPos;
        if (cur < prev) {
            cur += WAVELENGTH;
        }
        state.phase = (float) ((prev + (cur - prev) * partialTick) % WAVELENGTH);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState cameraState) {
        if (state.near) {
            submitController(state, poseStack, collector);
        }
        if (!state.formed) {
            return;
        }
        // Ring geometry is in world axes, relative to the controller block: no facing rotation.
        final int minX = state.minDx;
        final int minZ = state.minDz;
        final int maxX = state.maxDx;
        final int maxZ = state.maxDz;
        final int width = maxX - minX + 1;
        final int depth = maxZ - minZ + 1;
        final int perimeter = 2 * (width + depth) - 4;
        final int home = ringIndex(0, 0, minX, minZ, maxX, maxZ);
        final int readout = state.lamp.readout;
        final boolean failing = readout == PowerRenderHelper.READOUT_FAIL;
        final boolean ribbon = !state.lockout && (failing ? PowerRenderHelper.lampLit(state.lamp) : state.charge > 0);
        final int step = state.ringFull ? 1 : 2;
        if (ribbon && (state.ringFull || state.ringReduced)) {
            final float level = state.charge / (float) GAUGE_CELLS;
            final float phase = state.phase;
            final boolean flicker = state.flicker;
            final long tick = state.tick;
            collector.order(5).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FIELD_TEX), (pose, c) -> {
                for (int side = 0; side < 4; side++) {
                    int cells = sideCells(side, width, depth);
                    for (int i = 0; i < cells; i += step) {
                        int span = Math.min(step, cells - i);
                        int index = sideIndex(side, i, width, depth);
                        int rel = Math.floorMod(index - home, perimeter);
                        if (flicker && Math.floorMod(tick / 3L + rel, 4L) == 0L) {
                            continue;
                        }
                        int rgb;
                        if (failing) {
                            rgb = PowerRenderHelper.lerpColor(0.6F, PowerRenderHelper.ARC_GLOW_RGB, 0xFFFFFF);
                        } else {
                            float crest = 0.5F + 0.5F * Mth.cos((rel - phase) * ((float) Math.PI * 2.0F) / WAVELENGTH);
                            int body = PowerRenderHelper.lerpColor(level, PowerRenderHelper.ARC_RGB,
                                    PowerRenderHelper.ARC_LIGHT_RGB);
                            rgb = PowerRenderHelper.lerpColor(crest * (0.25F + 0.5F * level), body,
                                    PowerRenderHelper.ARC_GLOW_RGB);
                        }
                        ribbonQuad(c, pose, rgb, side, i, span, minX, minZ, maxX, maxZ);
                    }
                }
            });
        }
        if (state.ringFull && state.warmth < 10) {
            // Rime on every second coil; coils nearest the controller (clockwise) thaw first.
            final int thawed = perimeter * state.warmth / 10;
            collector.order(6).submitCustomGeometry(poseStack, RenderTypes.entityCutout(FROST_TEX), (pose, c) -> {
                for (int side = 0; side < 4; side++) {
                    int cells = sideCells(side, width, depth);
                    for (int i = 0; i < cells; i++) {
                        int index = sideIndex(side, i, width, depth);
                        int rel = Math.floorMod(index - home, perimeter);
                        if ((rel & 1) == 0 || rel < thawed) {
                            continue;
                        }
                        frostQuad(c, pose, side, i, minX, minZ, maxX, maxZ);
                    }
                }
            });
        }
        if (state.seam) {
            final int seamRgb = seamColor(state);
            if (seamRgb >= 0) {
                collector.order(7).submitCustomGeometry(poseStack,
                        RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX), (pose, c) -> {
                            float midX = (minX + maxX + 1) / 2.0F;
                            float midZ = (minZ + maxZ + 1) / 2.0F;
                            float halfX = width / 2.0F - SEAM_INSET;
                            float halfZ = depth / 2.0F - SEAM_INSET;
                            int light = PowerRenderHelper.FULL_BRIGHT;
                            PowerRenderHelper.quad(c, pose, light, seamRgb, midX, SEAM_Y, minZ + SEAM_INSET,
                                    halfX, 0, 0, 0, SEAM_HALF_HEIGHT, 0, 0.0F, 1.0F);
                            PowerRenderHelper.quad(c, pose, light, seamRgb, midX, SEAM_Y, maxZ + 1 - SEAM_INSET,
                                    halfX, 0, 0, 0, SEAM_HALF_HEIGHT, 0, 0.0F, 1.0F);
                            PowerRenderHelper.quad(c, pose, light, seamRgb, minX + SEAM_INSET, SEAM_Y, midZ,
                                    0, 0, halfZ, 0, SEAM_HALF_HEIGHT, 0, 0.0F, 1.0F);
                            PowerRenderHelper.quad(c, pose, light, seamRgb, maxX + 1 - SEAM_INSET, SEAM_Y, midZ,
                                    0, 0, halfZ, 0, SEAM_HALF_HEIGHT, 0, 0.0F, 1.0F);
                        });
            }
        }
    }

    /** Lamp, gauge and flag on the controller's own front. */
    private static void submitController(State state, PoseStack poseStack, SubmitNodeCollector collector) {
        poseStack.pushPose();
        PowerRenderHelper.rotateToFacing(poseStack, state.facing);
        PowerRenderHelper.lampSubmit(state.lamp, poseStack, collector, SOCKET);
        final int lit = state.lockout ? 0 : state.charge;
        final boolean flag = state.lockout;
        // Warming: the gauge dims and, with animations on, flickers.
        final boolean hot = state.lamp.readout == PowerRenderHelper.READOUT_HOT;
        final boolean gaugeOn = !(state.flicker && (state.tick % 6L) < 2L);
        final int gaugeRgb = hot ? PowerRenderHelper.ARC_RGB : PowerRenderHelper.ARC_LIGHT_RGB;
        if ((lit > 0 && gaugeOn) || flag) {
            collector.order(4).submitCustomGeometry(poseStack, RenderTypes.entityCutout(PowerRenderHelper.LAMP_TEX),
                    (pose, c) -> {
                        if (gaugeOn) {
                            for (int n = 0; n < lit; n++) {
                                gaugeCell(c, pose, gaugeRgb, n);
                            }
                        }
                        if (flag) {
                            texels(c, pose, PowerRenderHelper.LAMP_FAIL_RGB, 13, 16, 19, 20, FLAG_Z);
                            texels(c, pose, PowerRenderHelper.LAMP_TRIP_RGB, 14, 17, 18, 19, FLAG_INNER_Z);
                        }
                    });
        }
        poseStack.popPose();
    }

    /** Seam colour for the current readout, or -1 for none this tick. */
    private static int seamColor(State state) {
        switch (state.lamp.readout) {
            case PowerRenderHelper.READOUT_WORK:
                return PowerRenderHelper.LAMP_AMBER_RGB;
            case PowerRenderHelper.READOUT_HOT:
                return PowerRenderHelper.LAMP_OVERHEAT_RGB;
            case PowerRenderHelper.READOUT_FAIL:
                return PowerRenderHelper.lampLit(state.lamp) ? PowerRenderHelper.LAMP_FAIL_RGB : -1;
            default:
                return PowerRenderHelper.LAMP_EMBER_RGB;
        }
    }

    /**
     * Gauge socket {@code n} (0..15, clockwise from the top left as the player faces the block): lights
     * the outer half of its 2x2 socket, so the lit gauge reads as a broken square outline.
     */
    private static void gaugeCell(VertexConsumer c, PoseStack.Pose pose, int rgb, int n) {
        int i;
        int j;
        if (n < 5) {
            i = n;
            j = 0;
        } else if (n < 9) {
            i = 4;
            j = n - 4;
        } else if (n < 13) {
            i = 12 - n;
            j = 4;
        } else {
            i = 0;
            j = 16 - n;
        }
        int x = GAUGE_X + i * GAUGE_PITCH;
        int y = GAUGE_Y + j * GAUGE_PITCH;
        if (j == 0) {
            texels(c, pose, rgb, x, y, x + 2, y + 1, GAUGE_Z);
        } else if (j == 4) {
            texels(c, pose, rgb, x, y + 1, x + 2, y + 2, GAUGE_Z);
        } else if (i == 4) {
            texels(c, pose, rgb, x + 1, y, x + 2, y + 2, GAUGE_Z);
        } else {
            texels(c, pose, rgb, x, y, x + 1, y + 2, GAUGE_Z);
        }
    }

    /** A full-bright quad over a texel rectangle of the 32-texel north face (x1, y1 exclusive). */
    private static void texels(VertexConsumer c, PoseStack.Pose pose, int rgb, int tx0, int ty0, int tx1, int ty1,
            float z) {
        float x0 = 1.0F - tx1 / 32.0F;
        float x1 = 1.0F - tx0 / 32.0F;
        float y0 = 1.0F - ty1 / 32.0F;
        float y1 = 1.0F - ty0 / 32.0F;
        PowerRenderHelper.face(c, pose, PowerRenderHelper.FULL_BRIGHT, rgb, 0, 0, -1,
                x0, y0, z, 1, 1, x0, y1, z, 1, 0, x1, y1, z, 0, 0, x1, y0, z, 0, 1);
    }

    // ---- ring walk -------------------------------------------------------------------------------
    // Sides in clockwise order seen from above (north row west to east, east column north to south,
    // south row east to west, west column south to north). Rows own the corners.

    static int sideCells(int side, int width, int depth) {
        return (side & 1) == 0 ? width : depth - 2;
    }

    /** Clockwise perimeter index of cell {@code i} on {@code side}. */
    static int sideIndex(int side, int i, int width, int depth) {
        switch (side) {
            case 0:
                return i;
            case 1:
                return width + i;
            case 2:
                return width + depth - 2 + i;
            default:
                return 2 * width + depth - 2 + i;
        }
    }

    /** Clockwise perimeter index of the ring cell at offset ({@code dx}, {@code dz}). */
    static int ringIndex(int dx, int dz, int minX, int minZ, int maxX, int maxZ) {
        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (dz == minZ) {
            return dx - minX;
        }
        if (dz == maxZ) {
            return width + depth - 2 + (maxX - dx);
        }
        if (dx == maxX) {
            return width + (dz - minZ - 1);
        }
        return 2 * width + depth - 2 + (maxZ - 1 - dz);
    }

    /** The ribbon over {@code span} cells starting at cell {@code i} of {@code side}, on the inner edge. */
    private static void ribbonQuad(VertexConsumer c, PoseStack.Pose pose, int rgb, int side, int i, int span,
            int minX, int minZ, int maxX, int maxZ) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        float half = span / 2.0F;
        switch (side) {
            case 0:
                PowerRenderHelper.quad(c, pose, light, rgb, minX + i + half, RIBBON_Y, minZ + 0.5F + RIBBON_OFFSET,
                        half, 0, 0, 0, RIBBON_HALF_HEIGHT, 0, RIBBON_V0, RIBBON_V1);
                break;
            case 1:
                PowerRenderHelper.quad(c, pose, light, rgb, maxX + 0.5F - RIBBON_OFFSET, RIBBON_Y, minZ + 1 + i + half,
                        0, 0, half, 0, RIBBON_HALF_HEIGHT, 0, RIBBON_V0, RIBBON_V1);
                break;
            case 2:
                PowerRenderHelper.quad(c, pose, light, rgb, maxX + 1 - i - half, RIBBON_Y, maxZ + 0.5F - RIBBON_OFFSET,
                        half, 0, 0, 0, RIBBON_HALF_HEIGHT, 0, RIBBON_V0, RIBBON_V1);
                break;
            default:
                PowerRenderHelper.quad(c, pose, light, rgb, minX + 0.5F + RIBBON_OFFSET, RIBBON_Y, maxZ - i - half,
                        0, 0, half, 0, RIBBON_HALF_HEIGHT, 0, RIBBON_V0, RIBBON_V1);
                break;
        }
    }

    /** Rime lying on the back of the coil at cell {@code i} of {@code side}. */
    private static void frostQuad(VertexConsumer c, PoseStack.Pose pose, int side, int i,
            int minX, int minZ, int maxX, int maxZ) {
        int light = PowerRenderHelper.FULL_BRIGHT;
        float x;
        float z;
        boolean alongX = (side & 1) == 0;
        switch (side) {
            case 0:
                x = minX + i + 0.5F;
                z = minZ + 0.5F;
                break;
            case 1:
                x = maxX + 0.5F;
                z = minZ + 1 + i + 0.5F;
                break;
            case 2:
                x = maxX - i + 0.5F;
                z = maxZ + 0.5F;
                break;
            default:
                x = minX + 0.5F;
                z = maxZ - 1 - i + 0.5F;
                break;
        }
        if (alongX) {
            PowerRenderHelper.quad(c, pose, light, 0xFFFFFF, x, FROST_Y, z, 0.5F, 0, 0, 0, 0, TUBE_HALF, 0.0F, 1.0F);
        } else {
            PowerRenderHelper.quad(c, pose, light, 0xFFFFFF, x, FROST_Y, z, 0, 0, 0.5F, TUBE_HALF, 0, 0, 0.0F, 1.0F);
        }
    }
}

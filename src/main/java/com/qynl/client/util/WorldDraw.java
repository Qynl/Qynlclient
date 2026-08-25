package com.qynl.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Small helper for world-space overlay drawing (Qynl / Director / Search /
 * StorageESP / Tracers / NameTags). All draws are camera-relative: pass the
 * camera position and emit vertices offset by it, then let the caller's
 * {@code bufferSource.endBatch()} flush the batch.
 */
public final class WorldDraw {

    private WorldDraw() {
    }

    /**
     * Draws a 12-edge wireframe box spanning an arbitrary AABB, camera-relative.
     */
    public static void drawAABB(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                float r, float g, float b, float a) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        double x1 = minX - camPos.x, y1 = minY - camPos.y, z1 = minZ - camPos.z;
        double x2 = maxX - camPos.x, y2 = maxY - camPos.y, z2 = maxZ - camPos.z;

        // bottom square
        edge(consumer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        edge(consumer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        edge(consumer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        edge(consumer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // top square
        edge(consumer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        edge(consumer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        edge(consumer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        edge(consumer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // verticals
        edge(consumer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        edge(consumer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        edge(consumer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        edge(consumer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    /**
     * Same box via a plain AABB (already camera-relative or world-space with
     * camPos subtracted). Uses the vanilla box renderer for a 1-block cube.
     */
    public static void drawBox(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos,
                               double x, double y, double z, float r, float g, float b, float a) {
        Vec3 p = Vec3.atLowerCornerOf(BlockPos.containing(x, y, z)).subtract(camPos);
        AABB box = new AABB(p.x, p.y, p.z, p.x + 1, p.y + 1, p.z + 1);
        LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()),
                box, (int) (r * 255), (int) (g * 255), (int) (b * 255), (int) (a * 255));
    }

    /** Draws a single line segment from (x1,y1,z1) to (x2,y2,z2), camera-relative. */
    public static void line(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos,
                            double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();
        edge(consumer, matrix, x1 - camPos.x, y1 - camPos.y, z1 - camPos.z,
                x2 - camPos.x, y2 - camPos.y, z2 - camPos.z, r, g, b, a);
    }

    /**
     * True when no full-cube block sits between the player's eye and the
     * given point — used so combat assists never attack through solid walls.
     */
    public static boolean hasLineOfSight(Minecraft client, double tx, double ty, double tz) {
        if (client == null || client.player == null || client.level == null) return false;
        Vec3 eye = client.player.getEyePosition();
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.2) return true;
        double steps = Math.max(1.0, Math.ceil(dist / 0.25));
        for (int i = 1; i < steps; i++) {
            double t = i / steps;
            BlockPos pos = BlockPos.containing(eye.x + dx * t, eye.y + dy * t, eye.z + dz * t);
            if (client.level.getBlockState(pos).isSolidRender(client.level, pos)) {
                return false;
            }
        }
        return true;
    }

    private static void edge(VertexConsumer consumer, Matrix4f matrix,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        consumer.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a);
        consumer.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a);
    }
}

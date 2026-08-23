package com.qynl.client189;

import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Small helper for world-space overlay drawing (Search / StorageESP /
 * Tracers / NameTags). All draws are camera-relative: call {@link #begin}
 * before a frame of lines, then emit vertices offset by the camera position,
 * then {@link #end}.
 */
public final class WorldDraw {

    private WorldDraw() {
    }

    /** Sets up the GL state for world-space overlay lines/boxes. */
    public static void begin(boolean throughWalls) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        if (throughWalls) {
            GlStateManager.disableDepthTest();
        }
        GlStateManager.disableCull();
    }

    /** Restores the GL state after overlay drawing. */
    public static void end() {
        GlStateManager.enableCull();
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.enableTexture();
        GlStateManager.popMatrix();
    }

    /**
     * Draws a 12-edge wireframe box (one block, from (x, y, z) to
     * (x + 1, y + 1, z + 1)) camera-relative.
     */
    public static void drawBox(double x, double y, double z,
                               float r, float g, float b, float a,
                               double camX, double camY, double camZ) {
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);
        double x1 = x - camX, y1 = y - camY, z1 = z - camZ;
        double x2 = x1 + 1.0, y2 = y1 + 1.0, z2 = z1 + 1.0;
        // bottom square
        line(x1, y1, z1, x2, y1, z1);
        line(x2, y1, z1, x2, y1, z2);
        line(x2, y1, z2, x1, y1, z2);
        line(x1, y1, z2, x1, y1, z1);
        // top square
        line(x1, y2, z1, x2, y2, z1);
        line(x2, y2, z1, x2, y2, z2);
        line(x2, y2, z2, x1, y2, z2);
        line(x1, y2, z2, x1, y2, z1);
        // verticals
        line(x1, y1, z1, x1, y2, z1);
        line(x2, y1, z1, x2, y2, z1);
        line(x2, y1, z2, x2, y2, z2);
        line(x1, y1, z2, x1, y2, z2);
        GL11.glEnd();
    }

    /**
     * Draws a 12-edge wireframe box spanning an arbitrary AABB from
     * (minX, minY, minZ) to (maxX, maxY, maxZ), camera-relative.
     */
    public static void drawAABB(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                float r, float g, float b, float a,
                                double camX, double camY, double camZ) {
        GL11.glLineWidth(1.5F);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);
        double x1 = minX - camX, y1 = minY - camY, z1 = minZ - camZ;
        double x2 = maxX - camX, y2 = maxY - camY, z2 = maxZ - camZ;
        // bottom square
        line(x1, y1, z1, x2, y1, z1);
        line(x2, y1, z1, x2, y1, z2);
        line(x2, y1, z2, x1, y1, z2);
        line(x1, y1, z2, x1, y1, z1);
        // top square
        line(x1, y2, z1, x2, y2, z1);
        line(x2, y2, z1, x2, y2, z2);
        line(x2, y2, z2, x1, y2, z2);
        line(x1, y2, z2, x1, y2, z1);
        // verticals
        line(x1, y1, z1, x1, y2, z1);
        line(x2, y1, z1, x2, y2, z1);
        line(x2, y1, z2, x2, y2, z2);
        line(x1, y1, z2, x1, y2, z2);
        GL11.glEnd();
    }

    /**
     * True when no full-cube block sits between the player's eye and the
     * given point — used so combat assists never attack through solid walls
     * (an attack through a wall is an instant ban on Grim/Intave).
     */
    public static boolean hasLineOfSight(net.minecraft.client.MinecraftClient client,
                                         double tx, double ty, double tz) {
        if (client == null || client.player == null || client.world == null) return false;
        net.minecraft.util.math.Vec3d eye = client.player.getCameraPosVec(1.0F);
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.2) return true;
        double steps = Math.max(1.0, Math.ceil(dist / 0.25));
        for (int i = 1; i < steps; i++) {
            double t = i / steps;
            net.minecraft.util.math.BlockPos pos =
                    new net.minecraft.util.math.BlockPos(eye.x + dx * t, eye.y + dy * t, eye.z + dz * t);
            if (client.world.getBlockState(pos).getBlock().isFullCube()) {
                return false;
            }
        }
        return true;
    }

    /** Draws a single line segment from (x1,y1,z1) to (x2,y2,z2). */
    public static void line(double x1, double y1, double z1,
                            double x2, double y2, double z2,
                            float r, float g, float b, float a) {
        GL11.glBegin(GL11.GL_LINES);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }

    private static void line(double x1, double y1, double z1,
                             double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }
}

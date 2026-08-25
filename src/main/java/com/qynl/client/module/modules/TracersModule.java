package com.qynl.client.module.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qynl.client.Friends;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Tracers — draws a colored line to every entity in range.
 * Players red, mobs orange, friends green.
 */
public class TracersModule extends Module {
    private static TracersModule instance;

    public TracersModule() {
        super("Tracers", "Draws a line to every entity in range — players red, mobs orange, friends green.",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("maxDist", "Max distance", 40.0, 16, 128, 8, "b"));
    }

    public static TracersModule getInstance() { return instance; }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (!isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        double maxDist = getDoubleSetting("maxDist");
        Vec3 playerEye = client.player.getEyePosition();

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) continue;
            if (!(entity instanceof Player || entity instanceof Monster)) continue;
            if (entity instanceof LivingEntity living && !living.isAlive()) continue;
            // Ghost/invalid entities (mid world-load, NaN positions) would push
            // garbage vertices into the GPU — skip anything non-finite.
            Vec3 entityPos = entity.getBoundingBox().getCenter();
            if (!Double.isFinite(entityPos.x) || !Double.isFinite(entityPos.y)
                    || !Double.isFinite(entityPos.z)) continue;
            double dist = playerEye.distanceTo(entityPos);
            if (!Double.isFinite(dist) || dist > maxDist) continue;

            Vec3 renderPos = entityPos.subtract(camPos);
            int color;
            if (entity instanceof Player player) {
                color = Friends.isFriend(player.getName().getString()) ? 0xFF55FF55 : 0xFFFF5555;
            } else {
                color = 0xFFFF8800;
            }

            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            consumer.addVertex(matrix, 0, 0, 0).setColor(r, g, b, 1.0f);
            consumer.addVertex(matrix, (float) renderPos.x, (float) renderPos.y, (float) renderPos.z)
                    .setColor(r, g, b, 1.0f);
        }
    }
}
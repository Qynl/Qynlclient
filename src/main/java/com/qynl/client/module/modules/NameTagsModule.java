package com.qynl.client.module.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qynl.client.Friends;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * NameTags — renders entity nametags through walls.
 * Friends green, enemies red, mobs yellow.
 */
public class NameTagsModule extends Module {
    private static NameTagsModule instance;

    public NameTagsModule() {
        super("NameTags", "Renders entity nametags through walls — friends green, enemies red.",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("maxDist", "Max distance", 64.0, 16, 128, 8, "b"));
        addSetting(Setting.range("scale", "Scale", 1.0, 0.5, 2.0, 0.1));
        addSetting(Setting.options("showHealth", "Show health", "On", "On", "Off"));
    }

    public static NameTagsModule getInstance() { return instance; }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (!isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        double maxDist = getDoubleSetting("maxDist");
        double scale = getDoubleSetting("scale");
        boolean showHealth = "On".equals(getStringSetting("showHealth"));
        Font font = client.font;
        Vec3 playerEye = client.player.getEyePosition();

        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity == client.player) continue;
            if (!(entity instanceof Player || entity instanceof Monster)) continue;
            if (!living.isAlive()) continue;

            Vec3 entityPos = entity.getEyePosition();
            double dist = playerEye.distanceTo(entityPos);
            if (dist > maxDist) continue;

            Vec3 renderPos = entityPos.subtract(camPos);

            int color;
            String name;
            if (entity instanceof Player player) {
                name = player.getName().getString();
                color = Friends.isFriend(name) ? 0xFF55FF55 : 0xFFFF5555;
            } else {
                name = entity.getName().getString();
                color = 0xFFFFAA00;
            }

            if (showHealth && living instanceof LivingEntity le) {
                name += " " + String.format("%.0f", le.getHealth()) + "HP";
            }

            poseStack.pushPose();
            poseStack.translate(renderPos.x, renderPos.y + 0.5, renderPos.z);
            poseStack.mulPose(client.gameRenderer.getMainCamera().rotation());
            poseStack.scale((float) (-0.025 * scale), (float) (-0.025 * scale), (float) (0.025 * scale));

            Matrix4f matrix = poseStack.last().pose();
            float bgOpacity = 0.5f;
            int width = font.width(name) / 2;
            font.drawInBatch(Component.literal(name),
                    -width, -4, color, false, matrix, bufferSource,
                    Font.DisplayMode.SEE_THROUGH,
                    (int) (bgOpacity * 255) << 24, 0xF000F0);
            font.drawInBatch(Component.literal(name),
                    -width, -4, color, false, matrix, bufferSource,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);

            poseStack.popPose();
        }
    }
}
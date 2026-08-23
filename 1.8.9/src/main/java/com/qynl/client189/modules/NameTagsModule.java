package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * NameTags — renders entity nametags through walls (Vape-style). Names are
 * drawn as billboards at the entity's head, always facing you, so enemies
 * behind walls are still labelled. Friends are green, enemies red.
 */
public class NameTagsModule extends Module {
    private static NameTagsModule instance;

    public NameTagsModule() {
        super("NameTags", "Renders entity nametags through walls.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("range", "Range", 32.0, 8, 64, 4, "b"));
        addSetting(Setting.options("players", "Players", "On", "On", "Off"));
        addSetting(Setting.options("mobs", "Mobs", "On", "On", "Off"));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    /** Called from the world render hook every frame. */
    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        double range = instance.getDoubleSetting("range");
        boolean players = "On".equals(instance.getStringSetting("players"));
        boolean mobs = "On".equals(instance.getStringSetting("mobs"));

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        float cameraYaw = client.player.yaw;
        float cameraPitch = client.player.pitch;
        TextRenderer font = client.textRenderer;

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            if (entity == client.player || !entity.isAlive()) continue;

            if (entity instanceof PlayerEntity && !players) continue;
            if (entity instanceof MobEntity && !mobs) continue;
            if (!(entity instanceof PlayerEntity) && !(entity instanceof MobEntity)) continue;

            double dx = entity.x - client.player.x;
            double dy = entity.y - client.player.y;
            double dz = entity.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz > range * range) continue;

            String name = FriendsModule.entityName(entity);
            if (name == null || name.isEmpty()) {
                name = entity.getClass().getSimpleName().replace("Entity", "");
            }
            int color;
            if (entity instanceof PlayerEntity && FriendsModule.isFriend(name)) {
                color = 0xFF4ADE80;
            } else if (entity instanceof PlayerEntity) {
                color = 0xFFFF6B6B;
            } else {
                color = 0xFFFFCC80;
            }

            // Billboarding: translate to the entity's head, undo the camera
            // rotation, scale to nametag size (vanilla 1.8 technique).
            GlStateManager.pushMatrix();
            GlStateManager.disableDepthTest();
            GL11.glTranslated(entity.x - camX, entity.y - camY + entity.getEyeHeight() + 0.5, entity.z - camZ);
            GL11.glRotatef(-cameraYaw, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(cameraPitch, 1.0F, 0.0F, 0.0F);
            GL11.glScalef(-0.025F, -0.025F, 0.025F);
            float w = font.getStringWidth(name);
            font.drawWithShadow(name, -w / 2.0F, 0.0F, color);
            GlStateManager.enableDepthTest();
            GlStateManager.popMatrix();
        }
    }
}

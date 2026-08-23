package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;

/**
 * Tracers — draws a line from your view to every entity in range. Players
 * are red (friends green), monsters orange, so you always know where the
 * next target is, even behind you.
 */
public class TracersModule extends Module {
    private static TracersModule instance;

    public TracersModule() {
        super("Tracers", "Draws a line to every entity in range.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("range", "Range", 48.0, 8, 96, 4, "b"));
        addSetting(Setting.options("players", "Players", "On", "On", "Off"));
        addSetting(Setting.options("mobs", "Mobs", "On", "On", "Off"));
        addSetting(Setting.options("throughWalls", "Through walls", "On", "On", "Off"));
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
        boolean through = "On".equals(instance.getStringSetting("throughWalls"));

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(through);
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

            float r, g, b;
            String name = FriendsModule.entityName(entity);
            if (entity instanceof PlayerEntity && FriendsModule.isFriend(name)) {
                r = 0.30f; g = 0.90f; b = 0.50f; // friend green
            } else if (entity instanceof PlayerEntity) {
                r = 0.95f; g = 0.35f; b = 0.35f; // enemy red
            } else {
                r = 1.00f; g = 0.60f; b = 0.20f; // mob orange
            }

            double targetY = entity.y + entity.getEyeHeight() - camY;
            WorldDraw.line(0, 0, 0, entity.x - camX, targetY, entity.z - camZ, r, g, b, 0.9f);
        }
        WorldDraw.end();
    }
}

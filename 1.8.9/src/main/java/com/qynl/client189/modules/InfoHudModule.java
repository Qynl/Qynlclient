package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * InfoHudModule for 1.8.9 — FPS, coordinates, direction, biome, time and ping.
 * Mirrors the 1.21.1 InfoHUD module.
 */
public class InfoHudModule extends Module {
    private int frameCount = 0;
    private long lastFpsReset = System.currentTimeMillis();
    private int fps = 0;

    public InfoHudModule() {
        super("InfoHUD", "Show FPS, coordinates, direction, biome, time of day and ping.", Category.INFO);
    }

    /**
     * FPS is measured by counting calls (one per rendered frame) and
     * averaging over a one-second window.
     */
    public String fps(MinecraftClient client) {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastFpsReset >= 1000L) {
            fps = frameCount;
            frameCount = 0;
            lastFpsReset = now;
        }
        return "FPS " + Math.max(fps, frameCount);
    }

    public String coords(MinecraftClient client) {
        if (client.player == null) {
            return "";
        }
        BlockPos pos = new BlockPos(client.player.x, client.player.y, client.player.z);
        return String.format("XYZ %d %d %d", pos.getX(), pos.getY(), pos.getZ());
    }

    public String direction(MinecraftClient client) {
        if (client.player == null) {
            return "";
        }
        // 1.8.9: yaw 0 = South (+Z), 90 = West (-X), 180 = North, 270 = East.
        float yaw = ((client.player.yaw % 360.0F) + 360.0F) % 360.0F;
        int dir = ((int) Math.floor((yaw + 45.0) / 90.0)) % 4;
        String[] names = {"South", "West", "North", "East"};
        return "Facing " + names[dir] + " (" + Math.round(yaw) + "\u00b0)";
    }

    public String biome(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return "";
        }
        BlockPos pos = new BlockPos(client.player.x, client.player.y, client.player.z);
        String name = client.world.getBiome(pos).name;
        return "Biome " + (name == null ? "?" : name);
    }

    public String time(MinecraftClient client) {
        if (client.world == null) {
            return "";
        }
        long dayTime = client.world.getTimeOfDay() % 24000L;
        int hours = (int) ((dayTime / 1000 + 6) % 24);
        int minutes = (int) ((dayTime % 1000) / 1000.0 * 60);
        return String.format("Time %02d:%02d", hours, minutes);
    }

    public String ping(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) {
            return "Ping --";
        }
        net.minecraft.client.network.PlayerListEntry info =
                client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return info != null ? "Ping " + info.getLatency() + "ms" : "Ping --";
    }
}

package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.input.Keyboard;

/**
 * AntiAFK — gently turns your view now and then so servers do not kick you
 * for being idle. Only acts while you are truly idle, and the turn is small
 * and human-scale — exactly what a player glancing around would do.
 */
public class AntiAfkModule extends Module {
    private int ticks = 0;

    public AntiAfkModule() {
        super("AntiAFK", "Gently turns your view now and then so servers do not kick you for being idle.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("interval", "Interval", 30.0, 10, 120, 5, "s"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        // Only act when the player is truly idle.
        if (player.input.movementForward != 0.0F || player.input.movementSideways != 0.0F
                || player.input.jumping || player.isUsingItem()) {
            ticks = 0;
            return;
        }
        ticks++;
        int intervalTicks = (int) (getDoubleSetting("interval") * 20.0);
        if (ticks >= intervalTicks) {
            ticks = 0;
            player.yaw += 20.0F;
        }
    }
}

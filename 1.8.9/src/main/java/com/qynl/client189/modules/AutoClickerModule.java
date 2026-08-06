package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public class AutoClickerModule extends Module {
    private final Random random = new Random();
    private int clickTimer = 0;
    private int nextInterval = 4;
    private int burstClicks = 0;
    private int burstDelay = 0;
    private double currentCps = 10.0;

    public AutoClickerModule() {
        super("AutoClicker", "Clicks for you with human-like timing variance.", Category.ASSIST);
        bindKey(Keyboard.KEY_G);
        addSetting(Setting.range("cps",       "Target CPS", 10.0, 5, 16, 1));
        addSetting(Setting.range("jitter",     "Jitter",      8.0, 0, 20, 1, "%"));
        addSetting(Setting.options("pattern",  "Pattern",    "Steady", "Steady", "Burst"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            clickTimer = 0;
            return;
        }
        if (client.currentScreen != null || !client.options.keyAttack.isPressed()) {
            clickTimer = 0;
            burstClicks = 0;
            burstDelay = 0;
            return;
        }

        // Burst mode: click 2-4 times fast, then pause briefly
        if ("Burst".equals(getStringSetting("pattern")) && burstDelay > 0) {
            burstDelay--;
            return;
        }

        clickTimer++;
        if (clickTimer < nextInterval) return;
        clickTimer = 0;

        double baseCps = getDoubleSetting("cps");
        double jitter = getDoubleSetting("jitter") / 100.0;

        // Slowly drift CPS for human feel
        currentCps += (random.nextDouble() - 0.5) * jitter * 4.0;
        currentCps = Math.max(baseCps * (1.0 - jitter), Math.min(baseCps * (1.0 + jitter), currentCps));

        double effectiveCps = Math.max(1, currentCps);
        nextInterval = Math.max(1, (int) Math.round(20.0 / effectiveCps) + random.nextInt(5) - 2);
        if (nextInterval < 1) nextInterval = 1;

        ((MinecraftClientInvoker) client).invokeDoAttack();

        // Burst tracking
        if ("Burst".equals(getStringSetting("pattern"))) {
            burstClicks++;
            if (burstClicks >= 2 + random.nextInt(3)) {
                burstClicks = 0;
                burstDelay = 3 + random.nextInt(4); // small pause between bursts
            }
        }
    }
}

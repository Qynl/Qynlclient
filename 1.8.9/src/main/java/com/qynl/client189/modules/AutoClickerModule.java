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
    private int clickTimer = 0, nextInterval = 4;

    public AutoClickerModule() {
        super("AutoClicker", "Clicks for you while you hold attack. 1.8.9-style fast CPS.", Category.ASSIST);
        bindKey(Keyboard.KEY_G);
        addSetting(Setting.range("cps", "Clicks / sec", 10.0, 5, 16, 1));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) { clickTimer = 0; return; }
        if (client.currentScreen != null || !client.options.keyAttack.isPressed()) { clickTimer = 0; return; }
        clickTimer++;
        if (clickTimer < nextInterval) return;
        clickTimer = 0;
        double cps = getDoubleSetting("cps"); if (cps < 1) cps = 10;
        nextInterval = Math.max(1, (int) Math.round(20.0 / cps) + random.nextInt(5) - 2);
        if (nextInterval < 1) nextInterval = 1;

        // In 1.8.9, invoke doAttack via mixin invoker which handles the crosshair target internally
        ((MinecraftClientInvoker) client).invokeDoAttack();
    }
}

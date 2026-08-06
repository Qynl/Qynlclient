package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.QynlClient189;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * ReachAssist for 1.8.9 — extends reach with natural, humanized fluctuation.
 */
public class ReachAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private static double currentBonus = 0.75;
    private static double minBonus = 0.45, maxBonus = 0.95;
    private double targetBonus = 0.75;
    private int walkTimer = 0;
    private int transitionTimer = 0;

    public ReachAssistModule() {
        super("ReachAssist", "Extends reach with smooth, human-like fluctuation.", Category.ASSIST);
        bindKey(Keyboard.KEY_I);
        addSetting(Setting.options("mode",        "Mode",        "Normal", "Subtle", "Normal", "Aggressive"));
        addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
    }

    @Override
    public void onEnable() { applyBounds(); }

    @Override
    public void onTick(MinecraftClient client) {
        applyBounds();

        int interval;
        double flucRange;
        switch (getStringSetting("fluctuation")) {
            case "Low":  interval = 7; flucRange = 0.06; break;
            case "High": interval = 3; flucRange = 0.20; break;
            default:     interval = 5; flucRange = 0.12; break;
        }

        if (--walkTimer <= 0) {
            walkTimer = interval + RANDOM.nextInt(interval);
            targetBonus = minBonus + RANDOM.nextDouble() * (maxBonus - minBonus);
            transitionTimer = interval; // smooth transition over interval ticks
        }

        // Smoothly interpolate toward target for natural feel
        if (transitionTimer > 0) {
            transitionTimer--;
            double t = 1.0 - (double) transitionTimer / (walkTimer + 1);
            // Smoothstep-ish
            t = t * t * (3.0 - 2.0 * t);
            double prevBonus = currentBonus;
            currentBonus = prevBonus + (targetBonus - prevBonus) * t * 0.5;
        }

        currentBonus = MathHelper.clamp(currentBonus, minBonus, maxBonus);
    }

    private void applyBounds() {
        switch (getStringSetting("mode")) {
            case "Subtle":     minBonus = 0.25; maxBonus = 0.55; break;
            case "Aggressive": minBonus = 0.65; maxBonus = 1.15; break;
            default:           minBonus = 0.45; maxBonus = 0.95; break;
        }
        currentBonus = MathHelper.clamp(currentBonus, minBonus, maxBonus);
        targetBonus = MathHelper.clamp(targetBonus, minBonus, maxBonus);
    }

    public static double currentBonus() { return currentBonus; }
    public static boolean isActive() {
        QynlClient189 qynl = QynlClient189.getInstance();
        return qynl != null && qynl.getModuleManager().isEnabled("ReachAssist");
    }
}

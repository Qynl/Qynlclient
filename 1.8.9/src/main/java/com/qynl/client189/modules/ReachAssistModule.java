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
 * ReachAssist for 1.8.9 - extends reach with natural fluctuation.
 */
public class ReachAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private static double currentBonus = 0.7;
    private static double minBonus = 0.4, maxBonus = 0.9;
    private int walkTimer = 0;

    public ReachAssistModule() {
        super("ReachAssist", "Extends your reach with natural fluctuation.", Category.ASSIST);
        bindKey(Keyboard.KEY_I);
        addSetting(Setting.options("mode", "Mode", "Normal", "Subtle", "Normal", "Aggressive"));
        addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
    }

    @Override
    public void onEnable() { applyBounds(); }

    @Override
    public void onTick(MinecraftClient client) {
        applyBounds();
        int sc;
        switch (getStringSetting("fluctuation")) {
            case "Low": sc = 6; break;
            case "High": sc = 3; break;
            default: sc = 4; break;
        }
        if (--walkTimer <= 0) {
            walkTimer = sc + RANDOM.nextInt(sc);
            double fluc;
            switch (getStringSetting("fluctuation")) {
                case "Low": fluc = 0.08; break;
                case "High": fluc = 0.25; break;
                default: fluc = 0.15; break;
            }
            currentBonus = MathHelper.clamp(currentBonus + (RANDOM.nextDouble() - 0.5) * fluc * 2.0, minBonus, maxBonus);
        }
    }

    private void applyBounds() {
        switch (getStringSetting("mode")) {
            case "Subtle": minBonus = 0.25; maxBonus = 0.55; break;
            case "Aggressive": minBonus = 0.65; maxBonus = 1.15; break;
            default: minBonus = 0.40; maxBonus = 0.90; break;
        }
        currentBonus = MathHelper.clamp(currentBonus, minBonus, maxBonus);
    }

    public static double currentBonus() { return currentBonus; }
    public static boolean isActive() {
        QynlClient189 qynl = QynlClient189.getInstance();
        return qynl != null && qynl.getModuleManager().isEnabled("ReachAssist");
    }
}

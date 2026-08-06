package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * VelocityAssist for 1.8.9 — softens knockback with natural variance.
 */
public class VelocityAssistModule extends Module {
    private static VelocityAssistModule instance;
    private static final Random RANDOM = new Random();

    public VelocityAssistModule() {
        super("VelocityAssist", "Reduces knockback naturally — varies slightly each hit to feel legit.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_H);
        addSetting(Setting.range("horizontal",  "Horizontal %", 60.0, 0, 90, 5, "%"));
        addSetting(Setting.range("vertical",    "Vertical %",   30.0, 0, 90, 5, "%"));
        addSetting(Setting.range("variance",    "Variance",     10.0, 0, 25, 5, "%"));
    }

    public static VelocityAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /**
     * Returns horizontal reduction multiplier. Applied per-hit so variance
     * changes each time — looks like natural lag/ping jitter.
     */
    public double horizontalFactor() {
        double base = getDoubleSetting("horizontal") / 100.0;
        double var = getDoubleSetting("variance") / 100.0;
        double factor = base + (RANDOM.nextDouble() - 0.5) * var * 2.0;
        return Math.max(0.0, Math.min(0.95, 1.0 - factor));
    }

    public double verticalFactor() {
        double base = getDoubleSetting("vertical") / 100.0;
        double var = getDoubleSetting("variance") / 100.0;
        double factor = base + (RANDOM.nextDouble() - 0.5) * var * 2.0;
        return Math.max(0.0, Math.min(0.95, 1.0 - factor));
    }
}

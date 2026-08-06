package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * VelocityAssist for 1.8.9 - softens knockback by a percentage.
 */
public class VelocityAssistModule extends Module {
    private static VelocityAssistModule instance;
    private static final Random RANDOM = new Random();

    public VelocityAssistModule() {
        super("VelocityAssist", "Softens knockback by a percentage - keeps a natural portion.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_H);
        addSetting(Setting.range("horizontal", "Horizontal reduce", 60.0, 0, 90, 5, "%"));
        addSetting(Setting.range("vertical", "Vertical reduce", 30.0, 0, 90, 5, "%"));
    }

    public static VelocityAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }
    public double horizontalFactor() { return 1.0 - getDoubleSetting("horizontal") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16); }
    public double verticalFactor() { return 1.0 - getDoubleSetting("vertical") / 100.0 * (0.92 + RANDOM.nextDouble() * 0.16); }
}

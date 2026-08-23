package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

/**
 * Text GUI — shows the list of enabled modules on the HUD, Vape-style:
 * a clean, minimal list with the module name and its active mode. The
 * rendering itself lives in {@code HudRenderer189}; this module only
 * decides whether it is shown and where.
 */
public class TextGuiModule extends Module {
    private static TextGuiModule instance;

    public TextGuiModule() {
        super("Text GUI", "Shows all enabled modules on the HUD.", Category.OTHER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("pos", "Position", "TopLeft", "TopLeft", "TopRight"));
        addSetting(Setting.options("color", "Color", "Green", "Green", "White", "Grey"));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    public static boolean isRight() {
        return instance != null && "TopRight".equals(instance.getStringSetting("pos"));
    }

    public static int textColor() {
        if (instance == null) return 0xFFE5E7EB;
        switch (instance.getStringSetting("color")) {
            case "White": return 0xFFFFFFFF;
            case "Grey":  return 0xFF9CA3AF;
            default:      return 0xFF4ADE80;
        }
    }
}

package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

/**
 * StreamerMode for 1.8.9 — hides mod HUD elements from the screen.
 *
 * <p>When enabled, the module list and/or the info HUD disappear. The
 * assists still work silently in the background, but nothing is visible
 * on stream or in recordings.</p>
 */
public class StreamerModeModule extends Module {
    private static StreamerModeModule instance;

    public StreamerModeModule() {
        super("StreamerMode",
                "Hides all mod HUD elements — safe for OBS and recording. Assists still work silently.",
                Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_F8);
        addSetting(Setting.options("hideStyle", "Hide style", "All", "All", "HUD only", "Module list"));
    }

    public static StreamerModeModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** Backwards-compatible: hides everything. */
    public static boolean shouldHide() {
        return shouldHide("any");
    }

    /**
     * Returns true if the caller should skip rendering because StreamerMode
     * wants to hide that type of content.
     *
     * @param type "all" (module list), "hud" (info/keystrokes/durability), or "any"
     */
    public static boolean shouldHide(String type) {
        if (!isActive()) return false;
        String style = instance.getStringSetting("hideStyle");
        if ("All".equals(style)) return true;
        if ("Module list".equals(style) && "all".equals(type)) return true;
        if ("HUD only".equals(style) && "hud".equals(type)) return true;
        return false;
    }
}

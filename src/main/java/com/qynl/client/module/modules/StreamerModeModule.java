package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import org.lwjgl.glfw.GLFW;

/**
 * StreamerMode — hides ALL QynlClient HUD elements from the screen.
 *
 * <p>When enabled, the module list, info HUD, keystrokes, durability
 * warnings, death coords — everything the mod draws — disappears.
 * The assists still work silently in the background, but nothing is
 * visible on stream or in recordings.</p>
 *
 * <p>Toggle with a keybind or click the module — OBS-safe with one press.</p>
 */
public class StreamerModeModule extends Module {

    private static StreamerModeModule instance;

    public StreamerModeModule() {
        super("StreamerMode",
                "Hides all mod HUD elements — safe for OBS and recording. Assists still work silently.",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_F8);
        addSetting(Setting.options("hideStyle", "Hide style", "All", "All", "HUD only", "Module list"));
    }

    public static StreamerModeModule getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
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

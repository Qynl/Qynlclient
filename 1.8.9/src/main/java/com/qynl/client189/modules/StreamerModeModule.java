package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

/**
 * StreamerMode — hides the Text GUI from the screen while the assists keep
 * working silently in the background, safe for OBS and recordings.
 */
public class StreamerModeModule extends Module {
    private static StreamerModeModule instance;

    public StreamerModeModule() {
        super("StreamerMode",
                "Hides the HUD — safe for OBS and recording. Assists still work silently.",
                Category.OTHER);
        instance = this;
        bindKey(Keyboard.KEY_F8);
        addSetting(Setting.options("hideStyle", "Hide style", "All", "All", "Text GUI"));
    }

    public static StreamerModeModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** Backwards-compatible: hides everything. */
    public static boolean shouldHide() {
        return shouldHide("all");
    }

    /**
     * Returns true if the caller should skip rendering because StreamerMode
     * wants to hide that type of content.
     *
     * @param type "all" (Text GUI / module list) or "any"
     */
    public static boolean shouldHide(String type) {
        if (!isActive()) return false;
        String style = instance.getStringSetting("hideStyle");
        if ("All".equals(style)) return true;
        return "Text GUI".equals(style) && "all".equals(type);
    }
}

package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import org.lwjgl.input.Keyboard;

/**
 * StreamerMode for 1.8.9 — hides ALL mod HUD elements from the screen.
 *
 * <p>When enabled, the module list disappears. The assists still work
 * silently in the background, but nothing is visible on stream or in
 * recordings.</p>
 */
public class StreamerModeModule extends Module {
    private static StreamerModeModule instance;

    public StreamerModeModule() {
        super("StreamerMode",
                "Hides all mod HUD elements — safe for OBS and recording. Assists still work silently.",
                Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_F8);
        addSetting(Setting.options("hideStyle", "Hide style", "All", "All", "Module list"));
    }

    public static StreamerModeModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    public static boolean shouldHide() {
        return isActive();
    }
}

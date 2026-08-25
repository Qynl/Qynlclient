package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * NoViewBob — removes walking view bobbing.
 * Movement and camera control untouched (render-only, silent).
 */
public class NoViewBobModule extends Module {
    private static NoViewBobModule instance;

    public NoViewBobModule() {
        super("NoViewBob", "Removes walking view bobbing — movement and camera control untouched (render-only).",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
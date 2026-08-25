package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * NoHurtCam — removes the damage camera tilt.
 * Damage and knockback untouched (render-only, silent).
 */
public class NoHurtCamModule extends Module {
    private static NoHurtCamModule instance;

    public NoHurtCamModule() {
        super("NoHurtCam", "Removes the damage camera tilt — damage and knockback untouched (render-only).",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}
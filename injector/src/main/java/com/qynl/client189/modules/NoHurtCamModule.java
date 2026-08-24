package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/** Disables the visual camera tilt caused by taking damage. */
public class NoHurtCamModule extends Module {
    private static NoHurtCamModule instance;

    public NoHurtCamModule() {
        super("NoHurtCam", "Removes the damage camera tilt while leaving damage and knockback unchanged.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

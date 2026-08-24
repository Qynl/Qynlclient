package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/** Disables the visual walking bob while preserving normal movement. */
public class NoViewBobModule extends Module {
    private static NoViewBobModule instance;

    public NoViewBobModule() {
        super("NoViewBob", "Removes walking view bobbing while leaving movement and camera control unchanged.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * AutoWalk — walks forward automatically so you can free your hands while
 * travelling. Applied via {@link com.qynl.client189.mixin.InputMixin} at the
 * correct moment in the tick; pauses while any screen is open.
 */
public class AutoWalkModule extends Module {
    private static AutoWalkModule instance;

    public AutoWalkModule() {
        super("AutoWalk", "Walk forward automatically — free your hands while travelling.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_F5);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

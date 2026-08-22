package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * ToggleSneak — keeps you sneaking without holding the key, handy while
 * building or bridging. While enabled, sneak is forced every tick via
 * {@link com.qynl.client189.mixin.InputMixin}; disable the module to stand
 * up again.
 */
public class ToggleSneakModule extends Module {
    private static ToggleSneakModule instance;

    public ToggleSneakModule() {
        super("ToggleSneak", "Sneak without holding the key down — handy while building.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    /** Whether the module is currently forcing sneak. Checked by InputMixin. */
    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

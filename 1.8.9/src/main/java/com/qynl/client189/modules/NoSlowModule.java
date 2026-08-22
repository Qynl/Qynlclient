package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * NoSlow — removes the movement slowdown while using items (eating, blocking
 * with a sword, drawing a bow, drinking). Purely client-side input handling
 * (see {@link com.qynl.client189.mixin.InputMixin}): no packets are changed,
 * so it looks like ordinary, fluid movement to the server.
 */
public class NoSlowModule extends Module {
    private static NoSlowModule instance;

    public NoSlowModule() {
        super("NoSlow",
                "Removes the slowdown while using items (eating, blocking, bow) so movement stays fluid.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

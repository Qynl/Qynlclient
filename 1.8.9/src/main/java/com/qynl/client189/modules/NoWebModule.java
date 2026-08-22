package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * NoWeb — removes the cobweb slowdown so webs barely touch your movement.
 * The slowdown in 1.8.9 is applied purely client-side inside
 * {@code CobwebBlock.onEntityCollision} (it sets the web flag on the
 * entity); skipping that call for the local player leaves movement normal
 * while the server never sees any difference.
 */
public class NoWebModule extends Module {
    private static NoWebModule instance;

    public NoWebModule() {
        super("NoWeb", "Removes cobweb slowdown — move through webs at full speed.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

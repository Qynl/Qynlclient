package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * AntiBlind — removes the blindness (black fog) and nausea (wobble) screen
 * effects that mobs, witches and potions can force on you. Purely visual and
 * client-side: the effect still exists on the server, only the fog and wobble
 * are suppressed, so it cannot be flagged and nothing is sent.
 */
public class AntiBlindModule extends Module {
    private static AntiBlindModule instance;

    public AntiBlindModule() {
        super("AntiBlind", "Removes blindness and nausea screen effects — clear vision in fights.",
                Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

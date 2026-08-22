package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import org.lwjgl.input.Keyboard;

/**
 * NoRotate — some servers force your camera to rotate (anti-cheat checks,
 * teleport glitches, obnoxious plugins). NoRotate intercepts the server's
 * position+look correction packet and reverts the rotation to your own while
 * still applying the position, so your aim is never yanked around.
 *
 * <p>Purely client-side: the position is applied normally and only the
 * camera angle is restored, so there are no packets to flag.</p>
 */
public class NoRotateModule extends Module {
    private static NoRotateModule instance;

    public NoRotateModule() {
        super("NoRotate", "Blocks servers from forcing your camera rotation — your aim stays yours.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_F11);
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }
}

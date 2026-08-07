package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Mouse;

/**
 * KeystrokesModule for 1.8.9 — shows keys and clicks-per-second on screen.
 * Mirrors the 1.21.1 Keystrokes module.
 */
public class KeystrokesModule extends Module {
    public static final int KEY_W = 0;
    public static final int KEY_A = 1;
    public static final int KEY_S = 2;
    public static final int KEY_D = 3;
    public static final int KEY_SPACE = 4;
    public static final int MOUSE_L = 5;
    public static final int MOUSE_R = 6;
    private static final int TRACKED = 7;
    private static final int HISTORY = 8;

    private final long[][] presses = new long[TRACKED][HISTORY];
    private final boolean[] prevDown = new boolean[TRACKED];

    public KeystrokesModule() {
        super("Keystrokes", "Show your keys and clicks-per-second on screen.", Category.INFO);
    }

    @Override
    public void onTick(MinecraftClient client) {
        for (int i = 0; i < TRACKED; i++) {
            boolean down = isKeyDown(client, i);
            if (down && !prevDown[i]) {
                recordPress(i);
            }
            prevDown[i] = down;
        }
    }

    public boolean isKeyDown(MinecraftClient client, int key) {
        if (client.options == null) {
            return false;
        }
        switch (key) {
            case KEY_W: return client.options.keyForward.isPressed();
            case KEY_A: return client.options.keyLeft.isPressed();
            case KEY_S: return client.options.keyBack.isPressed();
            case KEY_D: return client.options.keyRight.isPressed();
            case KEY_SPACE: return client.options.keyJump.isPressed();
            case MOUSE_L: return Mouse.isButtonDown(0);
            case MOUSE_R: return Mouse.isButtonDown(1);
            default: return false;
        }
    }

    public int getCps(MinecraftClient client, int key) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (long t : presses[key]) {
            if (t != 0 && now - t <= 1000) {
                count++;
            }
        }
        return count;
    }

    private void recordPress(int key) {
        long now = System.currentTimeMillis();
        long[] history = presses[key];
        int oldestIndex = 0;
        for (int i = 0; i < HISTORY; i++) {
            if (history[i] == 0) {
                history[i] = now;
                return;
            }
            if (history[i] < history[oldestIndex]) {
                oldestIndex = i;
            }
        }
        history[oldestIndex] = now;
    }
}

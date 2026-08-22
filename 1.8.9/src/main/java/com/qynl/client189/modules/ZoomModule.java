package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * Zoom — hold the key to zoom in for a closer look (spyglass-style). Reduces
 * the FOV and mouse sensitivity while held, restoring both when released.
 */
public class ZoomModule extends Module {
    private static final float ZOOM_LEVEL = 2.5F;

    private float baseFov = -1.0F;
    private float baseSensitivity = -1.0F;

    public ZoomModule() {
        super("Zoom", "Hold the key to zoom in for a closer look.",
                Category.RENDER);
        bindKey(Keyboard.KEY_F7);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        boolean down = getKeyBinding() != null && getKeyBinding().isPressed();
        if (down) {
            if (baseFov < 0.0F) {
                baseFov = client.options.fov;
                baseSensitivity = client.options.sensitivity;
            }
            client.options.fov = baseFov / ZOOM_LEVEL;
            client.options.sensitivity = baseSensitivity / ZOOM_LEVEL;
        } else {
            restore(client);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        restore(client);
    }

    private void restore(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }
        if (baseFov >= 0.0F) {
            client.options.fov = baseFov;
        }
        if (baseSensitivity >= 0.0F) {
            client.options.sensitivity = baseSensitivity;
        }
        baseFov = -1.0F;
        baseSensitivity = -1.0F;
    }
}

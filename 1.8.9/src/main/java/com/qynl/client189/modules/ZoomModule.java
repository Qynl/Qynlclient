package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * Zoom — hold the key to zoom in for a closer look (spyglass-style). Reduces
 * the FOV and mouse sensitivity while held and glides smoothly back to the
 * normal FOV when released, so there is never a jarring snap. The zoom level
 * is adjustable (2×–6×).
 */
public class ZoomModule extends Module {
    private float baseFov = -1.0F;
    private float baseSensitivity = -1.0F;
    /** Animated FOV while gliding in/out. */
    private float animatedFov = -1.0F;

    public ZoomModule() {
        super("Zoom", "Hold the key to zoom in smoothly — adjustable level for long-range aiming.",
                Category.RENDER);
        bindKey(Keyboard.KEY_F7);
        addSetting(Setting.range("level", "Level", 3.0, 2, 6, 1, "x"));
        addSetting(Setting.options("smooth", "Smooth", "On", "On", "Off"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        boolean down = getKeyBinding() != null && getKeyBinding().isPressed();
        boolean smooth = "On".equals(getStringSetting("smooth"));
        if (down) {
            if (baseFov < 0.0F) {
                baseFov = client.options.fov;
                baseSensitivity = client.options.sensitivity;
                animatedFov = baseFov;
            }
            float target = baseFov / (float) getDoubleSetting("level");
            if (smooth) {
                // Ease toward the target — fast at first, then settle.
                animatedFov += (target - animatedFov) * 0.35F;
                if (Math.abs(animatedFov - target) < 0.05F) {
                    animatedFov = target;
                }
            } else {
                animatedFov = target;
            }
            client.options.fov = animatedFov;
            client.options.sensitivity = baseSensitivity / (float) getDoubleSetting("level");
        } else {
            restore(client, smooth);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        restore(client, true);
    }

    private void restore(MinecraftClient client, boolean smooth) {
        if (client == null || client.options == null || baseFov < 0.0F) {
            return;
        }
        if (smooth) {
            animatedFov += (baseFov - animatedFov) * 0.35F;
            client.options.fov = animatedFov;
            if (Math.abs(animatedFov - baseFov) < 0.05F) {
                client.options.fov = baseFov;
                finishRestore(client);
            }
        } else {
            client.options.fov = baseFov;
            finishRestore(client);
        }
    }

    /** Restores the player's original sensitivity and forgets the zoom state. */
    private void finishRestore(MinecraftClient client) {
        if (baseSensitivity >= 0.0F) {
            client.options.sensitivity = baseSensitivity;
        }
        baseFov = -1.0F;
        baseSensitivity = -1.0F;
        animatedFov = -1.0F;
    }
}

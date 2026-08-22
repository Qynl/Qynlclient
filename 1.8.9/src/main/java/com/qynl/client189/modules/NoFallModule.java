package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * NoFall — prevents fall damage silently. While the player is genuinely
 * falling past the trigger distance, outgoing movement packets are spoofed
 * as {@code onGround = true} (see
 * {@link com.qynl.client189.mixin.ClientPlayerSendMovementMixin}), so the
 * server never records the landing and no fall damage is taken. Only
 * activates during real falls, so normal jumps are left untouched.
 */
public class NoFallModule extends Module {
    private static NoFallModule instance;

    public NoFallModule() {
        super("NoFall", "Prevents fall damage — a missed jump never kills you (silent onGround spoof).",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_X);
        addSetting(Setting.range("trigger", "Trigger", 3.0, 2.0, 6.0, 0.5, "b"));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    /**
     * True when the player is mid-fall and should have movement packets
     * spoofed as grounded.
     */
    public static boolean shouldSpoof(MinecraftClient client) {
        if (instance == null || !instance.isEnabled() || client.player == null) {
            return false;
        }
        if (client.player.onGround || client.player.abilities.flying) {
            return false;
        }
        // Only during a real downward fall, past the safe fall distance.
        if (client.player.fallDistance <= instance.getDoubleSetting("trigger")) {
            return false;
        }
        return client.player.velocityY < 0.0;
    }
}

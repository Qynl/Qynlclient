package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * InvWalk — keep moving while your inventory or any menu is open. Key
 * bindings do not update while a screen is open, so the raw LWJGL key state
 * is read and written into the player's {@link Input} at the correct moment
 * via {@link com.qynl.client189.mixin.InputMixin}.
 */
public class InvWalkModule extends Module {
    private static InvWalkModule instance;

    public InvWalkModule() {
        super("InvWalk", "Keep walking while your inventory or any menu is open.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_P);
    }

    /** Called by InputMixin while a screen is open. */
    public static void apply(MinecraftClient client, Input input) {
        if (instance == null || !instance.isEnabled()) {
            return;
        }
        if (client.player == null || client.options == null || client.currentScreen == null) {
            return;
        }
        input.movementForward = isDown(client.options.keyForward) ? 1.0F
                : isDown(client.options.keyBack) ? -1.0F : 0.0F;
        input.movementSideways = isDown(client.options.keyLeft) ? 1.0F
                : isDown(client.options.keyRight) ? -1.0F : 0.0F;
        input.jumping = isDown(client.options.keyJump);
        input.sneaking = isDown(client.options.keySneak);
    }

    /** Raw physical key state (works while screens consume key events). */
    private static boolean isDown(KeyBinding binding) {
        return binding != null && binding.getCode() != 0 && Keyboard.isKeyDown(binding.getCode());
    }
}

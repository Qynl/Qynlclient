package com.qynl.client.mixin;

import com.qynl.client.module.modules.AutoStepModule;
import com.qynl.client.module.modules.InvWalkModule;
import com.qynl.client.module.modules.SafeWalkModule;
import com.qynl.client.module.modules.ToggleSneakModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies input overrides AFTER vanilla {@link Input#tick} has read the
 * keyboard. This is the only reliable moment to force sneak/jump/movement
 * flags: writing to {@code player.input} from a module tick runs at
 * END_CLIENT_TICK, which happens after the player has already moved for
 * that tick — and the next {@code Input.tick} would wipe the write anyway.
 */
@Mixin(Input.class)
public abstract class InputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void qynlclient$applyInputOverrides(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Input input = (Input) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        // ToggleSneak — sneak without holding the key.
        if (ToggleSneakModule.isActive()) {
            input.shiftKeyDown = true;
        }

        // SafeWalk — sneak at block edges so you never walk off.
        if (SafeWalkModule.shouldSneak(client)) {
            input.shiftKeyDown = true;
        }

        // AutoStep — jump when walking into a one-block ledge.
        if (AutoStepModule.shouldJump(client)) {
            input.jumping = true;
        }

        // InvWalk — keep moving while a screen is open (key bindings don't
        // update while a screen consumes keyboard input, so read GLFW raw).
        InvWalkModule.apply(client, input);
    }
}

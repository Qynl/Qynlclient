package com.qynl.client.mixin;

import com.qynl.client.QynlClient;
import com.qynl.client.module.modules.AegisModule;
import com.qynl.client.module.modules.AutoStepModule;
import com.qynl.client.module.modules.InvWalkModule;
import com.qynl.client.module.modules.SafeWalkModule;
import com.qynl.client.module.modules.StrafeAssistModule;
import com.qynl.client.module.modules.ToggleSneakModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies input overrides AFTER vanilla {@link Input#tick} has read the
 * keyboard.
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

        // InvWalk — keep moving while a screen is open.
        InvWalkModule.apply(client, input);

        // Aegis — evasion engine (projectile dodging).
        AegisModule aegis = (AegisModule) QynlClient.getInstance().getModuleManager().find("Aegis");
        if (aegis != null && aegis.isDodging()) {
            double forward = aegis.getForwardDodge();
            double strafe = aegis.getStrafeDodge();
            // Blend dodge with existing player input
            if (Math.abs(forward) > 0.1) {
                input.forwardImpulse = (float) Math.signum(forward);
            }
            if (Math.abs(strafe) > 0.1) {
                input.leftImpulse = (float) -Math.signum(strafe);
            }
        }

        // StrafeAssist — auto-strafe in combat.
        StrafeAssistModule strafe = (StrafeAssistModule) QynlClient.getInstance().getModuleManager().find("StrafeAssist");
        if (strafe != null && strafe.isEnabled()) {
            if (strafe.shouldStrafeLeft()) {
                input.leftImpulse = 1.0F;
                input.forwardImpulse = Math.max(0, input.forwardImpulse);
            } else if (strafe.shouldStrafeRight()) {
                input.leftImpulse = -1.0F;
                input.forwardImpulse = Math.max(0, input.forwardImpulse);
            }
        }
    }
}
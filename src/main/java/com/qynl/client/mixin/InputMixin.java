package com.qynl.client.mixin;	import com.qynl.client.QynlClient;
	import com.qynl.client.module.modules.AegisModule;
	import com.qynl.client.module.modules.QynlModule;
	import com.qynl.client.module.modules.StrafeAssistModule;
	import com.qynl.client.module.modules.WTapModule;
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
        }		// WTap — release forward for a tick after each hit (sprint reset).
		// Applied to the input, never to the W KeyMapping, so the player's
		// real keyboard state is untouched and forward resumes naturally.
		if (WTapModule.isTapping()) {
			input.forwardImpulse = 0;
		}

		// Qynl — Quantum Collapse dodge strafe (only while the dodge hold is
		// active; the player must already be moving, enforced by QynlModule).
		float dodge = QynlModule.dodgeStrafe();
        if (dodge != 0.0F) {
            // positive dodge = strafe right → negative leftImpulse
            input.leftImpulse = -dodge;
        }

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

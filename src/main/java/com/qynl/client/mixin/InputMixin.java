package com.qynl.client.mixin;

import com.qynl.client.QynlClient;
import com.qynl.client.module.modules.AegisModule;
import com.qynl.client.module.modules.QynlModule;
import com.qynl.client.module.modules.ScaffoldWalkModule;
import com.qynl.client.module.modules.StrafeAssistModule;
import com.qynl.client.module.modules.WTapModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies input overrides AFTER vanilla {@link KeyboardInput#tick} has read
 * the keyboard.
 *
 * <p>Must target {@code KeyboardInput}, NOT the base {@code Input} class:
 * {@code KeyboardInput} overrides {@code tick()}, so an injection into
 * {@code Input.tick()} lands in the empty base method that is never called
 * at runtime — which once made every override here silently dead code
 * (WTap, StrafeAssist, Aegis dodge, scaffold sneak).</p>
 *
 * <p>Every override runs behind its own try/catch: one broken feature must
 * never disable all of them together.</p>
 */
@Mixin(KeyboardInput.class)
public abstract class InputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void qynlclient$applyInputOverrides(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        try {
            applyOverrides();
        } catch (Throwable ignored) {
        }
    }

    private void applyOverrides() {
        Input input = (Input) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }

        // Qynl — Quantum Collapse dodge strafe (only while the dodge hold is
        // active; the player must already be moving, enforced by QynlModule).
        try {
            float dodge = QynlModule.dodgeStrafe();
            if (dodge != 0.0F) {
                // positive dodge = strafe right → negative leftImpulse
                input.leftImpulse = -dodge;
            }
        } catch (Throwable ignored) {
        }

        // Aegis — evasion engine (projectile dodging).
        try {
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
        } catch (Throwable ignored) {
        }

        // StrafeAssist — auto-strafe in combat. Full-speed strafing needs
        // forward movement, so push a forward floor — unless the player is
        // actively pressing backward (then the strafe keeps their direction).
        try {
            StrafeAssistModule strafe = (StrafeAssistModule)
                    QynlClient.getInstance().getModuleManager().find("StrafeAssist");
            if (strafe != null && strafe.isEnabled()) {
                if (strafe.shouldStrafeLeft()) {
                    input.leftImpulse = 1.0F;
                    floorForward(input);
                } else if (strafe.shouldStrafeRight()) {
                    input.leftImpulse = -1.0F;
                    floorForward(input);
                }
            }
        } catch (Throwable ignored) {
        }

        // WTap — release forward for a tick after each hit (sprint reset).
        // Applied to the input, never to the W KeyMapping, so the player's
        // real keyboard state is untouched and forward resumes naturally.
        // Runs LAST among the movement overrides so no other one (e.g. the
        // strafe forward floor) can re-push forward during a tap — that
        // interaction made WTap silently dead when StrafeAssist was also on.
        try {
            if (WTapModule.isTapping()) {
                input.forwardImpulse = 0;
            }
        } catch (Throwable ignored) {
        }

        // ScaffoldWalk — ninja auto-sneak while bridging. Applied AFTER the
        // real keyboard was read, so the bridge sneak can never be cancelled
        // by Input.tick mid-tick; when the module stops forcing, the player's
        // own shift key is untouched.
        try {
            if (ScaffoldWalkModule.sneakForced()) {
                input.shiftKeyDown = true;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Keeps sprint-speed forward during a strafe without overriding a backpedal. */
    private static void floorForward(Input input) {
        if (input.forwardImpulse >= 0.0F) {
            input.forwardImpulse = Math.max(0.8F, input.forwardImpulse);
        }
    }
}

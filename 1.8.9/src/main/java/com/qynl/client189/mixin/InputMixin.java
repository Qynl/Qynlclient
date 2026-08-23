package com.qynl.client189.mixin;

import com.qynl.client189.modules.AegisModule;
import com.qynl.client189.modules.QynlModule;
import com.qynl.client189.modules.WTapModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Input overrides for 1.8.9 — applied AFTER vanilla {@link Input#tick} has
 * read the keyboard, which is the only reliable moment to force movement
 * flags: the values written here are consumed by the player's movement later
 * in the same tick.
 *
 * <p>Currently handles the WTap forward-release: for the tick after a hit,
 * the forward input is dropped so the sprint reset the server sees matches
 * the movement a real W-tap produces.</p>
 */
@Mixin(Input.class)
public abstract class InputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void qynlclient189$applyInputOverrides(CallbackInfo ci) {
        Input input = (Input) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        if (input != client.player.input) {
            return;
        }

        // ── WTap — release forward for a tick after each hit ──
        if (WTapModule.isTapping()) {
            input.movementForward = 0.0F;
        }

        // ── Aegis — Evasion Engine: the decisive single-tick dodge away
        //    from an inbound projectile. Takes priority over the Qynl
        //    collapse strafe — a dodge out of an arrow's path must be clean.
        float aegisDodge = AegisModule.dodgeStrafe();
        if (aegisDodge != 0.0F) {
            input.movementSideways = aegisDodge;
            if (AegisModule.wantsJump()) {
                input.jumping = true;
            }
        } else {
            // ── Qynl — Quantum Collapse: strafe away from the attacker
            //    during the 1–2 tick dodge window so the collapse reads as a
            //    sidestep. The value ramps (0.5 → 1.0) so the flushed motion
            //    stays a smooth lateral step, not an abrupt side-teleport.
            float qynlDodge = QynlModule.dodgeStrafe();
            if (qynlDodge != 0.0F) {
                input.movementSideways = qynlDodge;
            }
        }
    }
}

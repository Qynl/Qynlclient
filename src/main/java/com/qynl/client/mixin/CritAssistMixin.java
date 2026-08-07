package com.qynl.client.mixin;

import com.qynl.client.module.modules.CritAssistModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent-crit packet hook for 1.21.1.
 *
 * <p>Critical hits happen when the server believes the player is airborne
 * (onGround = false) at the moment of the attack. This mixin spoofs
 * onGround = false in every movement packet while CritAssist is active,
 * so every swing lands as a critical strike — no jumping required.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class CritAssistMixin {

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void qynlclient$critAssistHead(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (CritAssistModule.shouldSpoof(self)) {
            CritAssistModule.captureGround(self.onGround());
            self.setOnGround(false);
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void qynlclient$critAssistTail(CallbackInfo ci) {
        if (CritAssistModule.hasCapturedGround()) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            self.setOnGround(CritAssistModule.releaseGround());
        }
    }
}

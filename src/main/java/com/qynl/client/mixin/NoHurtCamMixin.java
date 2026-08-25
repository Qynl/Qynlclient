package com.qynl.client.mixin;

import com.qynl.client.module.modules.NoHurtCamModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the damage camera tilt when NoHurtCam is active.
 */
@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void qynlclient$noHurtCam(CallbackInfo ci) {
        if (NoHurtCamModule.isActive()) {
            ci.cancel();
        }
    }
}
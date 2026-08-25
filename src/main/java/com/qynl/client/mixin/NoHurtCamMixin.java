package com.qynl.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qynl.client.module.modules.NoHurtCamModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes the damage camera tilt when NoHurtCam is active.
 *
 * <p>1.21.1 signature: {@code GameRenderer.bobHurt(PoseStack, float)} —
 * the handler must declare the target's arguments or Mixin fails to apply
 * and the game crashes during initialization.</p>
 */
@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {
    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void qynlclient$noHurtCam(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoHurtCamModule.isActive()) {
            ci.cancel();
        }
    }
}
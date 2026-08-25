package com.qynl.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qynl.client.module.modules.NoViewBobModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes walking view bobbing when NoViewBob is active.
 *
 * <p>1.21.1 signature: {@code GameRenderer.bobView(PoseStack, float)} —
 * the handler must declare the target's arguments or Mixin fails to apply
 * and the game crashes during initialization.</p>
 */
@Mixin(GameRenderer.class)
public abstract class NoViewBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void qynlclient$noViewBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (NoViewBobModule.isActive()) {
            ci.cancel();
        }
    }
}
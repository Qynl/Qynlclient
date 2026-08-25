package com.qynl.client.mixin;

import com.qynl.client.module.modules.NoViewBobModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Removes walking view bobbing when NoViewBob is active.
 */
@Mixin(GameRenderer.class)
public abstract class NoViewBobMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void qynlclient$noViewBob(CallbackInfo ci) {
        if (NoViewBobModule.isActive()) {
            ci.cancel();
        }
    }
}
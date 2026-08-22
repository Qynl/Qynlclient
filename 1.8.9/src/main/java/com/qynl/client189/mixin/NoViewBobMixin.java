package com.qynl.client189.mixin;

import com.qynl.client189.modules.NoViewBobModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only the visual walking bob animation. */
@Mixin(GameRenderer.class)
public abstract class NoViewBobMixin {
    @Inject(method = "bobView(F)V", at = @At("HEAD"), cancellable = true)
    private void qynlclient189$disableViewBob(float tickDelta, CallbackInfo ci) {
        if (NoViewBobModule.isActive()) {
            ci.cancel();
        }
    }
}

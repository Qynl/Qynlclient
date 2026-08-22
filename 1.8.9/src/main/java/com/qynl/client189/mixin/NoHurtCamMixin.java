package com.qynl.client189.mixin;

import com.qynl.client189.modules.NoHurtCamModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only the visual damage tilt applied by GameRenderer. */
@Mixin(GameRenderer.class)
public abstract class NoHurtCamMixin {
    @Inject(method = "bobViewWhenHurt(F)V", at = @At("HEAD"), cancellable = true)
    private void qynlclient189$disableHurtCamera(float tickDelta, CallbackInfo ci) {
        if (NoHurtCamModule.isActive()) {
            ci.cancel();
        }
    }
}

package com.qynl.client189.mixin;

import com.qynl.client189.modules.ChronostasisModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Camera hook for {@link ChronostasisModule} — the "mouse decoupling" half.
 *
 * <p>{@code GameRenderer.setupCamera(float, int)} runs after the mouse look
 * has already been applied to the player for this frame and right before the
 * camera is oriented from the player's rotation. We use it as a clean window:
 * the player entity holds the <i>real</i> rotation (the one that will be sent
 * to the server in the next movement packet), so we feed the camera a
 * smoothed pan toward it and restore the real rotation as soon as the camera
 * is done. On screen the turn plays out slowly; the server sees real-time
 * aim.</p>
 */
@Mixin(GameRenderer.class)
public abstract class ChronoCameraMixin {

    @Inject(method = "setupCamera", at = @At("HEAD"))
    private void qynl189$chronoCameraHead(float tickDelta, int dimension, CallbackInfo ci) {
        ChronostasisModule.beginCamera();
    }

    @Inject(method = "setupCamera", at = @At("RETURN"))
    private void qynl189$chronoCameraReturn(float tickDelta, int dimension, CallbackInfo ci) {
        ChronostasisModule.endCamera();
    }
}

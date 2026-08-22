package com.qynl.client189.mixin;

import com.qynl.client189.modules.QuantumSuperpositionModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World-space render hook for {@link QuantumSuperpositionModule}.
 *
 * <p>Injects at the tail of {@code GameRenderer.renderWorld(float, long)} —
 * the point where the world, entities and particles have already been drawn
 * and the camera transform is still active. That is the only place where a
 * client-side overlay can be drawn in true world coordinates (camera-relative
 * translation works because the matrix stack is still set up for the world
 * pass).</p>
 *
 * <p>The module then draws the "Quantum Echo": the fading position trail, the
 * cyan ghost box at Vector A (where the server believes the player is), the
 * magenta ghost box at Vector C (where the player is about to be), the B→C
 * predictive beam and the red pulsing anchor ring during lag spikes.</p>
 */
@Mixin(GameRenderer.class)
public abstract class QuantumRenderMixin {

    @Inject(method = "renderWorld(FJ)V", at = @At("TAIL"))
    private void qynlclient189$quantumEcho(float partialTicks, long nanoTime, CallbackInfo ci) {
        QuantumSuperpositionModule.renderEcho(partialTicks);
    }
}

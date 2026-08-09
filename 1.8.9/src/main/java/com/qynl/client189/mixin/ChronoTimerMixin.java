package com.qynl.client189.mixin;

import com.qynl.client189.modules.ChronostasisModule;
import net.minecraft.client.render.ClientTickTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame-splitting hook for {@link ChronostasisModule}.
 *
 * <p>{@code ClientTickTracker} is the 1.8.9 yarn name for Minecraft's
 * {@code Timer}. Its {@code tick()} method computes the render interpolation
 * factor and stores it in the public {@code tickDelta} field (0..1, growing
 * between server ticks). It also decides how many game ticks run this frame
 * ({@code ticksThisFrame}).</p>
 *
 * <p>We only re-scale {@code tickDelta} after {@code tick()} has finished —
 * {@code ticksThisFrame} is left untouched, so the game logic and the network
 * tick keep running at exactly 100% speed. The renderer interpolates entity
 * and camera positions from {@code tickDelta}, so scaling it down is what
 * produces the slow-motion view without the server ever noticing.</p>
 */
@Mixin(ClientTickTracker.class)
public abstract class ChronoTimerMixin {

    @Shadow
    public float tickDelta;

    @Inject(method = "tick", at = @At("RETURN"))
    private void qynl189$chronoPartialTicks(CallbackInfo ci) {
        float factor = ChronostasisModule.currentFactor();
        if (factor == 1.0F) return;
        this.tickDelta = Math.min(1.6F, this.tickDelta * factor);
    }
}

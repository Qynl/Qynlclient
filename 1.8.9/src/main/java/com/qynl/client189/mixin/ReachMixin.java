package com.qynl.client189.mixin;

import com.qynl.client189.modules.ReachAssistModule;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reach extension for 1.8.9.
 *
 * <p>In 1.8.9, reach distance comes from
 * {@code ClientPlayerInteractionManager.getReachDistance()} — not from
 * {@code PlayerEntity}. This mixin adds the ReachAssist bonus to the
 * value returned by the interaction manager, extending both block and
 * entity reach.</p>
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ReachMixin {

    @Inject(method = "getReachDistance", at = @At("RETURN"), cancellable = true)
    private void qynlclient189$extendReach(CallbackInfoReturnable<Float> cir) {
        if (ReachAssistModule.isActive()) {
            cir.setReturnValue(cir.getReturnValue() + (float) ReachAssistModule.currentBonus());
        }
    }
}

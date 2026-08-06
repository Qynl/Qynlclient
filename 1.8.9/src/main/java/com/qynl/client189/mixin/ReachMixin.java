package com.qynl.client189.mixin;

import com.qynl.client189.modules.ReachAssistModule;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class ReachMixin {
    @Inject(method = "getReachDistance", at = @At("RETURN"), cancellable = true)
    private void qynlclient189$extendReach(CallbackInfoReturnable<Double> cir) {
        if (ReachAssistModule.isActive()) {
            cir.setReturnValue(cir.getReturnValue() + ReachAssistModule.currentBonus());
        }
    }
}

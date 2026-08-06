package com.qynl.client.mixin;

import com.qynl.client.module.modules.ReachAssistModule;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives ReachAssist its effect: when the module is on, the player can
 * interact with entities and blocks a little further away.
 */
@Mixin(Player.class)
public abstract class PlayerReachMixin {
	@Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
	private void qynlclient$extendEntityReach(CallbackInfoReturnable<Double> cir) {
		if (ReachAssistModule.isActive()) {
			cir.setReturnValue(cir.getReturnValue() + ReachAssistModule.currentBonus());
		}
	}

	@Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
	private void qynlclient$extendBlockReach(CallbackInfoReturnable<Double> cir) {
		if (ReachAssistModule.isActive()) {
			cir.setReturnValue(cir.getReturnValue() + ReachAssistModule.currentBonus());
		}
	}
}

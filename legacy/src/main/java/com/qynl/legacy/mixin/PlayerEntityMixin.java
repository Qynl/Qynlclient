package com.qynl.legacy.mixin;

import com.qynl.legacy.QynlLegacyClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dampens knockback (velocity) for the local player.
 * Gated behind the VelocityAssist module being enabled.
 */
@Mixin(Entity.class)
public abstract class PlayerEntityMixin {

	/** VelocityAssist: reduce incoming knockback by 60 % horizontally, 30 % vertically. */
	@Inject(method = "setVelocity", at = @At("HEAD"), cancellable = true)
	private void dampenVelocity(double x, double y, double z, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		// Only affect the local player.
		if (self != net.minecraft.client.MinecraftClient.getInstance().player) {
			return;
		}
		QynlLegacyClient client = QynlLegacyClient.getInstance();
		if (client != null && client.getModuleManager().isEnabled("VelocityAssist")) {
			// Soften knockback instead of fully blocking it — looks natural.
			self.setVelocity(x * 0.4, y * 0.7, z * 0.4);
			ci.cancel();
		}
	}
}

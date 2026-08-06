package com.qynl.client.mixin;

import com.qynl.client.QynlClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FastPlace hook — while FastPlaceAssist is active, item use cooldown
 * is clamped to a low value so the behinderten can place blocks quickly
 * without the normal 4-tick delay.
 *
 * <p>The cooldown is not set to zero every tick (that looks robotic).
 * Instead it respects the user-chosen speed setting.</p>
 */
@Mixin(Minecraft.class)
public abstract class FastPlaceMixin {
	@Shadow
	private int rightClickDelay;

	@Inject(method = "tick", at = @At("HEAD"))
	private void qynlclient$fastPlaceTick(CallbackInfo ci) {
		QynlClient qynl = QynlClient.getInstance();
		if (qynl == null) return;
		if (!qynl.getModuleManager().isEnabled("FastPlaceAssist")) return;

		String speed = qynl.getModuleManager()
				.find("FastPlaceAssist")
				.getStringSetting("speed");

		int maxDelay;
		if ("Instant".equals(speed)) {
			maxDelay = 0;
		} else if ("Slow".equals(speed)) {
			maxDelay = 2;
		} else {
			maxDelay = 1; // Fast
		}

		if (rightClickDelay > maxDelay) {
			rightClickDelay = maxDelay;
		}
	}
}

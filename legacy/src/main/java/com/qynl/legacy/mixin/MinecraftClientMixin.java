package com.qynl.legacy.mixin;

import com.qynl.legacy.QynlLegacyClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forwards key-press events to the ModuleManager so keys can toggle modules. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
	@Inject(method = "handleInputEvents", at = @At("TAIL"))
	private void onHandleInput(CallbackInfo ci) {
		// key forwarding is handled inside the main class via a manual check
	}
}

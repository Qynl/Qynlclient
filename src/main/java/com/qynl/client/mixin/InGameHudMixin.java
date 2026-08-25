package com.qynl.client.mixin;

import com.qynl.client.QynlClient;
import com.qynl.client.module.modules.StreamerModeModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
	@Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
	private void qynlclient$renderHud(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		// StreamerMode check — if active, render NOTHING on the HUD
		if (StreamerModeModule.shouldHide("any")) return;

		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.level != null && client.screen == null) {
			QynlClient.getInstance().getHudRenderer().render(guiGraphics, client,
					deltaTracker.getGameTimeDeltaPartialTick(false));
		}
	}
}

package com.qynl.client.mixin;

import com.qynl.client.module.modules.VelocityAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives VelocityAssist its effect: knockback (and similar hits) sent to the
 * local player are dampened by a percentage instead of being fully blocked.
 */
@Mixin(ClientPacketListener.class)
public abstract class VelocityMixin {
	@Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
	private void qynlclient$dampKnockback(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.player.getId() != packet.getId()) {
			return;
		}
		VelocityAssistModule module = VelocityAssistModule.getInstance();
		if (module == null || !module.isActive()) {
			return;
		}
		if (!VelocityAssistModule.rollChance()) {
			return; // this hit takes full knockback — human variance
		}
		double x = packet.getXa() * module.horizontalFactor();
		double y = packet.getYa() * module.verticalFactor();
		double z = packet.getZa() * module.horizontalFactor();
		client.player.setDeltaMovement(x, y, z);
		VelocityAssistModule.markMixinDampened();
		ci.cancel();
	}
}

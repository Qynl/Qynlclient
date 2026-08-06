package com.qynl.client.mixin;

import com.qynl.client.util.SilentAim;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-mode aim (silent aim) hook.
 *
 * <p>While {@link com.qynl.client.util.SilentAim} is armed, the movement
 * packet that is built inside {@code LocalPlayer.sendPosition()} carries the
 * silently-aimed rotation — so the server resolves attacks as if the player
 * were looking at the target — while the camera is restored to the player's
 * own view immediately afterwards.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSendPositionMixin {
	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void qynlclient$silentAimHead(CallbackInfo ci) {
		if (SilentAim.isArmed()) {
			LocalPlayer self = (LocalPlayer) (Object) this;
			self.setYRot(SilentAim.getSilentYaw());
			self.setXRot(SilentAim.getSilentPitch());
		}
	}

	@Inject(method = "sendPosition", at = @At("TAIL"))
	private void qynlclient$silentAimTail(CallbackInfo ci) {
		if (SilentAim.isArmed()) {
			LocalPlayer self = (LocalPlayer) (Object) this;
			self.setYRot(SilentAim.getVisualYaw());
			self.setXRot(SilentAim.getVisualPitch());
		}
	}
}

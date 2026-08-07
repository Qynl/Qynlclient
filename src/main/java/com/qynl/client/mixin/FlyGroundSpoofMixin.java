package com.qynl.client.mixin;

import com.qynl.client.module.modules.FlyAssistModule;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ground-spoofing hook for silent FlyAssist.
 *
 * <p>When FlyAssist is in Silent mode with ground-spoofing enabled, this
 * mixin rewrites every outgoing {@link ServerboundMovePlayerPacket} so
 * the server sees {@code onGround = true}.  To the server the player
 * appears to be walking/jumping normally, never flying — which is how
 * simple anti-cheat checks are bypassed.</p>
 *
 * <p>This hooks {@link Connection#send(Packet, PacketSendListener)} because
 * that is the single choke-point all packets pass through on 1.21.1.
 * Once the packet is modified, the connection sends the modified version
 * without the player ever seeing a flicker.</p>
 */
@Mixin(Connection.class)
public abstract class FlyGroundSpoofMixin {

	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
			at = @At("HEAD"),
			cancellable = true)
	private void qynlclient$spoofGround(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
		if (!FlyAssistModule.shouldSpoofGround()) {
			return;
		}
		if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) {
			return;
		}

		// If onGround is already true, nothing to do — skip the overhead
		// of creating a replacement packet.
		if (movePacket.isOnGround()) {
			return;
		}

		// Create a replacement packet with onGround = true.
		// The original move packet stores all position/rotation data;
		// we construct an identical packet whose only difference is the
		// ground flag.  Connection.send() is cancelled for the original
		// and re-invoked with the replacement.
		Connection self = (Connection) (Object) this;
		ci.cancel();

		double x = movePacket.getX(0);
		double y = movePacket.getY(0);
		double z = movePacket.getZ(0);
		float yRot = movePacket.getYRot(0);
		float xRot = movePacket.getXRot(0);

		ServerboundMovePlayerPacket spoofed = new ServerboundMovePlayerPacket.PosRot(
				x, y, z, yRot, xRot, true);

		self.send(spoofed, listener);
	}
}

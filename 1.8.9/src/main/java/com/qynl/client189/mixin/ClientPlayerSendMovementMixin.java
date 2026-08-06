package com.qynl.client189.mixin;

import com.qynl.client189.SilentAim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent-aim packet hook for 1.8.9.
 * <p>
 * Intercepts outgoing {@link PlayerMoveC2SPacket} packets and, while
 * {@link SilentAim} is armed, replaces the yaw/pitch with the silently-aimed
 * rotation. The player's camera stays exactly where they are looking — only
 * the server sees the corrected aim.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayerSendMovementMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void qynlclient189$silentAimSendPacket(Packet packet, CallbackInfo ci) {
        if (!SilentAim.isArmed()) return;
        if (!(packet instanceof PlayerMoveC2SPacket)) return;

        PlayerMoveC2SPacket move = (PlayerMoveC2SPacket) packet;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        SilentAim.captureVisual(client.player.yaw, client.player.pitch);

        ((PlayerMoveC2SPacketAccessor) move).setYaw(SilentAim.getSilentYaw());
        ((PlayerMoveC2SPacketAccessor) move).setPitch(SilentAim.getSilentPitch());

        // Restore the visual rotation right after the packet is built
        client.player.yaw = SilentAim.getVisualYaw();
        client.player.pitch = SilentAim.getVisualPitch();
    }
}

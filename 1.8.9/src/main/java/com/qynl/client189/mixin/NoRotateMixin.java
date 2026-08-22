package com.qynl.client189.mixin;

import com.qynl.client189.modules.NoRotateModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NoRotateMixin — the packet side of {@link NoRotateModule}.
 *
 * <p>When the server sends a position+look correction
 * ({@code PlayerPositionLookS2CPacket}) the player's camera is rotated to
 * the server's angles. This mixin captures the local yaw/pitch before the
 * handler runs and restores them afterwards, so position corrections are
 * applied but the camera never gets yanked. Only the rotation is restored —
 * the position update is left untouched, keeping the movement fully legit.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class NoRotateMixin {

    private float qynl189$savedYaw;
    private float qynl189$savedPitch;
    private boolean qynl189$armed = false;

    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void qynl189$saveRotation(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !NoRotateModule.isActive()) {
            qynl189$armed = false;
            return;
        }
        qynl189$savedYaw = client.player.yaw;
        qynl189$savedPitch = client.player.pitch;
        qynl189$armed = true;
    }

    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    private void qynl189$restoreRotation(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        if (!qynl189$armed) {
            return;
        }
        qynl189$armed = false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.yaw = qynl189$savedYaw;
            client.player.pitch = qynl189$savedPitch;
        }
    }
}

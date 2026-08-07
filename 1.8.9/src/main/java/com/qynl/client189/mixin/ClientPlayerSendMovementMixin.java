package com.qynl.client189.mixin;

import com.qynl.client189.SilentAim;
import com.qynl.client189.modules.CritAssistModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet hook for 1.8.9 — silent aim rotations + silent crit onGround spoofing.
 *
 * <p>Intercepts outgoing {@link PlayerMoveC2SPacket} packets and:</p>
 * <ul>
 *   <li>Replaces yaw/pitch for silent aim when {@link SilentAim} is armed.</li>
 *   <li>Spoofs {@code onGround = false} for silent crits when
 *       {@link CritAssistModule} is active, so every hit lands as a crit
 *       without needing to jump.</li>
 * </ul>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayerSendMovementMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void qynlclient189$silentAimSendPacket(Packet packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean isMovePacket = packet instanceof PlayerMoveC2SPacket;

        // ── Silent aim ───────────────────────────────────────
        if (SilentAim.isArmed() && isMovePacket) {
            PlayerMoveC2SPacket move = (PlayerMoveC2SPacket) packet;

            SilentAim.captureVisual(client.player.yaw, client.player.pitch);

            ((PlayerMoveC2SPacketAccessor) move).setYaw(SilentAim.getSilentYaw());
            ((PlayerMoveC2SPacketAccessor) move).setPitch(SilentAim.getSilentPitch());

            client.player.yaw = SilentAim.getVisualYaw();
            client.player.pitch = SilentAim.getVisualPitch();
        }

        // ── Silent crits ─────────────────────────────────────
        if (isMovePacket && CritAssistModule.shouldSpoof(client)) {
            PlayerMoveC2SPacketAccessor move = (PlayerMoveC2SPacketAccessor) packet;
            CritAssistModule.captureOriginalGround(move.getOnGround());
            move.setOnGround(false);
        }
    }
}

package com.qynl.client189.mixin;

import com.qynl.client189.SilentAim;
import com.qynl.client189.modules.ReachModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet hook for 1.8.9 — silent aim rotations + the Reach pack-choke.
 *
 * <p>Intercepts outgoing {@link PlayerMoveC2SPacket} packets and:</p>
 * <ul>
 *   <li>Replaces yaw/pitch for silent aim when {@link SilentAim} is armed.</li>
 *   <li>Holds movement packets while {@link ReachModule}'s silent pack-choke
 *       is active, then flushes them together so the server resolves your
 *       position further ahead.</li>
 * </ul>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayerSendMovementMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void qynlclient189$silentAimSendPacket(Packet packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        boolean isMovePacket = packet instanceof PlayerMoveC2SPacket;

        // ── Reach silent pack-choke: while the choke is armed, hold
        //    movement packets back for 1–2 ticks and flush them together —
        //    the server resolves your position further ahead than opponents
        //    see, buying effective reach without an impossible reach value.
        if (isMovePacket && ReachModule.shouldHoldPacket()) {
            ReachModule.buffer(packet);
            ci.cancel();
            return;
        }

        // ── Silent aim (one-shot) ────────────────────────────
        if (SilentAim.isArmed() && isMovePacket) {
            PlayerMoveC2SPacket move = (PlayerMoveC2SPacket) packet;

            // Scaffold pre-captures the real camera rotation before setting
            // the player's yaw/pitch to the spoof for one tick; AimAssist
            // never touches the camera, so capturing here is the real value.
            if (!SilentAim.hasCapturedVisual()) {
                SilentAim.captureVisual(client.player.yaw, client.player.pitch);
            }

            ((PlayerMoveC2SPacketAccessor) move).setYaw(SilentAim.getSilentYaw());
            ((PlayerMoveC2SPacketAccessor) move).setPitch(SilentAim.getSilentPitch());

            client.player.yaw = SilentAim.getVisualYaw();
            client.player.pitch = SilentAim.getVisualPitch();

            // One-shot: modules re-arm per tick as needed.
            SilentAim.clear();
        }
    }
}

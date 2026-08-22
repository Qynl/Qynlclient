package com.qynl.client189.mixin;

import com.qynl.client189.modules.QuantumSuperpositionModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet-side hook for {@link QuantumSuperpositionModule}.
 *
 * <p>Two jobs:</p>
 * <ul>
 *   <li><b>Compensated interaction.</b> On {@code sendPacket}, when an
 *       interact/attack packet is about to go out, the module flushes the
 *       player's true current position first (see
 *       {@link QuantumSuperpositionModule#onSendPacket}). Vanilla only sends
 *       movement packets at the end of the tick, so a mid-tick click would
 *       otherwise let the server check reach against a stale position.</li>
 *   <li><b>Server reconciliation.</b> When the server applies a position
 *       correction ({@code PlayerPositionLookS2CPacket}), the module re-bases
 *       its latency anchor on the corrected, authoritative position and
 *       clears any pending lag-spike state.</li>
 * </ul>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class QuantumNetworkMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void qynlclient189$quantumSend(Packet packet, CallbackInfo ci) {
        QuantumSuperpositionModule.onSendPacket(packet);
    }

    @Inject(method = "onPlayerPositionLook", at = @At("RETURN"))
    private void qynlclient189$quantumReconcile(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        QuantumSuperpositionModule.onPositionCorrection(MinecraftClient.getInstance());
    }
}

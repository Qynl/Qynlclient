package com.qynl.client189.mixin;

import com.qynl.client189.PingTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeepAliveMixin — feeds {@link PhantomModule}'s real ping tracker.
 *
 * <p>The tab list latency most servers report is fake or stale (1 ms, 0 ms),
 * so the client measures the actual round trip from the keep-alive stream:
 * the client responds to every server keep-alive, and the gap between that
 * response and the next server keep-alive minus the server's own interval is
 * the RTT. Both directions are timestamped here; the math lives in
 * {@link PingTracker}.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class KeepAliveMixin {

    @Inject(method = "onKeepAlive", at = @At("HEAD"))
    private void qynl$pingServerKeepAlive(KeepAliveS2CPacket packet, CallbackInfo ci) {
        PingTracker.onKeepAliveReceived();
    }

    @Inject(method = "sendPacket", at = @At("HEAD"))
    private void qynl$pingClientKeepAlive(Packet packet, CallbackInfo ci) {
        if (packet instanceof KeepAliveC2SPacket) {
            PingTracker.onKeepAliveSent();
        }
    }
}

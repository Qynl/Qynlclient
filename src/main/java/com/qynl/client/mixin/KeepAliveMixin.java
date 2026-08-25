package com.qynl.client.mixin;

import com.qynl.client.util.PingTracker;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * KeepAliveMixin — feeds the client's real ping tracker ({@link PingTracker}),
 * used by the Qynl combat engine.
 *
 * <p>The tab list latency most servers report is fake or stale (1 ms, 0 ms),
 * so the client measures the actual round trip from the keep-alive stream:
 * the client responds to every server keep-alive, and the gap between that
 * response and the next server keep-alive minus the server's own interval is
 * the RTT. The receive side is timestamped here; the send side is timestamped
 * in {@link BlinkMixin} (which already intercepts every outgoing packet).</p>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class KeepAliveMixin {

    @Inject(method = "handleKeepAlive", at = @At("HEAD"))
    private void qynl$pingServerKeepAlive(ClientboundKeepAlivePacket packet, CallbackInfo ci) {
        PingTracker.onKeepAliveReceived();
    }
}

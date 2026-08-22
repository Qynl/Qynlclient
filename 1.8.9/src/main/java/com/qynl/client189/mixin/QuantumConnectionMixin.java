package com.qynl.client189.mixin;

import com.qynl.client189.modules.QuantumSuperpositionModule;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Netty inbound hook for {@link QuantumSuperpositionModule}.
 *
 * <p>{@code ClientConnection.channelRead0} is the single choke point every
 * inbound packet passes through (the Netty {@code SimpleChannelInboundHandler}
 * callback). We timestamp each decoded packet here so the module's jitter and
 * lag-spike model can measure inbound silence — the input for the defensive
 * position stabilization (anchor re-send).</p>
 *
 * <p>The method name is explicit to disambiguate from the inherited
 * {@code channelRead0(ChannelHandlerContext, Object)} bridge.</p>
 */
@Mixin(ClientConnection.class)
public abstract class QuantumConnectionMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",
            at = @At("HEAD"))
    private void qynlclient189$quantumInbound(ChannelHandlerContext ctx, Packet packet, CallbackInfo ci) {
        QuantumSuperpositionModule.onPacketReceived();
    }
}

package com.qynl.client.mixin;

import com.qynl.client.module.modules.BlinkModule;
import com.qynl.client.module.modules.QynlModule;
import com.qynl.client.module.modules.ReachAssistModule;
import com.qynl.client.util.PingTracker;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Packet choke-point: everything leaving the client passes through
 * {@code Connection.send}. This mixin applies the three packet-hold engines
 * (Qynl Quantum Collapse, Reach Silent Pack-Choke, Blink) and feeds the
 * keep-alive ping tracker.
 *
 * <p>Deferral order (never two holds at once): Qynl defers to Blink and the
 * Reach choke; the Reach choke defers to Blink; Blink holds whatever remains.</p>
 *
 * <p>Note: this mixin deliberately holds no state of its own. The Blink hold
 * buffer lives in {@link BlinkModule} (a plain class) — Mixin rejects any
 * non-private static method on a mixin class at apply time, so helpers that
 * must be called from normal classes never belong inside a mixin.</p>
 */
@Mixin(Connection.class)
public abstract class BlinkMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void qynlclient$blinkHold(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        // Keep-alive responses go out through this funnel — timestamp for PingTracker.
        if (packet instanceof ServerboundKeepAlivePacket) {
            PingTracker.onKeepAliveSent();
        }

        // 1. Qynl Quantum Collapse — one-shot dodge hold.
        if (QynlModule.shouldHoldPacket()) {
            QynlModule.buffer(packet);
            ci.cancel();
            return;
        }

        // 2. Reach Silent Pack-Choke — 1-2 tick combat holds.
        if (ReachAssistModule.shouldHoldPacket()) {
            ReachAssistModule.buffer(packet);
            ci.cancel();
            return;
        }

        // 3. Blink — movement-packet burst hold.
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) return;

        if (BlinkModule.shouldHold()) {
            BlinkModule.buffer(movePacket);
            ci.cancel();
        }
    }
}

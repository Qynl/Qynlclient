package com.qynl.client189.mixin;

import com.qynl.client189.modules.BlinkModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BlinkMixin — the packet side of {@link BlinkModule}.
 *
 * <p>Intercepts {@code ClientPlayNetworkHandler.sendPacket} (the single funnel
 * every outgoing packet passes through, already used by the silent-aim / crit
 * / fly hooks). When {@link BlinkModule} decides a packet should be held, the
 * send is cancelled and the packet is buffered instead; {@link #flush} replays
 * the buffer through the regular public {@code sendPacket} path, so the other
 * send hooks still apply to flushed packets exactly as if they had gone out
 * normally. Keep-alives and non-movement packets are never intercepted because
 * they never reach {@code shouldHold} as movement packets.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class BlinkMixin {

    /** Guards against re-buffering while the module flushes its own queue. */
    private static boolean flushing = false;

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void qynl189$blinkHold(Packet packet, CallbackInfo ci) {
        if (flushing) {
            return;
        }
        if (BlinkModule.shouldHold(packet)) {
            BlinkModule.hold(packet);
            ci.cancel();
        }
    }

    /**
     * Sends every buffered packet through the real send path. The {@code
     * flushing} flag makes the injection pass them through instead of
     * re-buffering them.
     */
    public static void flush(ClientPlayNetworkHandler handler) {
        if (handler == null) {
            return;
        }
        flushing = true;
        try {
            Packet packet;
            while ((packet = BlinkModule.poll()) != null) {
                handler.sendPacket(packet);
            }
        } finally {
            flushing = false;
        }
    }
}

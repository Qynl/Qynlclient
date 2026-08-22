package com.qynl.client189.mixin;

import com.qynl.client189.modules.BacktrackModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * BacktrackMixin — the packet side of {@link BacktrackModule}.
 *
 * <p>Intercepts the three inbound entity-movement handlers on the client
 * network handler ({@code onEntityUpdate} covers the base relative-move packet
 * <i>and</i> its {@code MoveRelative}/{@code Rotate}/{@code RotateAndMoveRelative}
 * subclasses, since they all dispatch to the same handler; plus the absolute
 * {@code onEntityPosition} teleport and {@code onEntitySetHeadYaw} head look).
 * While the module is active each packet is buffered with a timestamp instead
 * of being applied; {@link #pump} replays the packets whose delay window has
 * elapsed, in arrival order, every client tick.</p>
 *
 * <p>Replay dispatches through the packet's own {@code apply} method, which
 * calls the original handler method. A {@code replaying} guard makes the
 * injection pass those replays straight through instead of re-buffering
 * them.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class BacktrackMixin {

    /** Maximum buffered packets before the oldest are dropped. */
    private static final int MAX_QUEUE = 512;
    /** If the oldest buffered packet is older than this, the queue is stale. */
    private static final long MAX_HOLD_MS = 1500;

    private static final Deque<Held> QUEUE = new ArrayDeque<>();
    private static boolean replaying = false;

    /** One buffered inbound packet with its arrival time. */
    private static final class Held {
        final long time;
        final Object packet;

        Held(long time, Object packet) {
            this.time = time;
            this.packet = packet;
        }
    }

    // ── hold: intercept, buffer and cancel ────────────────────────

    @Inject(method = "onEntityUpdate", at = @At("HEAD"), cancellable = true)
    private void qynl189$holdEntityUpdate(EntityS2CPacket packet, CallbackInfo ci) {
        if (hold(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "onEntityPosition", at = @At("HEAD"), cancellable = true)
    private void qynl189$holdEntityPosition(EntityPositionS2CPacket packet, CallbackInfo ci) {
        if (hold(packet)) {
            ci.cancel();
        }
    }

    @Inject(method = "onEntitySetHeadYaw", at = @At("HEAD"), cancellable = true)
    private void qynl189$holdEntitySetHeadYaw(EntitySetHeadYawS2CPacket packet, CallbackInfo ci) {
        if (hold(packet)) {
            ci.cancel();
        }
    }

    /** Buffers the packet; returns true when it was held (caller cancels). */
    private static boolean hold(Object packet) {
        if (replaying || !BacktrackModule.isActive()) {
            return false;
        }
        QUEUE.addLast(new Held(System.currentTimeMillis(), packet));
        while (QUEUE.size() > MAX_QUEUE) {
            QUEUE.pollFirst();
        }
        return true;
    }

    // ── pump / flush: replay buffered packets through the originals ──

    /**
     * Called every client tick from the module: replays every buffered packet
     * whose delay window has elapsed, oldest first.
     */
    public static void pump(ClientPlayNetworkHandler handler, int delayMs) {
        if (handler == null || QUEUE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - QUEUE.peekFirst().time > MAX_HOLD_MS) {
            // Stale queue (long hitch / reconnect) — drop rather than teleport.
            QUEUE.clear();
            return;
        }
        replaying = true;
        try {
            while (!QUEUE.isEmpty() && now - QUEUE.peekFirst().time >= delayMs) {
                apply(handler, QUEUE.pollFirst());
            }
        } finally {
            replaying = false;
        }
    }

    /** Instantly applies everything still buffered (module disable). */
    public static void flush(ClientPlayNetworkHandler handler) {
        if (handler == null || QUEUE.isEmpty()) {
            return;
        }
        replaying = true;
        try {
            while (!QUEUE.isEmpty()) {
                apply(handler, QUEUE.pollFirst());
            }
        } finally {
            replaying = false;
        }
    }

    /** Number of packets currently buffered (HUD display). */
    public static int bufferedCount() {
        return QUEUE.size();
    }

    /**
     * Dispatches one held packet through its own {@code apply} method. The
     * packet's {@code apply} calls the original handler method, which
     * re-enters the {@code hold} injection — the {@code replaying} guard makes
     * it pass straight through to the real logic.
     */
    private static void apply(ClientPlayNetworkHandler handler, Held held) {
        Object packet = held.packet;
        if (packet instanceof EntityPositionS2CPacket) {
            ((EntityPositionS2CPacket) packet).apply(handler);
        } else if (packet instanceof EntitySetHeadYawS2CPacket) {
            ((EntitySetHeadYawS2CPacket) packet).apply(handler);
        } else if (packet instanceof EntityS2CPacket) {
            ((EntityS2CPacket) packet).apply(handler);
        }
    }
}

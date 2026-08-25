package com.qynl.client.mixin;

import com.qynl.client.QynlClient;
import com.qynl.client.module.modules.BlinkModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Blink — holds movement packets while the module is active,
 * then releases them in a burst when it's disabled.
 *
 * <p>Targets Connection.send() — the single choke-point all packets pass through.</p>
 */
@Mixin(Connection.class)
public abstract class BlinkMixin {
    private static final List<ServerboundMovePlayerPacket> qynlclient$heldPackets = new ArrayList<>();

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"), cancellable = true)
    private void qynlclient$blinkHold(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (!(packet instanceof ServerboundMovePlayerPacket movePacket)) return;

        QynlClient qynl = QynlClient.getInstance();
        if (qynl == null) return;

        BlinkModule blink = (BlinkModule) qynl.getModuleManager().find("Blink");
        if (blink == null || !blink.isHolding()) return;

        // Hold the packet
        qynlclient$heldPackets.add(movePacket);
        ci.cancel();
    }

    /** Called when Blink releases — sends all held packets. */
    public static void releasePackets(Connection connection) {
        if (qynlclient$heldPackets.isEmpty()) return;
        for (ServerboundMovePlayerPacket pkt : qynlclient$heldPackets) {
            connection.send(pkt, null);
        }
        qynlclient$heldPackets.clear();
    }

    /** Clear held packets without sending (e.g., on disconnect). */
    public static void clearPackets() {
        qynlclient$heldPackets.clear();
    }
}
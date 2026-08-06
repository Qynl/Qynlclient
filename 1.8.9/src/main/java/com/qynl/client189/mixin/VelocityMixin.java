package com.qynl.client189.mixin;

import com.qynl.client189.modules.VelocityAssistModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityMixin {
    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void qynlclient189$dampKnockback(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.player.getEntityId() != packet.getId()) return;
        VelocityAssistModule module = VelocityAssistModule.getInstance();
        if (module == null || !module.isActive()) return;
        double hx = packet.getVelocityX() / 8000.0 * module.horizontalFactor();
        double hy = packet.getVelocityY() / 8000.0 * module.verticalFactor();
        double hz = packet.getVelocityZ() / 8000.0 * module.horizontalFactor();
        client.player.setVelocityClient(hx, hy, hz);
        ci.cancel();
    }
}

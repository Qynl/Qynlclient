package com.qynl.client.mixin;

import com.qynl.client.module.modules.EchoModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards sound packets to the Echo module for soundscape radar.
 */
@Mixin(ClientPacketListener.class)
public abstract class SoundPacketMixin {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"))
    private void qynlclient$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        EchoModule.onSoundPacket(packet);
    }
}
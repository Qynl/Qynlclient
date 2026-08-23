package com.qynl.client189.mixin;

import com.qynl.client189.modules.EchoModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlaySoundIdS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SoundEchoMixin — feeds {@link EchoModule} from the server's sound packets.
 *
 * <p>In 1.8.9 every broadcast sound arrives as a
 * {@link PlaySoundIdS2CPacket} handled by {@code onPlaySound}. This hook
 * reads the sound name and world position (read-only) and hands them to the
 * Echo renderer. No packet is modified or answered, so the server sees
 * nothing but a normal client.</p>
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class SoundEchoMixin {

    @Inject(method = "onPlaySound", at = @At("HEAD"))
    private void qynlclient189$echoOnPlaySound(PlaySoundIdS2CPacket packet, CallbackInfo ci) {
        EchoModule.onSound(packet.getSound(), packet.getX(), packet.getY(), packet.getZ(), packet.getVolume());
    }
}

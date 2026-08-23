package com.qynl.client189.mixin;

import com.qynl.client189.QynlClient189;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MinecraftMixin — drives the module tick loop from the head of
 * {@code MinecraftClient.tick}, before the world ticks and movement
 * packets are sent, so module input/packet work lands in the same tick.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void qynlclient189$onTick(CallbackInfo ci) {
        QynlClient189 instance = QynlClient189.getInstance();
        if (instance != null) {
            instance.onClientTick();
        }
    }
}

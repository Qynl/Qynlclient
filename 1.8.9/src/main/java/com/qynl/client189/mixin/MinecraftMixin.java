package com.qynl.client189.mixin;

import com.qynl.client189.QynlClient189;
import com.qynl.client189.modules.FastPlaceModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftMixin {
    @Shadow
    private int blockPlaceDelay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void qynlclient189$onTick(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        int maxDelay = FastPlaceModule.maxBlockPlaceDelay(client);
        if (maxDelay >= 0 && blockPlaceDelay > maxDelay) {
            blockPlaceDelay = maxDelay;
        }
        QynlClient189 instance = QynlClient189.getInstance();
        if (instance != null) {
            instance.onClientTick();
        }
    }
}

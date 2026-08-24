package com.qynl.client189.mixin;

import com.qynl.client189.modules.BlockHitModule;
import com.qynl.client189.modules.WTapModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WTapMixin — fires {@link WTapModule#onAttack} and
 * {@link BlockHitModule#onAttack} right before every attack (real clicks and
 * AutoClicker's, which both go through {@code MinecraftClient.doAttack}), so
 * the W-tap lands on the same tick as the hit — exactly when a real player
 * would tap it — and the BlockHit block is released at the exact moment of
 * the attack, before it executes (true post-attack blocking).
 */
@Mixin(MinecraftClient.class)
public abstract class WTapMixin {

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void qynlclient189$wtapOnAttack(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        WTapModule.onAttack(client);
        BlockHitModule.onAttack(client);
    }
}

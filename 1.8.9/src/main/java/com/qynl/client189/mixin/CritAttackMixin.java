package com.qynl.client189.mixin;

import com.qynl.client189.modules.CritAssistModule;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pre-attack crit hook for 1.8.9 (MiniJump technique).
 *
 * <p>Hooks {@code MinecraftClient.doAttack()} — the single method every
 * attack goes through (mouse click, AutoClicker and TriggerBot all call
 * it). Right before the hit is executed, {@link CritAssistModule} sends
 * the two tiny position packets (Y + 0.06 up, Y + 0.01 down, both
 * airborne) so the server grants a critical hit. The real attack then
 * runs normally.</p>
 */
@Mixin(MinecraftClient.class)
public abstract class CritAttackMixin {

    @Shadow
    private int attackCooldown;

    @Inject(method = "doAttack", at = @At("HEAD"))
    private void qynlclient189$preAttackCrit(CallbackInfo ci) {
        // Only spoof when the attack will actually be executed.
        if (attackCooldown > 0) return;
        CritAssistModule.onPreAttack(MinecraftClient.getInstance());
    }
}

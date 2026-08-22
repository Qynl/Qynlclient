package com.qynl.client189.mixin;

import com.qynl.client189.modules.AntiBlindModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AntiBlindMixin — the render side of {@link AntiBlindModule}.
 *
 * <p>Blindness (black fog) and nausea (screen wobble) are purely visual in
 * 1.8.9: the world renderer asks {@code LivingEntity.hasStatusEffect} before
 * drawing either effect. Suppressing those two effects on the client player
 * removes the visuals while the effect itself stays active server-side — no
 * packets are touched, so it is completely silent.</p>
 */
@Mixin(LivingEntity.class)
public abstract class AntiBlindMixin {

    @Inject(method = "hasStatusEffect(Lnet/minecraft/entity/effect/StatusEffect;)Z",
            at = @At("HEAD"), cancellable = true)
    private void qynl189$suppressBlindnessNausea(StatusEffect effect, CallbackInfoReturnable<Boolean> cir) {
        if (!AntiBlindModule.isActive()) {
            return;
        }
        if (!((Object) this instanceof ClientPlayerEntity)) {
            return;
        }
        if (effect == StatusEffect.BLINDNESS || effect == StatusEffect.NAUSEA) {
            cir.setReturnValue(false);
        }
    }
}

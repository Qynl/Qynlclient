package com.qynl.client.mixin;

import com.qynl.client.module.modules.WTapModule;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires on every client-side attack. This is the single reliable attack
 * signal for WTap: manual clicks, AutoClicker (Legacy included) and
 * AimAssist's silent fire all call {@code MultiPlayerGameMode.attack},
 * whereas the swing-cooldown bar never fills when Legacy clicks bypass it.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class AttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void qynlclient$onAttack(Player player, Entity target, CallbackInfo ci) {
        WTapModule.onPlayerAttack(target);
    }
}

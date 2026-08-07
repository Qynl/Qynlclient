package com.qynl.client.mixin;

import com.qynl.client.module.modules.CritAssistModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pre-attack crit hook for 1.21.1 (MiniJump technique).
 *
 * <p>Hooks {@code MultiPlayerGameMode.attack()} — the method the game calls
 * every time a real attack on an entity is executed (mouse click, or any
 * assist module that attacks). Right before the hit, {@link CritAssistModule}
 * sends the two tiny position packets (Y + 0.06 up, Y + 0.01 down, both
 * airborne) so the server grants a critical hit. The real attack then runs
 * normally.</p>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class CritAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void qynlclient$preAttackCrit(Player player, Entity target, CallbackInfo ci) {
        CritAssistModule.onPreAttack(Minecraft.getInstance());
    }
}

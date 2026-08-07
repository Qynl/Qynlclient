package com.qynl.client189.mixin;

import com.qynl.client189.modules.VelocityAssistModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Velocity dampening for 1.8.9.
 *
 * <p>Hooks {@link Entity#addVelocity(double, double, double)} — the
 * method that the network handler calls after receiving a knockback
 * packet from the server. This is more reliable than hooking the
 * packet handler directly because {@code addVelocity} has a stable
 * Yarn name across all 1.8.9 mapping builds.</p>
 *
 * <p>Only affects the local player — all other entities receive
 * normal velocity.</p>
 */
@Mixin(Entity.class)
public abstract class VelocityMixin {

    @Inject(method = "addVelocity", at = @At("HEAD"), cancellable = true)
    private void qynlclient189$dampKnockback(double x, double y, double z, CallbackInfo ci) {
        // Only apply to the local player
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if ((Object) this != client.player) return;

        VelocityAssistModule module = VelocityAssistModule.getInstance();
        if (module == null || !module.isEnabled()) return;

        // Reduce the velocity before it's applied
        double hx = x * module.horizontalFactor();
        double hy = y * module.verticalFactor();
        double hz = z * module.horizontalFactor();

        // Cancel original and apply reduced velocity via direct field access
        ci.cancel();

        // Use raw field names that compile on this Yarn build (velocityX/Y/Z)
        client.player.velocityX += hx;
        client.player.velocityY += hy;
        client.player.velocityZ += hz;

        // Remember that the mixin handled this hit so the per-tick fallback in
        // VelocityAssistModule doesn't reduce the same knockback a second time.
        VelocityAssistModule.markMixinDampened();
    }
}

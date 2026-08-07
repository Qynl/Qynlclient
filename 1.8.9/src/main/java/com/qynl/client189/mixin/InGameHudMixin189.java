package com.qynl.client189.mixin;

import com.qynl.client189.HudRenderer189;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the QynlClient 1.8.9 HUD (module list) at the tail of the
 * vanilla in-game HUD render. The HUD is skipped when StreamerMode is
 * active, keeping recordings OBS-safe.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin189 {

    @Inject(method = "render", at = @At("TAIL"))
    private void qynlclient189$renderHud(float tickDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null && client.currentScreen == null) {
            HudRenderer189.render(client);
        }
    }
}

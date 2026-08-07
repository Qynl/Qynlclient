package com.qynl.client189.mixin;

import com.qynl.client189.HudRenderer189;
import com.qynl.client189.modules.StreamerModeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the QynlClient 1.8.9 HUD (module list + info panels + keystrokes)
 * at the tail of the vanilla in-game HUD render, and handles clicks on the
 * module rows. The HUD is skipped while StreamerMode is active, keeping
 * recordings OBS-safe.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin189 {

    @Inject(method = "render", at = @At("TAIL"))
    private void qynlclient189$renderHud(float tickDelta, CallbackInfo ci) {
        // StreamerMode check — if active, render NOTHING on the HUD
        if (StreamerModeModule.shouldHide("any")) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null && client.currentScreen == null) {
            HudRenderer189.render(client);
            HudRenderer189.handleClick(client);
        }
    }
}

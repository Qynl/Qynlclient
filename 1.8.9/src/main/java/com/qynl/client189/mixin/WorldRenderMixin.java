package com.qynl.client189.mixin;

import com.qynl.client189.modules.AegisModule;
import com.qynl.client189.modules.ClutchModule;
import com.qynl.client189.modules.CriticalsModule;
import com.qynl.client189.modules.DirectorModule;
import com.qynl.client189.modules.EchoModule;
import com.qynl.client189.modules.HindsightModule;
import com.qynl.client189.modules.NameTagsModule;
import com.qynl.client189.modules.QynlModule;
import com.qynl.client189.modules.SearchModule;
import com.qynl.client189.modules.StorageESPModule;
import com.qynl.client189.modules.TracersModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World-space render hook for the Qyn-L render modules. Injects at the tail
 * of {@code GameRenderer.renderWorld(float, long)} — the point where the
 * world has been drawn and the camera transform is still active, so
 * overlays can be drawn in true world coordinates (camera-relative).
 */
@Mixin(GameRenderer.class)
public abstract class WorldRenderMixin {

    @Inject(method = "renderWorld(FJ)V", at = @At("TAIL"))
    private void qynlclient189$renderWorldOverlays(float partialTicks, long nanoTime, CallbackInfo ci) {
        SearchModule.render(partialTicks);
        StorageESPModule.render(partialTicks);
        TracersModule.render(partialTicks);
        NameTagsModule.render(partialTicks);
        QynlModule.render(partialTicks);
        HindsightModule.render(partialTicks);
        CriticalsModule.render(partialTicks);
        AegisModule.render(partialTicks);
        ClutchModule.render(partialTicks);
        EchoModule.render(partialTicks);
        DirectorModule.render(partialTicks);
    }
}

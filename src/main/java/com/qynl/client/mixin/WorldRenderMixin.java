package com.qynl.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qynl.client.module.modules.EchoModule;
import com.qynl.client.module.modules.NameTagsModule;
import com.qynl.client.module.modules.SearchModule;
import com.qynl.client.module.modules.StorageESPModule;
import com.qynl.client.module.modules.TracersModule;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders 3D ESP overlays (Search, StorageESP, Tracers, NameTags, Echo)
 * at the end of the level render pass.
 */
@Mixin(LevelRenderer.class)
public abstract class WorldRenderMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void qynlclient$renderESP(PoseStack poseStack, float partialTick, long finishNanoTime,
                                      boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                                      LightTexture lightTexture, Matrix4f projectionMatrix, Matrix4f frustumMatrix,
                                      CallbackInfo ci) {
        if (minecraft.player == null || minecraft.level == null) return;

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        Vec3 camPos = camera.getPosition();

        // Render ESP modules
        SearchModule search = SearchModule.getInstance();
        if (search != null) search.render(poseStack, bufferSource, camPos);

        StorageESPModule storage = StorageESPModule.getInstance();
        if (storage != null) storage.render(poseStack, bufferSource, camPos);

        TracersModule tracers = TracersModule.getInstance();
        if (tracers != null) tracers.render(poseStack, bufferSource, camPos);

        NameTagsModule nameTags = NameTagsModule.getInstance();
        if (nameTags != null) nameTags.render(poseStack, bufferSource, camPos);

        // Echo markers
        EchoModule echo = EchoModule.getInstance();
        if (echo != null && echo.isEnabled()) {
            for (EchoModule.EchoMarker marker : echo.getMarkers()) {
                long elapsed = System.currentTimeMillis() - marker.timestamp();
                double fadeMs = echo.getDoubleSetting("fadeMs");
                float alpha = 1.0f - Math.min(1.0f, (float) elapsed / (float) fadeMs);
                if (alpha <= 0) continue;

                Vec3 renderPos = marker.pos().subtract(camPos);
                int color = marker.color();
                int drawColor = color | ((int) (alpha * 255) << 24);

                minecraft.font.drawInBatch(
                        net.minecraft.network.chat.Component.literal("\u25cf"),
                        (float) renderPos.x - 3, (float) renderPos.y - 3,
                        drawColor,
                        true, poseStack.last().pose(), bufferSource,
                        net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                        0, 0xF000F0);
            }
        }

        bufferSource.endBatch();
    }
}
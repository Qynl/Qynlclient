package com.qynl.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qynl.client.module.modules.DirectorModule;
import com.qynl.client.module.modules.EchoModule;
import com.qynl.client.module.modules.NameTagsModule;
import com.qynl.client.module.modules.QynlModule;
import com.qynl.client.module.modules.SearchModule;
import com.qynl.client.module.modules.StorageESPModule;
import com.qynl.client.module.modules.TracersModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders 3D ESP overlays (Qynl, Director, Search, StorageESP, Tracers,
 * NameTags, Echo) at the end of the level render pass.
 *
 * <p>1.21.1 signature: {@code LevelRenderer.renderLevel(DeltaTracker, boolean,
 * Camera, GameRenderer, LightTexture, Matrix4f, Matrix4f)} — note the PoseStack
 * was removed from the method in 1.21, so the camera-rotated model matrix is
 * rebuilt here for world-space overlays.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class WorldRenderMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void qynlclient$renderESP(DeltaTracker deltaTracker, boolean renderBlockOutline,
                                      Camera camera, GameRenderer gameRenderer,
                                      LightTexture lightTexture, Matrix4f projectionMatrix,
                                      Matrix4f frustumMatrix, CallbackInfo ci) {
        if (minecraft.player == null || minecraft.level == null) return;

        // The whole ESP block is crash-isolated: a rendering exception here
        // would otherwise propagate out of renderLevel and kill the frame
        // (which looks exactly like a server kick / disconnect). No module
        // may ever take the render thread down.
        try {
            renderESP(camera, bufferSource());
        } catch (Throwable ignored) {
            // Skip this frame's overlays; never crash the game.
        }
    }

    private MultiBufferSource.BufferSource bufferSource() {
        return minecraft.renderBuffers().bufferSource();
    }

    private void renderESP(Camera camera, MultiBufferSource.BufferSource bufferSource) {
        Vec3 camPos = camera.getPosition();

        // Camera-rotated model matrix for world-space boxes / lines (the same
        // matrix vanilla builds inside renderLevel for block outlines).
        PoseStack worldStack = new PoseStack();
        worldStack.mulPose(camera.rotation());

        // Render the flagship quantum overlay (Qynl) and the Director focus ring.
        QynlModule.render(worldStack, bufferSource, camPos);
        DirectorModule.render(worldStack, bufferSource, camPos);

        // Render ESP modules
        SearchModule search = SearchModule.getInstance();
        if (search != null) search.render(worldStack, bufferSource, camPos);

        StorageESPModule storage = StorageESPModule.getInstance();
        if (storage != null) storage.render(worldStack, bufferSource, camPos);

        TracersModule tracers = TracersModule.getInstance();
        if (tracers != null) tracers.render(worldStack, bufferSource, camPos);

        // NameTags builds its own billboard transform (translate → face camera → scale),
        // so it needs an identity pose stack to start from.
        NameTagsModule nameTags = NameTagsModule.getInstance();
        if (nameTags != null) nameTags.render(new PoseStack(), bufferSource, camPos);

        // Echo markers
        EchoModule echo = EchoModule.getInstance();
        if (echo != null && echo.isEnabled()) {
            for (EchoModule.EchoMarker marker : echo.getMarkers()) {
                long elapsed = System.currentTimeMillis() - marker.timestamp();
                double fadeMs = echo.getDoubleSetting("fadeMs");
                if (fadeMs <= 0) continue;
                float alpha = 1.0f - Math.min(1.0f, (float) elapsed / (float) fadeMs);
                if (alpha <= 0) continue;

                Vec3 renderPos = marker.pos().subtract(camPos);
                if (!Double.isFinite(renderPos.x) || !Double.isFinite(renderPos.y)
                        || !Double.isFinite(renderPos.z)) continue;
                int color = marker.color();
                int drawColor = color | ((int) (alpha * 255) << 24);

                minecraft.font.drawInBatch(
                        Component.literal("\u25cf"),
                        (float) renderPos.x - 3, (float) renderPos.y - 3,
                        drawColor,
                        true, worldStack.last().pose(), bufferSource,
                        net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH,
                        0, 0xF000F0);
            }
        }

        bufferSource.endBatch();
    }
}

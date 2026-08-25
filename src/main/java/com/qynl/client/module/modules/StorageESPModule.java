package com.qynl.client.module.modules;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StorageESP — outlines all storage blocks through walls.
 */
public class StorageESPModule extends Module {
    private static StorageESPModule instance;

    private final Map<BlockPos, Long> cache = new ConcurrentHashMap<>();
    private int scanTimer = 0;
    private BlockPos lastPos = BlockPos.ZERO;
    private static final int RADIUS = 32;

    public StorageESPModule() {
        super("StorageESP", "Outlines all storage blocks through walls.",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("opacity", "Opacity", 40.0, 10, 100, 5, "%"));
    }

    public static StorageESPModule getInstance() { return instance; }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (!isEnabled() || cache.isEmpty()) return;

        int alpha = (int) (getDoubleSetting("opacity") / 100.0 * 255);
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now - e.getValue() > 10000);

        for (BlockPos pos : cache.keySet()) {
            Vec3 renderPos = Vec3.atLowerCornerOf(pos).subtract(camPos);
            AABB box = new AABB(renderPos.x, renderPos.y, renderPos.z,
                    renderPos.x + 1, renderPos.y + 1, renderPos.z + 1);
            LevelRenderer.renderLineBox(poseStack, consumer, box, 0.0f, 0.667f, 1.0f, alpha / 255f);
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        scanTimer++;
        BlockPos currentPos = client.player.blockPosition();
        if (scanTimer < 20 && currentPos.distSqr(lastPos) < 64) return;
        scanTimer = 0;
        lastPos = currentPos;

        Level level = client.level;
        cache.entrySet().removeIf(e -> e.getKey().distSqr(currentPos) > RADIUS * RADIUS);

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    BlockPos pos = currentPos.offset(x, y, z);
                    if (cache.containsKey(pos) || !level.isLoaded(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (isStorage(state)) {
                        cache.put(pos.immutable(), System.currentTimeMillis());
                    }
                }
            }
        }
    }

    private boolean isStorage(BlockState state) {
        String name = state.getBlock().getDescriptionId();
        return name.contains("chest") || name.contains("barrel") || name.contains("shulker")
                || name.contains("furnace") || name.contains("hopper") || name.contains("dispenser")
                || name.contains("dropper") || name.contains("ender_chest");
    }

    @Override
    public void onDisable() { cache.clear(); }
}
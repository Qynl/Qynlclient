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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Search — outlines configured blocks through walls.
 * Cached scan with distance-based updates.
 */
public class SearchModule extends Module {
    private static SearchModule instance;

    private final Map<BlockPos, Long> cachedBlocks = new ConcurrentHashMap<>();
    private int scanTimer = 0;
    private BlockPos lastPlayerPos = BlockPos.ZERO;

    // Block types to search for
    private static final List<String> BLOCK_TYPES = List.of(
            "Chests", "Ores", "Storage", "Spawners", "Portals", "Vaults"
    );

    public SearchModule() {
        super("Search", "Outlines chests, ores, and storage blocks through walls (cached scan).",
                Category.RENDER);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("blocks", "Search for", "Ores", BLOCK_TYPES.toArray(new String[0])));
        addSetting(Setting.range("radius", "Scan radius", 64.0, 16, 128, 8, "b"));
        addSetting(Setting.range("opacity", "Opacity", 40.0, 10, 100, 5, "%"));
    }

    public static SearchModule getInstance() { return instance; }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (!isEnabled() || cachedBlocks.isEmpty()) return;

        String blockType = getStringSetting("blocks");
        int alpha = (int) (getDoubleSetting("opacity") / 100.0 * 255);
        int color = switch (blockType) {
            case "Chests" -> 0xFFFFAA00;
            case "Storage" -> 0xFF00AAFF;
            case "Spawners" -> 0xFFFF4444;
            case "Portals" -> 0xFFAA00FF;
            case "Vaults" -> 0xFF4444FF;
            default -> 0xFFFFFF00; // Ores
        };

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        long now = System.currentTimeMillis();
        cachedBlocks.entrySet().removeIf(e -> now - e.getValue() > 10000);

        for (BlockPos pos : cachedBlocks.keySet()) {
            Vec3 renderPos = Vec3.atLowerCornerOf(pos).subtract(camPos);
            AABB box = new AABB(renderPos.x, renderPos.y, renderPos.z,
                    renderPos.x + 1, renderPos.y + 1, renderPos.z + 1);

            LevelRenderer.renderLineBox(poseStack, consumer, box,
                    (color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, alpha);
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        scanTimer++;
        BlockPos currentPos = client.player.blockPosition();

        // Only rescan when player moves significantly or timer fires
        if (scanTimer < 20 && currentPos.distSqr(lastPlayerPos) < 64) return;
        scanTimer = 0;
        lastPlayerPos = currentPos;

        int radius = (int) getDoubleSetting("radius");
        String blockType = getStringSetting("blocks");
        Level level = client.level;

        // Clear old cache beyond radius
        cachedBlocks.entrySet().removeIf(e -> e.getKey().distSqr(currentPos) > radius * radius);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = currentPos.offset(x, y, z);
                    if (cachedBlocks.containsKey(pos)) continue;
                    if (!level.isLoaded(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (isTargetBlock(state, blockType)) {
                        cachedBlocks.put(pos.immutable(), System.currentTimeMillis());
                    }
                }
            }
        }
    }

    private boolean isTargetBlock(BlockState state, String type) {
        String name = state.getBlock().getDescriptionId();
        return switch (type) {
            case "Ores" -> name.contains("ore") || name.contains("diamond") || name.contains("emerald")
                    || name.contains("gold") || name.contains("iron") || name.contains("coal")
                    || name.contains("copper") || name.contains("redstone") || name.contains("lapis")
                    || name.contains("netherite") || name.contains("ancient_debris");
            case "Chests" -> name.contains("chest") || name.contains("barrel");
            case "Storage" -> name.contains("chest") || name.contains("barrel")
                    || name.contains("shulker") || name.contains("furnace")
                    || name.contains("hopper") || name.contains("dispenser") || name.contains("dropper");
            case "Spawners" -> name.contains("spawner");
            case "Portals" -> name.contains("portal");
            case "Vaults" -> name.contains("vault");
            default -> false;
        };
    }

    @Override
    public void onDisable() {
        cachedBlocks.clear();
        scanTimer = 0;
    }
}
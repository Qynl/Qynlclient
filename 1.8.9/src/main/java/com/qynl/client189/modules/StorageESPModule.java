package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * StorageESP — draws an outline around every storage block (chests,
 * furnaces, hoppers, dispensers, brewing stands…) through walls, so loot
 * rooms light up from across the map.
 */
public class StorageESPModule extends Module {
    private static StorageESPModule instance;

    private final List<BlockPos> cache = new ArrayList<>();
    private int scanTimer = 0;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    public StorageESPModule() {
        super("StorageESP", "Outlines all storage blocks through walls.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("range", "Range", 24.0, 8, 48, 4, "b"));
        addSetting(Setting.options("throughWalls", "Through walls", "On", "On", "Off"));
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        int range = (int) getDoubleSetting("range");
        int cx = (int) Math.floor(client.player.x) >> 4;
        int cz = (int) Math.floor(client.player.z) >> 4;
        if (--scanTimer > 0 && cx == lastPlayerChunkX && cz == lastPlayerChunkZ) {
            return;
        }
        scanTimer = 10;
        lastPlayerChunkX = cx;
        lastPlayerChunkZ = cz;

        cache.clear();
        BlockPos base = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.y),
                (int) Math.floor(client.player.z));
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -Math.min(range, 16); dy <= Math.min(range, 16); dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state == null) continue;
                    Block block = state.getBlock();
                    if (block == null || block == Blocks.AIR) continue;
                    if (SearchModule.isStorage(block)) {
                        cache.add(pos);
                    }
                }
            }
        }
    }

    /** Called from the world render hook every frame. */
    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled() || instance.cache.isEmpty()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        boolean through = "On".equals(instance.getStringSetting("throughWalls"));
        WorldDraw.begin(through);
        for (BlockPos pos : instance.cache) {
            WorldDraw.drawBox(pos.getX(), pos.getY(), pos.getZ(), 0.40f, 0.85f, 0.95f, 0.85f, camX, camY, camZ);
        }
        WorldDraw.end();
    }

    @Override
    public void onDisable() {
        cache.clear();
    }
}

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
 * Search — outlines specific blocks through walls (Vape-style). Chests and
 * ore veins become visible behind terrain so you never walk past a
 * diamond stash. The block list is rescanned every few ticks into a cache,
 * so the per-frame cost stays tiny.
 */
public class SearchModule extends Module {
    private static SearchModule instance;

    private final List<BlockPos> cache = new ArrayList<>();
    private int scanTimer = 0;
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    public SearchModule() {
        super("Search", "Outlines chests, ores or storage blocks through walls.", Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("mode", "Mode", "Chests", "Chests", "Ores", "Storage"));
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
        boolean movedChunk = cx != lastPlayerChunkX || cz != lastPlayerChunkZ;
        // Full scan only when the player enters a new chunk, or every ~2 s as
        // a failsafe for block changes. Standing still should cost nothing —
        // the old fixed 10-tick rescan hit ~79k block lookups every 0.5 s
        // even when the world around you had not changed.
        if (!movedChunk && --scanTimer > 0) {
            return;
        }
        scanTimer = 40;
        lastPlayerChunkX = cx;
        lastPlayerChunkZ = cz;

        cache.clear();
        BlockPos base = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.y),
                (int) Math.floor(client.player.z));
        String mode = getStringSetting("mode");
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -Math.min(range, 16); dy <= Math.min(range, 16); dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state == null) continue;
                    Block block = state.getBlock();
                    if (block == null || block == Blocks.AIR) continue;
                    if (matches(block, mode)) {
                        cache.add(pos);
                    }
                }
            }
        }
    }

    private boolean matches(Block block, String mode) {
        switch (mode) {
            case "Ores":
                return block == Blocks.DIAMOND_ORE || block == Blocks.EMERALD_ORE
                        || block == Blocks.GOLD_ORE || block == Blocks.IRON_ORE
                        || block == Blocks.COAL_ORE || block == Blocks.LAPIS_LAZULI_ORE
                        || block == Blocks.REDSTONE_ORE || block == Blocks.LIT_REDSTONE_ORE
                        || block == Blocks.NETHER_QUARTZ_ORE;
            case "Storage":
                return isStorage(block);
            default: // Chests
                return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                        || block == Blocks.ENDERCHEST;
        }
    }

    /** Storage blocks for the "Storage" search mode / StorageESP. */
    public static boolean isStorage(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST
                || block == Blocks.ENDERCHEST
                || block == Blocks.FURNACE || block == Blocks.LIT_FURNACE
                || block == Blocks.DISPENSER || block == Blocks.DROPPER
                || block == Blocks.HOPPER || block == Blocks.BREWING_STAND
                || block == Blocks.BEACON;
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

        float r, g, b;
        switch (instance.getStringSetting("mode")) {
            case "Ores":    r = 0.95f; g = 0.45f; b = 0.40f; break; // red
            case "Storage": r = 0.40f; g = 0.85f; b = 0.95f; break; // cyan
            default:        r = 0.98f; g = 0.80f; b = 0.35f; break; // gold
        }

        for (BlockPos pos : instance.cache) {
            WorldDraw.drawBox(pos.getX(), pos.getY(), pos.getZ(), r, g, b, 0.85f, camX, camY, camZ);
        }

        WorldDraw.end();
    }

    @Override
    public void onDisable() {
        cache.clear();
    }
}

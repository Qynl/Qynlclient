package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * NinjaBridge — automates sneaking at block edges for full-speed bridging.
 *
 * <p><b>Straight mode:</b> walk backwards (S) while the module taps sneak
 * at the edge of each block.</p>
 *
 * <p><b>45° Diagonal mode:</b> walk diagonally backwards (S+A or S+D)
 * for true ninja-bridge speed. At a 45° angle you travel faster along
 * the bridge. The strafe side is picked from your facing angle and then
 * held steady while you move (no mid-bridge zig-zag), or you can force
 * it with the Diag. side setting.</p>
 *
 * <p>Behavior notes:</p>
 * <ul>
 *   <li>Edge sneak fires from your actual <b>movement direction</b> — and
 *       when you come to a stop at an edge it stays held, so you never
 *       slide off the moment you stop moving.</li>
 *   <li>Placement can run fully automatic (Auto) or be left to your own
 *       right-click (Hold RMB), and timing carries a small humanized
 *       jitter so the taps don't look machine-perfect.</li>
 *   <li>All auto-pressed keys (sneak, walk, strafe) are released as soon
 *       as the module is disabled, you stop holding a block, or you die.</li>
 * </ul>
 */
public class NinjaBridgeModule extends Module {

    private static final Random RANDOM = new Random();

    private int cycleTimer;
    private boolean sneaking;
    private int unsneakWindow;
    private int placeCooldown;
    private boolean diagonalLeft = true; // last diagonal lean, kept while moving

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while bridging. 45° diagonal for max speed.", Category.ASSIST);
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.options("mode",      "Mode",        "Straight", "Straight", "45° Diagonal"));
        addSetting(Setting.range("speed",       "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",   "Edge Window",  2.0,  1,   5, 1, "t"));
        addSetting(Setting.options("autoWalk",  "Auto Walk",   "On",  "Off", "On"));
        addSetting(Setting.options("place",     "Place",       "Auto", "Auto", "Hold RMB"));
        addSetting(Setting.options("side",      "Diag. side",  "Auto", "Auto", "Left", "Right"));
        addSetting(Setting.options("humanize",  "Humanize",    "On",  "Off", "On"));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            releaseSneak(client);
            releaseWalkKeys(client);
        }
        sneaking = false;
        cycleTimer = 0;
        unsneakWindow = 0;
        placeCooldown = 0;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        if (!client.player.isAlive()) return;

        if (!isHoldingBlock(client)) {
            releaseSneak(client);
            releaseWalkKeys(client);
            return;
        }

        String mode = getStringSetting("mode");
        boolean diagonal = "45° Diagonal".equals(mode);
        double speedPct = getDoubleSetting("speed") / 100.0;
        int edgeWindow = (int) getDoubleSetting("edgeSneak");
        boolean humanize = "On".equals(getStringSetting("humanize"));

        // Sneak tap length, scaled by speed + optional human jitter.
        int sneakTicks = Math.max(1, (int) Math.round(3.0 / speedPct));
        if (humanize) sneakTicks += RANDOM.nextInt(3); // +0..2 ticks

        boolean atEdge = diagonal ? isAtBlockEdgeDiagonal(client) : isAtBlockEdge(client);
        boolean moving = isMoving(client);

        cycleTimer++;

        if (atEdge) {
            if (!moving) {
                // Standing still at an open edge: hold sneak like shift so you
                // never slide off the moment you stop moving.
                if (!sneaking) {
                    pressSneak(client);
                    sneaking = true;
                }
                cycleTimer = 0;
            } else if (!sneaking) {
                if (unsneakWindow <= 0) {
                    pressSneak(client);
                    sneaking = true;
                    cycleTimer = 0;
                }
            } else if (cycleTimer >= sneakTicks) {
                // Tap done — release while we keep moving so speed isn't lost.
                releaseSneak(client);
                unsneakWindow = edgeWindow;
                cycleTimer = 0;
            }
        } else {
            releaseSneak(client);
            unsneakWindow = 0;
        }

        if (unsneakWindow > 0 && !sneaking) {
            unsneakWindow--;
        }

        // ── Auto walk ────────────────────────────────────────
        if ("On".equals(getStringSetting("autoWalk"))) {
            if (diagonal) {
                applyDiagonalWalk(client);
            } else {
                ((KeyBindingAccessor) client.options.keyBack).setPressed(true);
            }
        }

        // ── Auto place ───────────────────────────────────────
        boolean autoPlace = "Auto".equals(getStringSetting("place"));
        if (placeCooldown > 0) placeCooldown--;

        // Auto mode places for you (unless you're already right-clicking);
        // Hold RMB mode leaves placing to the player, the mod only sneaks/walks.
        if (autoPlace && !client.options.keyUse.isPressed() && placeCooldown <= 0) {
            client.interactionManager.interactItem(
                    client.player, client.world, client.player.inventory.getMainHandStack());
            int base = Math.max(1, (int) Math.round(4.0 / speedPct));
            placeCooldown = humanize ? base + RANDOM.nextInt(2) : base;
        }
    }

    // ── diagonal walk ───────────────────────────────────────────

    /**
     * Moves diagonally backward (S + one strafe key). The strafe side is
     * kept stable while moving so you never zig-zag mid-bridge; when at
     * rest it is chosen from the facing angle (or forced by the setting).
     */
    private void applyDiagonalWalk(MinecraftClient client) {
        String side = getStringSetting("side");
        boolean wantLeft;
        if ("Left".equals(side)) wantLeft = true;
        else if ("Right".equals(side)) wantLeft = false;
        else wantLeft = isMoving(client) ? diagonalLeft : seedDiagonalLeft(client);

        diagonalLeft = wantLeft;

        ((KeyBindingAccessor) client.options.keyBack).setPressed(true);
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(wantLeft);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(!wantLeft);
    }

    /**
     * Default diagonal lean for Auto: map the facing yaw to a side.
     *   0° = south, 90° = west, 180° = north, 270° = east.
     * Facing the western/northern half leans left (A), the eastern/southern
     * half leans right (D) — this matches the classic 45° bridge habit.
     */
    private boolean seedDiagonalLeft(MinecraftClient client) {
        float yaw = client.player.yaw;
        while (yaw < 0) yaw += 360;
        yaw = yaw % 360;
        return (yaw >= 90 && yaw < 180) || yaw >= 270;
    }

    private void releaseWalkKeys(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keyBack).setPressed(false);
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(false);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(false);
    }

    // ── edge detection ──────────────────────────────────────────

    private boolean isAtBlockEdge(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos feetPos = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.getBoundingBox().minY - 0.01),
                (int) Math.floor(client.player.z));

        double relX = client.player.x - feetPos.getX() - 0.5;
        double relZ = client.player.z - feetPos.getZ() - 0.5;

        double absX = Math.abs(relX), absZ = Math.abs(relZ);
        if (absX <= 0.29 && absZ <= 0.29) return false; // still centered on the block

        int dx = 0, dz = 0;
        if (absX > absZ) { dx = relX > 0 ? 1 : -1; }
        else             { dz = relZ > 0 ? 1 : -1; }

        BlockPos ahead = feetPos.add(dx, 0, dz);
        boolean isBridging = client.world.isAir(ahead) && client.world.isAir(ahead.down());

        // No movement-sign requirement: sneaking near an open edge is safe
        // whether you're walking into it, retreating from it, or standing still.
        return isBridging;
    }

    /**
     * Diagonal edge: the player is near at least one block edge and the
     * corner they are moving toward (or the corner their position biases
     * toward when standing still) is missing, so the bridge needs a block.
     */
    private boolean isAtBlockEdgeDiagonal(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos feetPos = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.getBoundingBox().minY - 0.01),
                (int) Math.floor(client.player.z));

        double relX = client.player.x - feetPos.getX() - 0.5;
        double relZ = client.player.z - feetPos.getZ() - 0.5;
        double absX = Math.abs(relX), absZ = Math.abs(relZ);
        if (absX <= 0.25 && absZ <= 0.25) return false;

        // Use the movement direction when moving, position bias when still.
        double mx, mz;
        if (isMoving(client)) {
            mx = client.player.x - client.player.prevX;
            mz = client.player.z - client.player.prevZ;
        } else {
            mx = relX;
            mz = relZ;
        }

        int dx = mx > 0 ? 1 : -1;
        int dz = mz > 0 ? 1 : -1;

        BlockPos corner = feetPos.add(dx, 0, dz);
        return client.world.isAir(corner) && client.world.isAir(corner.down());
    }

    // ── helpers ─────────────────────────────────────────────────

    /** True when the player is actually moving horizontally (> ~5 mm/tick). */
    private boolean isMoving(MinecraftClient client) {
        if (client.player == null) return false;
        double mx = client.player.x - client.player.prevX;
        double mz = client.player.z - client.player.prevZ;
        return mx * mx + mz * mz > 0.000025;
    }

    private void pressSneak(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keySneak).setPressed(true);
    }

    private void releaseSneak(MinecraftClient client) {
        if (sneaking) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneaking = false;
        }
    }

    private boolean isHoldingBlock(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack held = client.player.inventory.getMainHandStack();
        return held != null && held.getItem() instanceof BlockItem;
    }
}

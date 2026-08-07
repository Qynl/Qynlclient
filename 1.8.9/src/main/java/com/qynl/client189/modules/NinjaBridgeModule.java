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

/**
 * NinjaBridge — automates sneaking at block edges for full-speed bridging.
 *
 * <p><b>Straight mode:</b> walk backwards (S) while the module taps sneak
 * at the edge of each block.</p>
 *
 * <p><b>45° Diagonal mode:</b> walk diagonally backwards (S+A or S+D)
 * for true ninja-bridge speed. At a 45° angle, you travel faster along
 * the bridge and place blocks more quickly. The module picks the strafe
 * direction that matches your facing angle so you bridge diagonally
 * without thinking.</p>
 */
public class NinjaBridgeModule extends Module {

    private int cycleTimer;
    private boolean sneaking;
    private int unsneakWindow;
    private int placeCooldown;

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while bridging. 45° diagonal for max speed.", Category.ASSIST);
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.options("mode",      "Mode",        "Straight", "Straight", "45° Diagonal"));
        addSetting(Setting.range("speed",       "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",   "Edge Window",  2.0,  1,   5, 1, "t"));
        addSetting(Setting.options("autoWalk",  "Auto Walk",   "On",  "Off", "On"));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            if (sneaking) {
                ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            }
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
            if (sneaking) releaseSneak(client);
            releaseWalkKeys(client);
            return;
        }

        String mode = getStringSetting("mode");
        boolean diagonal = "45° Diagonal".equals(mode);
        double speedPct = getDoubleSetting("speed") / 100.0;
        int edgeWindow = (int) getDoubleSetting("edgeSneak");

        // ── Sneak timing ─────────────────────────────────────
        int sneakTicks   = Math.max(1, (int) Math.round(3.0 / speedPct));
        int unsneakTicks = Math.max(1, (int) Math.round(5.0 / speedPct));

        boolean atEdge = diagonal ? isAtBlockEdgeDiagonal(client) : isAtBlockEdge(client);

        cycleTimer++;

        if (atEdge) {
            if (!sneaking) {
                if (unsneakWindow <= 0) {
                    ((KeyBindingAccessor) client.options.keySneak).setPressed(true);
                    sneaking = true;
                    cycleTimer = 0;
                }
            } else if (cycleTimer >= sneakTicks) {
                ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
                sneaking = false;
                unsneakWindow = edgeWindow;
                cycleTimer = 0;
            }
        } else {
            if (sneaking) {
                ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
                sneaking = false;
                unsneakWindow = 0;
            }
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
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (!client.options.keyUse.isPressed() && placeCooldown <= 0) {
            client.interactionManager.interactItem(
                    client.player, client.world, client.player.inventory.getMainHandStack());
            placeCooldown = Math.max(1, (int) Math.round(4.0 / speedPct));
        }
    }

    // ── diagonal walk ───────────────────────────────────────────

    /**
     * Determines which diagonal direction to walk based on the player's
     * current yaw (facing angle). For 45° ninja bridging, you want to
     * move diagonally backwards-left or backwards-right.
     */
    private void applyDiagonalWalk(MinecraftClient client) {
        float yaw = client.player.yaw;
        // Normalize yaw to 0-360
        while (yaw < 0) yaw += 360;
        yaw = yaw % 360;

        // Map yaw to the dominant diagonal direction.
        //   0° = south (forward), 90° = west, 180° = north (back), 270° = east
        // For bridging, we walk opposite to the facing direction + 45° diagonal.
        // Simplified: pick left-strafe or right-strafe based on which quadrant
        // the player is facing.

        boolean strafeLeft;
        if (yaw >= 0   && yaw < 90)  strafeLeft = false; // facing SW → strafe right (D)
        else if (yaw >= 90  && yaw < 180) strafeLeft = true;  // facing NW → strafe left (A)
        else if (yaw >= 180 && yaw < 270) strafeLeft = false; // facing NE → strafe right (D)
        else                             strafeLeft = true;  // facing SE → strafe left (A)

        ((KeyBindingAccessor) client.options.keyBack).setPressed(true);

        if (strafeLeft) {
            ((KeyBindingAccessor) client.options.keyLeft).setPressed(true);
            ((KeyBindingAccessor) client.options.keyRight).setPressed(false);
        } else {
            ((KeyBindingAccessor) client.options.keyRight).setPressed(true);
            ((KeyBindingAccessor) client.options.keyLeft).setPressed(false);
        }
    }

    private void releaseWalkKeys(MinecraftClient client) {
        ((KeyBindingAccessor) client.options.keyBack).setPressed(false);
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(false);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(false);
    }

    // ── edge detection: straight ────────────────────────────────

    private boolean isAtBlockEdge(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos feetPos = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.getBoundingBox().minY - 0.01),
                (int) Math.floor(client.player.z));

        double relX = client.player.x - feetPos.getX() - 0.5;
        double relZ = client.player.z - feetPos.getZ() - 0.5;

        double absX = Math.abs(relX), absZ = Math.abs(relZ);
        if (absX <= 0.29 && absZ <= 0.29) return false;

        int dx = 0, dz = 0;
        if (absX > absZ) { dx = relX > 0 ? 1 : -1; }
        else             { dz = relZ > 0 ? 1 : -1; }

        BlockPos ahead = feetPos.add(dx, 0, dz);
        boolean isBridging = client.world.isAir(ahead) && client.world.isAir(ahead.down());

        double moveX = client.player.x - client.player.prevX;
        double moveZ = client.player.z - client.player.prevZ;
        boolean movingBackwards = (dx != 0 && Math.signum(moveX) == -Math.signum(dx))
                || (dz != 0 && Math.signum(moveZ) == -Math.signum(dz));

        return isBridging && movingBackwards;
    }

    // ── edge detection: 45° diagonal ────────────────────────────

    /**
     * For 45° diagonal bridging, the player is at the edge along BOTH
     * X and Z axes simultaneously. The detection is more generous
     * because diagonal movement shifts the edge threshold.
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

        // With diagonal bridging, at least one axis must be near the edge
        boolean atEdge = absX > 0.25 || absZ > 0.25;
        if (!atEdge) return false;

        int dx = relX > 0 ? 1 : -1;
        int dz = relZ > 0 ? 1 : -1;

        // Check the diagonal corner ahead (dx, dz)
        BlockPos corner = feetPos.add(dx, 0, dz);
        BlockPos cornerBelow = corner.down();

        boolean isBridging = client.world.isAir(corner) && client.world.isAir(cornerBelow);

        // Check movement direction: should be roughly diagonal backwards
        double moveX = client.player.x - client.player.prevX;
        double moveZ = client.player.z - client.player.prevZ;
        boolean movingBackwards = Math.signum(moveX) == -Math.signum(dx)
                && Math.signum(moveZ) == -Math.signum(dz);

        return isBridging && movingBackwards;
    }

    // ── helpers ─────────────────────────────────────────────────

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

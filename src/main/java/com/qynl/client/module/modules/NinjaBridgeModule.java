package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * NinjaBridge — automates sneaking at block edges for full-speed bridging.
 *
 * <p><b>Straight mode:</b> walk backwards (S) while the module taps sneak
 * at the edge of each block.</p>
 *
 * <p><b>45° Diagonal mode:</b> walk diagonally backwards (S+A or S+D)
 * for true ninja-bridge speed. At a 45° angle, you travel faster along
 * the bridge and place blocks more quickly. The module picks the strafe
 * direction that matches your facing angle.</p>
 */
public class NinjaBridgeModule extends Module {

    private int cycleTimer;
    private boolean sneaking;
    private int unsneakWindow;
    private int placeCooldown;

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while bridging. 45° diagonal for max speed.",
                Category.ASSIST);
        bindKey(GLFW.GLFW_KEY_N);
        addSetting(Setting.options("mode",      "Mode",        "Straight", "Straight", "45\u00b0 Diagonal"));
        addSetting(Setting.range("speed",       "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",   "Edge Window",  2.0,  1,   5, 1, "t"));
        addSetting(Setting.options("autoWalk",  "Auto Walk",   "On",  "Off", "On"));
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            if (sneaking) {
                client.options.keyShift.setDown(false);
            }
            releaseWalkKeys(client);
        }
        sneaking = false;
        cycleTimer = 0;
        unsneakWindow = 0;
        placeCooldown = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;
        if (!client.player.isAlive()) return;

        if (!isHoldingBlock(client)) {
            if (sneaking) {
                client.options.keyShift.setDown(false);
                sneaking = false;
            }
            return;
        }

        // Must be looking at a block to place against (actively bridging)
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
            if (sneaking) {
                client.options.keyShift.setDown(false);
                sneaking = false;
            }
            return;
        }

        String mode = getStringSetting("mode");
        boolean diagonal = "45\u00b0 Diagonal".equals(mode);
        double speedPct = getDoubleSetting("speed") / 100.0;
        int edgeWindow = (int) getDoubleSetting("edgeSneak");

        int sneakTicks = Math.max(1, (int) Math.round(3.0 / speedPct));
        int unsneakTicks = Math.max(1, (int) Math.round(5.0 / speedPct));

        boolean atEdge = diagonal ? isAtBlockEdgeDiagonal(client) : isAtBlockEdge(client);

        cycleTimer++;

        if (atEdge) {
            if (!sneaking) {
                if (unsneakWindow <= 0) {
                    client.options.keyShift.setDown(true);
                    sneaking = true;
                    cycleTimer = 0;
                }
            } else if (cycleTimer >= sneakTicks) {
                client.options.keyShift.setDown(false);
                sneaking = false;
                unsneakWindow = edgeWindow;
                cycleTimer = 0;
            }
        } else {
            if (sneaking) {
                client.options.keyShift.setDown(false);
                sneaking = false;
                unsneakWindow = 0;
            }
        }

        if (unsneakWindow > 0 && !sneaking) {
            unsneakWindow--;
        }

        // Auto walk
        if ("On".equals(getStringSetting("autoWalk"))) {
            if (diagonal) {
                applyDiagonalWalk(client);
            } else {
                client.options.keyUp.setDown(true);
            }
        }

        // Auto right-click to place blocks
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (!client.options.keyUse.isDown() && placeCooldown <= 0) {
            client.options.keyUse.setDown(true);
            placeCooldown = 1;
        }
        if (placeCooldown == 0 && client.options.keyUse.isDown()) {
            client.options.keyUse.setDown(false);
            placeCooldown = Math.max(1, (int) Math.round(4.0 / speedPct));
        }
    }

    // ── diagonal walk ───────────────────────────────────────────

    private void applyDiagonalWalk(Minecraft client) {
        float yaw = client.player.getYRot();
        while (yaw < 0) yaw += 360;
        yaw = yaw % 360;

        boolean strafeLeft;
        if (yaw >= 0   && yaw < 90)  strafeLeft = false;
        else if (yaw >= 90  && yaw < 180) strafeLeft = true;
        else if (yaw >= 180 && yaw < 270) strafeLeft = false;
        else                             strafeLeft = true;

        client.options.keyUp.setDown(true);
        if (strafeLeft) {
            client.options.keyLeft.setDown(true);
            client.options.keyRight.setDown(false);
        } else {
            client.options.keyRight.setDown(true);
            client.options.keyLeft.setDown(false);
        }
    }

    private void releaseWalkKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
    }

    // ── edge detection ──────────────────────────────────────────

    private boolean isAtBlockEdge(Minecraft client) {
        if (client.player == null || client.level == null) return false;

        BlockPos feetPos = client.player.getBlockPosBelowThatAffectsMyMovement();

        double relX = client.player.getX() - feetPos.getX() - 0.5;
        double relZ = client.player.getZ() - feetPos.getZ() - 0.5;
        double absX = Math.abs(relX), absZ = Math.abs(relZ);

        if (absX <= 0.29 && absZ <= 0.29) return false;

        int dx = 0, dz = 0;
        if (absX > absZ) { dx = relX > 0 ? 1 : -1; }
        else             { dz = relZ > 0 ? 1 : -1; }

        BlockPos ahead = feetPos.offset(dx, 0, dz);
        boolean isBridging = client.level.isEmptyBlock(ahead)
                && client.level.isEmptyBlock(ahead.below());

        double mx = client.player.getDeltaMovement().x;
        double mz = client.player.getDeltaMovement().z;
        boolean movingBackwards = (dx != 0 && Math.signum(mx) == -Math.signum(dx))
                || (dz != 0 && Math.signum(mz) == -Math.signum(dz));

        return isBridging && movingBackwards;
    }

    private boolean isAtBlockEdgeDiagonal(Minecraft client) {
        if (client.player == null || client.level == null) return false;

        BlockPos feetPos = client.player.getBlockPosBelowThatAffectsMyMovement();

        double relX = client.player.getX() - feetPos.getX() - 0.5;
        double relZ = client.player.getZ() - feetPos.getZ() - 0.5;
        double absX = Math.abs(relX), absZ = Math.abs(relZ);

        if (absX <= 0.25 && absZ <= 0.25) return false;

        int dx = relX > 0 ? 1 : -1;
        int dz = relZ > 0 ? 1 : -1;

        BlockPos corner = feetPos.offset(dx, 0, dz);
        boolean isBridging = client.level.isEmptyBlock(corner)
                && client.level.isEmptyBlock(corner.below());

        double mx = client.player.getDeltaMovement().x;
        double mz = client.player.getDeltaMovement().z;
        boolean movingBackwards = Math.signum(mx) == -Math.signum(dx)
                && Math.signum(mz) == -Math.signum(dz);

        return isBridging && movingBackwards;
    }

    private boolean isHoldingBlock(Minecraft client) {
        if (client.player == null) return false;
        ItemStack held = client.player.getMainHandItem();
        return !held.isEmpty() && held.getItem() instanceof BlockItem;
    }
}

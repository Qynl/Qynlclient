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
 * NinjaBridge — automates the ninja-bridging technique by rapidly sneaking
 * at the very edge of each placed block so you can bridge at full speed
 * without falling off. Hold right-click with blocks and walk backwards —
 * the module handles the sneak rhythm.
 *
 * <p>For 1.21.1: sneaking has a movement-speed penalty, so the module
 * minimises time spent sneaking while still keeping you on the block.</p>
 */
public class NinjaBridgeModule extends Module {
    /** Ticks since last sneak toggle. */
    private int cycleTimer;
    /** Whether sneak is currently held by the module. */
    private boolean sneaking;
    /** Whether we just unsneaked and are waiting to re-sneak. */
    private int unsneakWindow;
    /** Counter for the placement cooldown. */
    private int placeCooldown;

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while you bridge — full speed, no falling.",
                Category.ASSIST);
        bindKey(GLFW.GLFW_KEY_N);
        addSetting(Setting.range("speed",      "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",  "Edge Window", 2.0,   1,   5, 1, "t"));
        addSetting(Setting.options("autoWalk", "Auto Walk",  "Off", "Off", "On"));
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && sneaking) {
            client.options.keyShift.setDown(false);
        }
        // Release auto-walk
        client.options.keyUp.setDown(false);
        sneaking = false;
        cycleTimer = 0;
        unsneakWindow = 0;
        placeCooldown = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) return;

        // Must be holding a placeable block
        if (!isHoldingBlock(client)) return;

        // Must be looking at a block to place against (actively bridging)
        if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
            if (sneaking) {
                client.options.keyShift.setDown(false);
                sneaking = false;
            }
            return;
        }

        double speedPct = getDoubleSetting("speed") / 100.0;
        int edgeWindow = (int) getDoubleSetting("edgeSneak");

        // Cycle timing: faster speed = shorter cycles
        int sneakTicks = Math.max(1, (int) Math.round(3.0 / speedPct));
        int unsneakTicks = Math.max(1, (int) Math.round(5.0 / speedPct));

        boolean atEdge = isAtBlockEdge(client);

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

        // Auto-walk backwards
        if ("On".equals(getStringSetting("autoWalk"))) {
            client.options.keyUp.setDown(true);
        }

        // Auto right-click to place blocks
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (!client.options.keyUse.isDown() && placeCooldown <= 0) {
            client.options.keyUse.setDown(true);
            placeCooldown = 1;
        }
        // Release use key briefly between placements
        if (placeCooldown == 0 && client.options.keyUse.isDown()) {
            client.options.keyUse.setDown(false);
            placeCooldown = Math.max(1, (int) Math.round(4.0 / speedPct));
        }
    }

    /** Returns true if the player is standing right at the edge of a block with a drop ahead. */
    private boolean isAtBlockEdge(Minecraft client) {
        if (client.player == null || client.level == null) return false;

        BlockPos feetPos = client.player.getBlockPosBelowThatAffectsMyMovement();

        double relX = client.player.getX() - feetPos.getX() - 0.5;
        double relZ = client.player.getZ() - feetPos.getZ() - 0.5;

        double absX = Math.abs(relX);
        double absZ = Math.abs(relZ);

        boolean atEdge = absX > 0.29 || absZ > 0.29;
        if (!atEdge) return false;

        int dx = 0, dz = 0;
        if (absX > absZ) {
            dx = relX > 0 ? 1 : -1;
        } else {
            dz = relZ > 0 ? 1 : -1;
        }

        BlockPos ahead = feetPos.offset(dx, 0, dz);
        BlockPos belowAhead = ahead.below();

        boolean isBridging = client.level.isEmptyBlock(ahead) && client.level.isEmptyBlock(belowAhead);

        double motionX = client.player.getDeltaMovement().x;
        double motionZ = client.player.getDeltaMovement().z;
        boolean movingBackwards = (dx != 0 && Math.signum(motionX) == -Math.signum(dx))
                || (dz != 0 && Math.signum(motionZ) == -Math.signum(dz));

        return isBridging && movingBackwards;
    }

    private boolean isHoldingBlock(Minecraft client) {
        if (client.player == null) return false;
        ItemStack held = client.player.getMainHandItem();
        return !held.isEmpty() && held.getItem() instanceof BlockItem;
    }
}

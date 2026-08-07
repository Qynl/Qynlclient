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
 * NinjaBridge — automates the ninja-bridging technique by rapidly sneaking
 * at the very edge of each placed block so you can bridge at full speed
 * without falling off. Hold right-click with blocks and walk backwards —
 * the module handles the sneak rhythm.
 *
 * <p>For 1.8.9: no attack cooldown, so bridging is extremely fast.
 * The module pulses sneak in a tight window at the block edge.</p>
 */
public class NinjaBridgeModule extends Module {

    private int cycleTimer;
    private boolean sneaking;
    private int unsneakWindow;
    private int placeCooldown;

    public NinjaBridgeModule() {
        super("NinjaBridge", "Auto-sneaks at block edges while you bridge — full speed, no falling.", Category.ASSIST);
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.range("speed",      "Speed",       75.0, 50, 100, 1, "%"));
        addSetting(Setting.range("edgeSneak",  "Edge Window",  2.0,  1,   5, 1, "t"));
        addSetting(Setting.options("autoWalk", "Auto Walk",   "Off", "Off", "On"));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && sneaking) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
        }
        if (client.player != null) {
            ((KeyBindingAccessor) client.options.keyBack).setPressed(false);
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

        // Must be holding a placeable block
        if (!isHoldingBlock(client)) {
            if (sneaking) releaseSneak(client);
            return;
        }

        double speedPct = getDoubleSetting("speed") / 100.0;
        int edgeWindow = (int) getDoubleSetting("edgeSneak");

        int sneakTicks = Math.max(1, (int) Math.round(3.0 / speedPct));
        int unsneakTicks = Math.max(1, (int) Math.round(5.0 / speedPct));

        boolean atEdge = isAtBlockEdge(client);

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

        // Auto-walk backwards (hold S)
        if ("On".equals(getStringSetting("autoWalk"))) {
            ((KeyBindingAccessor) client.options.keyBack).setPressed(true);
        }

        // Auto right-click to place blocks
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        if (!client.options.keyUse.isPressed() && placeCooldown <= 0) {
            client.interactionManager.interactItem(
                    client.player, client.world, client.player.inventory.getMainHandStack());
            placeCooldown = Math.max(1, (int) Math.round(4.0 / speedPct));
        }
    }

    private void releaseSneak(MinecraftClient client) {
        if (sneaking) {
            ((KeyBindingAccessor) client.options.keySneak).setPressed(false);
            sneaking = false;
        }
    }

    /** Returns true if the player is standing right at the edge of a block with a drop ahead. */
    private boolean isAtBlockEdge(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;

        BlockPos feetPos = new BlockPos(
                (int) Math.floor(client.player.x),
                (int) Math.floor(client.player.getBoundingBox().minY - 0.01),
                (int) Math.floor(client.player.z)
        );

        double relX = client.player.x - feetPos.getX() - 0.5;
        double relZ = client.player.z - feetPos.getZ() - 0.5;

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

        BlockPos ahead = feetPos.add(dx, 0, dz);
        BlockPos belowAhead = ahead.down();

        boolean isBridging = client.world.isAir(ahead) && client.world.isAir(belowAhead);

        // Detect walking direction from position delta between ticks
        double moveX = client.player.x - client.player.prevX;
        double moveZ = client.player.z - client.player.prevZ;
        boolean movingBackwards = (dx != 0 && Math.signum(moveX) == -Math.signum(dx))
                || (dz != 0 && Math.signum(moveZ) == -Math.signum(dz));

        return isBridging && movingBackwards;
    }

    private boolean isHoldingBlock(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack held = client.player.inventory.getMainHandStack();
        return held != null && held.getItem() instanceof BlockItem;
    }
}

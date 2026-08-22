package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * AutoStep — when you walk into a one-block ledge, QynlClient jumps for
 * you. Climbing stairs, hills and chest-high ledges becomes effortless, so
 * one missed jump never stops you. Applied via
 * {@link com.qynl.client189.mixin.InputMixin} at the correct moment in the
 * tick — the server only ever sees a normal jump.
 */
public class AutoStepModule extends Module {
    private static AutoStepModule instance;

    public AutoStepModule() {
        super("AutoStep", "Automatically climbs ledges while you walk.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_F6);
        addSetting(Setting.options("height", "Height", "1.0", "1.0", "1.25"));
    }

    /**
     * True when the player is walking into a low ledge and should jump this
     * tick.
     */
    public static boolean shouldJump(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        if (client.player == null || client.world == null) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        if (!player.onGround || player.isSneaking() || player.hasVehicle()
                || player.isUsingItem()) {
            return false;
        }
        if (player.input.movementForward <= 0.0F) {
            return false;
        }

        float yawRad = (float) Math.toRadians(player.yaw);
        double dx = -Math.sin(yawRad);
        double dz = Math.cos(yawRad);
        BlockPos ahead = new BlockPos(player.x + dx * 1.2, player.y, player.z + dz * 1.2);

        // Solid at foot level (the ledge)…
        if (!client.world.getBlockState(ahead).getBlock().hasCollision()) {
            return false;
        }
        boolean tall = "1.25".equals(instance.getStringSetting("height"));
        if (!tall) {
            // 1.0 mode: open air above the ledge.
            if (client.world.getBlockState(ahead.up()).getBlock().hasCollision()) {
                return false;
            }
        } else {
            // 1.25 mode: a slab/carpet on top of the ledge is fine, but a
            // full second block (2-high wall) is not, and headroom above
            // must stay clear.
            net.minecraft.block.Block oneUp = client.world.getBlockState(ahead.up()).getBlock();
            if (oneUp.hasCollision() && oneUp.isFullCube()) {
                return false;
            }
            if (client.world.getBlockState(ahead.up(2)).getBlock().hasCollision()) {
                return false;
            }
        }
        return true;
    }
}

package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * SafeWalk — auto-sneaks at unsupported edges so you never walk off a cliff
 * or bridge. The edge check reads the block the player is about to step
 * onto; when it has no collision, sneak is forced for that tick (see
 * {@link com.qynl.client189.mixin.InputMixin}). Nothing is sent to the
 * server beyond a normal sneak, so it is fully silent.
 */
public class SafeWalkModule extends Module {
    private static SafeWalkModule instance;

    public SafeWalkModule() {
        super("SafeWalk", "Auto-sneak at block edges so you never walk off.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
    }

    /**
     * True when the player is walking toward an unsupported edge and should
     * sneak this tick.
     */
    public static boolean shouldSneak(MinecraftClient client) {
        if (instance == null || !instance.isEnabled()) {
            return false;
        }
        if (client.player == null || client.world == null) {
            return false;
        }
        ClientPlayerEntity player = client.player;
        if (!player.onGround || player.hasVehicle() || player.abilities.flying) {
            return false;
        }
        // Only relevant while actually moving along the ground.
        if (player.velocityX == 0.0 && player.velocityZ == 0.0) {
            return false;
        }
        BlockPos support = new BlockPos(player.x, player.y - 1.0, player.z);
        BlockPos ahead = support.add(
                (int) Math.signum(player.velocityX),
                0,
                (int) Math.signum(player.velocityZ));
        return !client.world.getBlockState(ahead).getBlock().hasCollision();
    }
}

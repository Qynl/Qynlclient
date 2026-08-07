package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public class SafeWalkModule extends Module {
	private static SafeWalkModule instance;

	public SafeWalkModule() {
		super("SafeWalk", "Auto-sneak at block edges so you never walk off.", Category.ASSIST);
		instance = this;
		bindKey(GLFW.GLFW_KEY_C);
	}

	/**
	 * True when the player is walking toward an unsupported edge and should
	 * sneak. Checked by InputMixin at the correct moment in the tick.
	 */
	public static boolean shouldSneak(Minecraft client) {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		if (client.player == null || client.level == null) {
			return false;
		}
		LocalPlayer player = client.player;
		if (!player.onGround() || player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
			return false;
		}
		// Only relevant while actually moving along the ground.
		var velocity = player.getDeltaMovement();
		if (velocity.x == 0.0 && velocity.z == 0.0) {
			return false;
		}

		BlockPos support = player.getBlockPosBelowThatAffectsMyMovement();
		BlockPos ahead = support.offset(
				(int) Math.signum(velocity.x),
				0,
				(int) Math.signum(velocity.z));
		BlockState aheadState = client.level.getBlockState(ahead);
		return aheadState.getCollisionShape(client.level, ahead).isEmpty();
	}
}

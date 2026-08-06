package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public class SafeWalkModule extends Module {
	public SafeWalkModule() {
		super("SafeWalk", "Auto-sneak at block edges so you never walk off.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_C);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		LocalPlayer player = client.player;
		if (!player.onGround() || player.isPassenger() || player.isSpectator() || player.getAbilities().flying) {
			return;
		}
		// Only relevant while actually moving along the ground.
		var velocity = player.getDeltaMovement();
		if (velocity.x == 0.0 && velocity.z == 0.0) {
			return;
		}

		BlockPos support = player.getBlockPosBelowThatAffectsMyMovement();
		BlockPos ahead = support.offset(
				(int) Math.signum(velocity.x),
				0,
				(int) Math.signum(velocity.z));
		BlockState aheadState = client.level.getBlockState(ahead);
		if (aheadState.getCollisionShape(client.level, ahead).isEmpty()) {
			player.setShiftKeyDown(true);
		}
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * AutoClimb — just walk toward a ladder or vine and QynlClient climbs it
 * for you. No need to aim at the ladder and hold space at the same time,
 * which is a real help when fine control is hard.
 */
public class AutoClimbModule extends Module {
	private boolean forcingJump = false;

	public AutoClimbModule() {
		super("AutoClimb", "Climbs ladders and vines automatically when you move toward them.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_L);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.options == null) {
			return;
		}
		var player = client.player;
		boolean moving = client.options.keyUp.isDown()
				|| client.options.keyDown.isDown()
				|| client.options.keyLeft.isDown()
				|| client.options.keyRight.isDown();
		boolean shouldClimb = player.onClimbable() && moving
				&& !player.isCrouching() && !player.isSpectator();
		if (shouldClimb) {
			client.options.keyJump.setDown(true);
			forcingJump = true;
		} else if (forcingJump) {
			client.options.keyJump.setDown(false);
			forcingJump = false;
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			client.options.keyJump.setDown(false);
		}
		forcingJump = false;
	}
}

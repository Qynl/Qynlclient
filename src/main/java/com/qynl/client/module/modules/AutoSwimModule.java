package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * AutoSwim — swims up automatically while you move forward in water, so
 * you never sink or have to hold space and navigate at the same time.
 */
public class AutoSwimModule extends Module {
	private boolean forcingJump = false;

	public AutoSwimModule() {
		super("AutoSwim", "Swims up automatically while you move forward in water.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_M);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.options == null) {
			return;
		}
		boolean shouldSwim = client.player.isInWater()
				&& client.player.input.hasForwardImpulse()
				&& !client.player.isSpectator();
		if (shouldSwim) {
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

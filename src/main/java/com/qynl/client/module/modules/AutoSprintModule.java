package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class AutoSprintModule extends Module {
	public AutoSprintModule() {
		super("AutoSprint", "Automatically sprint while you move forward.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_Y);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		var player = client.player;
		if (player.isSprinting()) {
			return;
		}
		boolean movingForward = player.input.forwardImpulse > 0.0F;
		boolean canSprint = player.getFoodData().getFoodLevel() > 6
				&& !player.isCrouching()
				&& !player.isUsingItem()
				&& !player.isPassenger();
		if (movingForward && canSprint) {
			player.setSprinting(true);
		}
	}
}

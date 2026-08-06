package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * InvWalk — keep moving while your inventory or any menu is open.
 * Walking while browsing your hotbar is a small thing that makes a
 * big difference when multitasking is hard.
 */
public class InvWalkModule extends Module {
	public InvWalkModule() {
		super("InvWalk", "Keep walking while your inventory or any menu is open.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_P);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.options == null || client.screen == null) {
			return;
		}
		var player = client.player;
		player.input.up = client.options.keyUp.isDown();
		player.input.down = client.options.keyDown.isDown();
		player.input.left = client.options.keyLeft.isDown();
		player.input.right = client.options.keyRight.isDown();
		player.input.jumping = client.options.keyJump.isDown();
		player.input.shiftKeyDown = client.options.keyShift.isDown();
		player.input.forwardImpulse = player.input.up ? 1.0F : player.input.down ? -1.0F : 0.0F;
		player.input.leftImpulse = player.input.left ? 1.0F : player.input.right ? -1.0F : 0.0F;
	}
}

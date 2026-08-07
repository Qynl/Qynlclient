package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class AutoJumpModule extends Module {
	private boolean hadAutoJump = false;

	public AutoJumpModule() {
		super("AutoJump", "Automatically hop over small gaps and up stairs.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_N);
	}

	@Override
	public void onEnable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			// Remember the user's own setting so disabling restores it.
			hadAutoJump = client.options.autoJump().get();
			client.options.autoJump().set(true);
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			client.options.autoJump().set(hadAutoJump);
		}
	}
}

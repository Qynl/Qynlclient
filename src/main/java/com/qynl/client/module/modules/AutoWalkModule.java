package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class AutoWalkModule extends Module {
	public AutoWalkModule() {
		super("AutoWalk", "Walk forward automatically — free your hands while travelling.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_B);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (client.screen != null) {
			client.options.keyUp.setDown(false);
			return;
		}
		client.options.keyUp.setDown(true);
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			client.options.keyUp.setDown(false);
		}
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ToggleSneakModule extends Module {
	public ToggleSneakModule() {
		super("ToggleSneak", "Sneak without holding the key down — handy while building.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_V);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player != null) {
			client.player.setShiftKeyDown(true);
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.player.setShiftKeyDown(false);
		}
	}
}

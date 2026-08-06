package com.qynl.client.module.modules;

import com.qynl.client.hud.ClickGuiScreen;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ClickGuiModule extends Module {
	public ClickGuiModule() {
		super("ClickGUI", "Open the module manager screen where you can toggle every module.", Category.GUI);
		bindKey(GLFW.GLFW_KEY_RIGHT_SHIFT);
	}

	@Override
	public void onEnable() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			client.setScreen(new ClickGuiScreen());
		} else {
			setEnabled(false);
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.screen instanceof ClickGuiScreen) {
			client.setScreen(null);
		}
	}
}

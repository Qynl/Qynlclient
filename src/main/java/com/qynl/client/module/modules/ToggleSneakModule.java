package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ToggleSneakModule extends Module {
	private static ToggleSneakModule instance;

	public ToggleSneakModule() {
		super("ToggleSneak", "Sneak without holding the key down — handy while building.", Category.ASSIST);
		instance = this;
		bindKey(GLFW.GLFW_KEY_V);
	}

	/** Whether the module is currently forcing sneak. Checked by InputMixin. */
	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	@Override
	public void onDisable() {
		// The InputMixin stops forcing sneak as soon as the module is off.
	}
}

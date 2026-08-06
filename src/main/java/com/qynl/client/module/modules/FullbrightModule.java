package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class FullbrightModule extends Module {
	private double originalGamma = -1.0;

	public FullbrightModule() {
		super("Fullbright", "Boost brightness so you can see clearly in the dark.", Category.RENDER);
		bindKey(GLFW.GLFW_KEY_K);
	}

	@Override
	public void onEnable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			if (originalGamma < 0) {
				originalGamma = client.options.gamma().get();
			}
			client.options.gamma().set(1.0);
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null && originalGamma >= 0) {
			client.options.gamma().set(originalGamma);
			originalGamma = -1.0;
		}
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.options != null && client.options.gamma().get() < 1.0) {
			client.options.gamma().set(1.0);
		}
	}
}

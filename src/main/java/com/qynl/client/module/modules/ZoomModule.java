package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class ZoomModule extends Module {
	private static final double ZOOM_LEVEL = 2.5;

	private double baseFov = -1.0;
	private double baseSensitivity = -1.0;

	public ZoomModule() {
		super("Zoom", "Hold the key to zoom in for a closer look.", Category.RENDER);
		bindKey(GLFW.GLFW_KEY_Z);
	}

	@Override
	public void onEnable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options == null) {
			return;
		}
		baseFov = client.options.fov().get();
		baseSensitivity = client.options.sensitivity().get();
		applyZoom(client, true);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.options == null) {
			return;
		}
		if (getKeyMapping() != null && getKeyMapping().isDown()) {
			if (baseFov < 0) {
				baseFov = client.options.fov().get();
				baseSensitivity = client.options.sensitivity().get();
			}
			applyZoom(client, true);
		} else {
			applyZoom(client, false);
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		applyZoom(client, false);
		baseFov = -1.0;
		baseSensitivity = -1.0;
	}

	private void applyZoom(Minecraft client, boolean zoomed) {
		if (client.options == null) {
			return;
		}
		if (zoomed && baseFov >= 0) {
			client.options.fov().set((int) Math.round(baseFov / ZOOM_LEVEL));
			client.options.sensitivity().set(baseSensitivity / ZOOM_LEVEL);
		} else {
			if (baseFov >= 0) {
				client.options.fov().set((int) Math.round(baseFov));
			}
			if (baseSensitivity >= 0) {
				client.options.sensitivity().set(baseSensitivity);
			}
		}
	}
}

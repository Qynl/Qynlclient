package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * FastPlaceAssist — reduces or removes the right-click placement delay
 * so the behinderten can build quickly without fighting the default
 * 4-tick item-use cooldown.
 *
 * <p>Settings:</p>
 * <ul>
 *   <li><b>Speed</b> — Instant (no delay), Fast (1 tick), or Slow (2 ticks)
 *       so the server sees a natural-but-quick placement rhythm.</li>
 * </ul>
 */
public class FastPlaceAssistModule extends Module {

	public FastPlaceAssistModule() {
		super("FastPlaceAssist",
				"Reduces the right-click delay so you can place blocks quickly. Adjustable speed for natural rhythm.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_PERIOD);
		addSetting(Setting.options("speed", "Speed", "Fast", "Instant", "Fast", "Slow"));
	}

	@Override
	public void onTick(Minecraft client) {
		// The actual work is done by FastPlaceMixin hooked into Minecraft#tick.
		// No per-tick logic needed here — the mixin reads our settings directly.
	}
}

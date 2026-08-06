package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * ReachAssist — lets you hit mobs and reach blocks about 0.9 blocks
 * further than normal.  Works on vanilla and most servers that don't
 * aggressively validate reach.
 */
public class ReachAssistModule extends Module {
	public ReachAssistModule() {
		super("ReachAssist",
				"Extends your reach by about 0.9 blocks so you can hit things a little further away.",
				Keyboard.KEY_C);
	}

	@Override
	public void onTick(MinecraftClient client) {
		// The actual reach extension is done by the PlayerEntityMixin
		// (getReachDistance).  This module just controls the toggle.
	}
}

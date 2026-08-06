package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/**
 * VelocityAssist — softens knockback so getting hit doesn't send you
 * flying.  60 % horizontal reduction, 30 % vertical — you still take
 * knockback (full blocking looks robotic to anti-cheat), just less.
 */
public class VelocityAssistModule extends Module {
	public VelocityAssistModule() {
		super("VelocityAssist",
				"Softens knockback — you still get pushed, just not as far.",
				Keyboard.KEY_H);
	}

	@Override
	public void onTick(MinecraftClient client) {
		// Velocity dampening is done by the PlayerEntityMixin
		// (setVelocity).  This module just controls the toggle.
	}
}

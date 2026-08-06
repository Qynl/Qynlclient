package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/** Fullbright — sets gamma to maximum so you can always see in the dark. */
public class FullbrightModule extends Module {
	private double prevGamma = 0.5;

	public FullbrightModule() {
		super("Fullbright", "Lights up the world so darkness is never a problem.",
				Keyboard.KEY_G);
	}

	@Override
	public void onEnable() {
		prevGamma = MinecraftClient.getInstance().options.gamma;
	}

	@Override
	public void onTick(MinecraftClient client) {
		client.options.gamma = 100.0;
	}

	@Override
	public void onDisable() {
		MinecraftClient.getInstance().options.gamma = prevGamma;
	}
}

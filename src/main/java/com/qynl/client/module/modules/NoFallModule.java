package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * NoFall — stops you from taking fall damage, so one missed jump never
 * kills you. A huge relief for players who find platforming hard.
 */
public class NoFallModule extends Module {
	public NoFallModule() {
		super("NoFall", "Prevents fall damage — a missed jump never kills you.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_X);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		// Reset the fall distance before it can build up into damage.
		if (client.player.fallDistance > 2.5F) {
			client.player.fallDistance = 0.0F;
		}
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * AntiDrown — when your air runs low underwater, QynlClient swims you up
 * to the surface automatically. You never have to fight the controls to
 * break the surface in time, and you can explore the water safely.
 */
public class AntiDrownModule extends Module {
	private boolean forcingJump = false;

	public AntiDrownModule() {
		super("AntiDrown", "Swims you up automatically when your air is running low.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_P);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.options == null) {
			return;
		}
		var player = client.player;
		if (player.isSpectator() || player.getAbilities().instabuild) {
			release(client);
			return;
		}

		int air = player.getAirSupply();
		int max = player.getMaxAirSupply();
		if (player.isInWater() && air < max * 0.4F) {
			client.options.keyJump.setDown(true);
			forcingJump = true;
		} else if (forcingJump && air > max * 0.7F) {
			release(client);
		} else if (!player.isInWater()) {
			release(client);
		}
	}

	private void release(Minecraft client) {
		if (forcingJump) {
			client.options.keyJump.setDown(false);
			forcingJump = false;
		}
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			client.options.keyJump.setDown(false);
		}
		forcingJump = false;
	}
}

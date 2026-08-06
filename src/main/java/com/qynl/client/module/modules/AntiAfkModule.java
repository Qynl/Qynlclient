package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class AntiAfkModule extends Module {
	private int ticks = 0;

	public AntiAfkModule() {
		super("AntiAFK", "Gently turn your view now and then so servers do not kick you for being idle.", Category.ASSIST);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}
		LocalPlayer player = client.player;
		// Only act when the player is truly idle.
		if (player.input.forwardImpulse != 0.0F || player.input.leftImpulse != 0.0F
				|| player.input.jumping || player.isUsingItem()) {
			ticks = 0;
			return;
		}
		ticks++;
		if (ticks >= 800) { // every 40 seconds
			ticks = 0;
			player.setYRot(player.getYRot() + 20.0F);
		}
	}
}

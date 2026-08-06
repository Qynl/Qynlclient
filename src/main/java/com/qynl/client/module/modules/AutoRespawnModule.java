package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

public class AutoRespawnModule extends Module {
	public AutoRespawnModule() {
		super("AutoRespawn", "Respawn instantly when you die.", Category.ASSIST);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (client.screen instanceof DeathScreen && client.player.isDeadOrDying()) {
			client.player.respawn();
			client.setScreen(null);
		}
	}
}

package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

/** Always sprint while moving forward — no double-tapping W needed. */
public class AutoSprintModule extends Module {
	public AutoSprintModule() {
		super("AutoSprint", "Always sprint while moving forward — no double-tap needed.",
				Keyboard.KEY_V);
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null) return;
		if (client.player.isSprinting()) return;

		boolean moving = client.player.input.movementForward > 0.0F
				|| client.player.input.movementSideways != 0.0F;
		if (moving && client.player.getFoodStats().getFoodLevel() > 6
				&& !client.player.isSneaking() && !client.player.isUsingItem()) {
			client.player.setSprinting(true);
		}
	}
}

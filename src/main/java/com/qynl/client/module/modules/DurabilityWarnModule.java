package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

public class DurabilityWarnModule extends Module {
	private boolean warned = false;

	public DurabilityWarnModule() {
		super("DurabilityWarn", "Warn with a sound and a HUD flash when your tool is about to break.", Category.ASSIST);
	}

	public boolean isWarning(Minecraft client) {
		ItemStack stack = mainHand(client);
		if (stack == null || !stack.isDamageableItem()) {
			return false;
		}
		int left = stack.getMaxDamage() - stack.getDamageValue();
		return left <= 8;
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) {
			return;
		}
		boolean warning = isWarning(client);
		if (warning && !warned) {
			warned = true;
			if (client.getSoundManager() != null) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ANVIL_LAND, 1.0F));
			}
		} else if (!warning) {
			warned = false;
		}
	}

	private ItemStack mainHand(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		return client.player.getMainHandItem();
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * ChestStealer — grabs every item from a chest for you with a single open.
 * Helps players who find click-dragging stacks into their inventory
 * difficult or tiring; just open a chest and it empties itself.
 */
public class ChestStealerModule extends Module {
	private static final int CLICK_DELAY = 3;
	private int cooldown = 0;

	public ChestStealerModule() {
		super("ChestStealer", "Open a chest and it automatically takes everything for you.",
				Category.UTILITY);
		bindKey(GLFW.GLFW_KEY_T);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.gameMode == null) {
			return;
		}
		boolean isChest = client.player.containerMenu instanceof ChestMenu
				|| client.player.containerMenu instanceof ShulkerBoxMenu;
		if (!isChest || client.player.isDeadOrDying()) {
			cooldown = 0;
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		var menu = client.player.containerMenu;
		// Chests append their slots before the player's 36 inventory slots.
		int chestSlots = menu.slots.size() - 36;
		if (chestSlots <= 0) {
			return;
		}
		for (int i = 0; i < chestSlots; i++) {
			Slot slot = menu.getSlot(i);
			if (slot.hasItem()) {
				client.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.QUICK_MOVE, client.player);
				cooldown = CLICK_DELAY;
				return;
			}
		}
	}
}

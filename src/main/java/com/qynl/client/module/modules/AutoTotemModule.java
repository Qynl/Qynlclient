package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * AutoTotem — if you have a Totem of Undying in your inventory and your
 * offhand is empty, it is moved into your offhand for you. Totems save you
 * from death, but only work while held — many players (especially those with
 * limited hand dexterity or slow reactions) simply never manage to equip one
 * in time. This does it automatically, quietly, one totem at a time.
 */
public class AutoTotemModule extends Module {
	private int cooldown = 0;

	public AutoTotemModule() {
		super("AutoTotem", "Keeps a Totem of Undying in your offhand automatically when you have one.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F4);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.player.isDeadOrDying()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		// Nothing to do if a totem is already in the offhand.
		if (client.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		int totemSlot = -1;
		for (int i = 0; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.TOTEM_OF_UNDYING)) {
				totemSlot = i;
				break;
			}
		}
		if (totemSlot < 0) {
			return;
		}

		// Player inventory container slots: hotbar = 36-44, main = 9-35, offhand = 45.
		int fromSlot = totemSlot < 9 ? 36 + totemSlot : totemSlot;
		int offhandSlot = 45;
		int containerId = client.player.containerMenu.containerId;

		client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, client.player);
		client.gameMode.handleInventoryMouseClick(containerId, offhandSlot, 0, ClickType.PICKUP, client.player);
		if (!client.player.containerMenu.getCarried().isEmpty()) {
			client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, client.player);
		}
		cooldown = 20;
	}
}

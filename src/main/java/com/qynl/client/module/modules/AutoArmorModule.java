package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * AutoArmor — equips the best armor found in your inventory for you.
 * Great for players who find opening the inventory and dragging pieces
 * of armor fiddly: it just keeps you safe automatically.
 */
public class AutoArmorModule extends Module {
	private int cooldown = 0;

	public AutoArmorModule() {
		super("AutoArmor", "Automatically equip the best armor you have — no fiddly inventory dragging.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_J);
		addSetting(Setting.range("swapDelay", "Swap delay", 20.0, 5, 40, 5, "t"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			return;
		}
		if (client.screen != null || client.player.isDeadOrDying() || client.player.isUsingItem()) {
			return;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}

		Inventory inventory = client.player.getInventory();
		// Best piece found for each armor index (0 = helmet, 1 = chest, 2 = legs, 3 = boots).
		int[] bestIndex = new int[]{-1, -1, -1, -1};
		float[] bestScore = new float[]{-1.0F, -1.0F, -1.0F, -1.0F};

		for (int i = 0; i < 36; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) {
				continue;
			}
			EquipmentSlot slot = armor.getEquipmentSlot();
			int idx = switch (slot) {
				case HEAD -> 0;
				case CHEST -> 1;
				case LEGS -> 2;
				case FEET -> 3;
				default -> -1;
			};
			if (idx < 0) {
				continue;
			}
			float score = armor.getDefense() + armor.getToughness() * 0.5F;
			if (score > bestScore[idx]) {
				bestScore[idx] = score;
				bestIndex[idx] = i;
			}
		}

		for (int idx = 0; idx < 4; idx++) {
			if (bestIndex[idx] < 0) {
				continue;
			}
			// Vanilla's armor list is ordered [FEET, LEGS, CHEST, HEAD], so the
			// equipped piece for our HEAD..FEET index (0..3) lives at 3 - idx.
			ItemStack equipped = inventory.getArmor(3 - idx);
			float currentScore = 0.0F;
			if (equipped.getItem() instanceof ArmorItem armor) {
				currentScore = armor.getDefense() + armor.getToughness() * 0.5F;
			}
			if (bestScore[idx] <= currentScore) {
				continue;
			}

			// Player inventory container slots: hotbar = 36-44, main = 9-35, armor = 5-8.
			int fromSlot = bestIndex[idx] < 9 ? 36 + bestIndex[idx] : bestIndex[idx];
			int toSlot = 5 + idx;
			int containerId = client.player.containerMenu.containerId;

			client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, client.player);
			client.gameMode.handleInventoryMouseClick(containerId, toSlot, 0, ClickType.PICKUP, client.player);
			if (!client.player.containerMenu.getCarried().isEmpty()) {
				client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, client.player);
			}
			cooldown = (int) getDoubleSetting("swapDelay");
			return;
		}
	}
}

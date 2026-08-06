package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public class AutoEatModule extends Module {
	private boolean forcingUse = false;

	public AutoEatModule() {
		super("AutoEat", "Eat the best food in your hotbar automatically when you are hungry.", Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_U);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.screen != null) {
			return;
		}
		LocalPlayer player = client.player;

		// Let go of the use button once the player has finished eating.
		if (forcingUse && !player.isUsingItem()) {
			releaseUse(client);
		}
		if (player.isDeadOrDying() || player.isSpectator() || player.isCreative() || player.isPassenger()) {
			return;
		}
		// Never steal the right-click while the player is using it for something else.
		if (client.options.keyUse.isDown()) {
			return;
		}
		if (player.isUsingItem() || player.getFoodData().getFoodLevel() >= 18) {
			return;
		}

		Inventory inventory = player.getInventory();
		int bestSlot = -1;
		float bestScore = 0.0F;
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food == null) {
				continue;
			}
			float score = food.nutrition() * (1.0F + 2.0F * food.saturation());
			if (score > bestScore) {
				bestScore = score;
				bestSlot = i;
			}
		}
		if (bestSlot < 0) {
			return;
		}
		if (inventory.selected != bestSlot) {
			inventory.selected = bestSlot;
		}
		ItemStack mainHand = player.getMainHandItem();
		if (mainHand.get(DataComponents.FOOD) == null) {
			return;
		}

		client.options.keyUse.setDown(true);
		forcingUse = true;
	}

	private void releaseUse(Minecraft client) {
		if (client.options != null) {
			client.options.keyUse.setDown(false);
		}
		forcingUse = false;
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		releaseUse(client);
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.lwjgl.glfw.GLFW;

/**
 * AutoPotion — when your health drops low, QynlClient drinks a healing or
 * regeneration potion from your hotbar for you. Perfect for players who
 * cannot react quickly enough to open the inventory and chug a potion
 * mid-fight.
 */
public class AutoPotionModule extends Module {
	private boolean forcingUse = false;

	public AutoPotionModule() {
		super("AutoPotion", "Drinks a healing potion automatically when you are low on health.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.screen != null) {
			return;
		}
		LocalPlayer player = client.player;

		// Let go of the use button once the player has finished drinking.
		if (forcingUse && !player.isUsingItem()) {
			releaseUse(client);
		}
		if (player.isDeadOrDying() || player.isSpectator() || player.isCreative() || player.isPassenger()) {
			return;
		}
		// Never steal the right-click while the player is using it for something else.
		if (client.options.keyUse.isDown() || player.isUsingItem()) {
			return;
		}

		float health = player.getHealth();
		if (health >= player.getMaxHealth() * 0.4F) {
			return;
		}

		Inventory inventory = player.getInventory();
		int bestSlot = -1;
		for (int i = 0; i < 9; i++) {
			if (isHealingPotion(inventory.getItem(i))) {
				bestSlot = i;
				break;
			}
		}
		if (bestSlot < 0) {
			return;
		}
		if (inventory.selected != bestSlot) {
			inventory.selected = bestSlot;
		}
		if (!isHealingPotion(player.getMainHandItem())) {
			return;
		}

		client.options.keyUse.setDown(true);
		forcingUse = true;
	}

	private boolean isHealingPotion(ItemStack stack) {
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) {
			return false;
		}
		for (MobEffectInstance effect : contents.getAllEffects()) {
			Holder<MobEffect> holder = effect.getEffect();
			MobEffect value = holder.value();
			if (value == MobEffects.HEAL.value() || value == MobEffects.REGENERATION.value()) {
				return true;
			}
		}
		return false;
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

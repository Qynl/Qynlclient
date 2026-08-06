package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import com.qynl.client.module.Setting;
import org.lwjgl.glfw.GLFW;

/**
 * AutoSword — when you attack a mob, QynlClient automatically switches to the
 * strongest melee weapon in your hotbar. Great for players who cannot switch
 * hotbar slots quickly (or at all) mid-fight: you keep swinging the attack
 * button and the client makes sure your best sword or axe is in your hand.
 */
public class AutoSwordModule extends Module {
	private double prevDamage = 0.0;

	public AutoSwordModule() {
		super("AutoSword", "Automatically switches to your strongest weapon when you attack a mob.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F8);
		addSetting(Setting.range("minAdvantage", "Min advantage", 0.5, 0.0, 3.0, 0.5));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (!client.options.keyAttack.isDown()) {
			return;
		}
		// Only help while actually aiming at a living creature.
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
			return;
		}
		if (!(client.hitResult instanceof EntityHitResult entityHit)
				|| !(entityHit.getEntity() instanceof LivingEntity)) {
			return;
		}

		Inventory inventory = client.player.getInventory();
		int bestSlot = inventory.selected;
		float bestDamage = attackDamage(inventory.getItem(bestSlot));
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			float damage = attackDamage(stack);
			if (damage > bestDamage) {
				bestDamage = damage;
				bestSlot = i;
			}
		}
		double minAdv = getDoubleSetting("minAdvantage");
		if (bestSlot != inventory.selected && (bestDamage - prevDamage) >= minAdv) {
			inventory.selected = bestSlot;
			prevDamage = bestDamage;
		}
	}

	/** Total melee damage this stack would deal: base fist damage + item modifiers. */
	private float attackDamage(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0F;
		}
		final float[] damage = {1.0F};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
			if (holder.is(Attributes.ATTACK_DAMAGE)) {
				damage[0] += (float) modifier.amount();
			}
		});
		return damage[0];
	}
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import org.lwjgl.glfw.GLFW;

/**
 * Throwpot — press the bind to throw/drink/eat your best healing item.
 * Prioritizes splash potions of healing, then regeneration, then food.
 */
public class ThrowpotModule extends Module {
    private boolean used = false;
    /** Ticks the use key is held after triggering (food/drinkables). */
    private int useHoldTicks = 0;

    public ThrowpotModule() {
        super("Throwpot", "Press the bind to throw/drink/eat your best healing item instantly.",
                Category.UTILITY);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("healthPct", "Use below", 60.0, 20, 90, 5, "%"));
    }

    @Override
    public void onEnable() {
        used = false;
        useHoldTicks = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null) {
            used = false;
            return;
        }
        var player = client.player;
        if (player.isDeadOrDying() || player.isSpectator() || player.isCreative()) {
            used = false;
            return;
        }		// Release the use key a couple of seconds after triggering so it is
		// never left logically held (which would hijack the player's right-click).
		if (useHoldTicks > 0) {
			if (--useHoldTicks <= 0) {
				client.options.keyUse.setDown(false);
			}
			return;
		}

		if (used) return;

		float healthPct = player.getHealth() / player.getMaxHealth() * 100f;
        if (healthPct > getDoubleSetting("healthPct")) return;

        Inventory inv = player.getInventory();
        int bestSlot = -1;
        float bestScore = 0;

        // 1. Splash/lingering healing potions (best)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            float score = healingScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        if (bestSlot < 0) {
            // 2. Try food as fallback
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getItem(i);
                var food = stack.get(DataComponents.FOOD);
                if (food != null) {
                    float score = food.nutrition() + food.saturation() * 2;
                    if (score > bestScore) {
                        bestScore = score;
                        bestSlot = i;
                    }
                }
            }
        }

        if (bestSlot < 0) return;

        int prevSlot = inv.selected;
        if (inv.selected != bestSlot) {
            inv.selected = bestSlot;
        }

        // Use the item
        ItemStack toUse = player.getMainHandItem();
        boolean isThrowable = toUse.get(DataComponents.POTION_CONTENTS) != null;

        if (isThrowable) {
            client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            // For throwable potions, this throws immediately
            inv.selected = prevSlot;		} else {
			// For food/drinkable, hold use to consume — released after ~1.6 s.
			client.options.keyUse.setDown(true);
			useHoldTicks = 32;
		}

		used = true;
	}

    private float healingScore(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return 0;

        float score = 0;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            Holder<MobEffect> holder = effect.getEffect();
            MobEffect value = holder.value();
            if (value == MobEffects.HEAL.value()) {
                score += 20 * (effect.getAmplifier() + 1);
            } else if (value == MobEffects.REGENERATION.value()) {
                score += 10 * (effect.getAmplifier() + 1);
            } else if (value == MobEffects.ABSORPTION.value()) {
                score += 8 * (effect.getAmplifier() + 1);
            }
        }
        return score;
    }	@Override
	public void onDisable() {
		used = false;
		useHoldTicks = 0;
		Minecraft client = Minecraft.getInstance();
		if (client.options != null) {
			client.options.keyUse.setDown(false);
		}
	}
}
package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FoodItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * AutoEat — eats the best food in your hotbar automatically when you are
 * hungry. Switches to the food slot and uses the item through the normal
 * interaction path (the same one right-click uses), so the server sees a
 * player who simply grabbed something to eat. The original hotbar slot is
 * restored once the meal is done.
 */
public class AutoEatModule extends Module {
    /** Slot we switched to for eating, or -1 when nothing is being eaten. */
    private int eatenSlot = -1;

    public AutoEatModule() {
        super("AutoEat", "Eat the best food in your hotbar automatically when you are hungry.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_U);
        addSetting(Setting.range("hunger", "Hunger level", 16.0, 6, 20, 1));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (!player.isAlive() || player.abilities.creativeMode || player.hasVehicle()) {
            return;
        }

        PlayerInventory inventory = player.inventory;

        // Finished eating (or stopped): restore the original slot.
        if (eatenSlot >= 0 && !player.isUsingItem()) {
            inventory.selectedSlot = eatenSlot;
            eatenSlot = -1;
        }

        // Never steal the right-click while the player is using it.
        if (client.options.keyUse.isPressed()) {
            return;
        }
        if (player.isUsingItem()) {
            return;
        }
        if (player.getHungerManager().getFoodLevel() >= (int) getDoubleSetting("hunger")) {
            return;
        }

        int bestSlot = -1;
        float bestScore = 0.0F;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.main[i];
            if (stack == null || stack.getItem() == null || !stack.getItem().isFood()) {
                continue;
            }
            FoodItem food = (FoodItem) stack.getItem();
            float score = food.getHungerPoints(stack) * (1.0F + 2.0F * food.getSaturation(stack));
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) {
            return;
        }
        if (eatenSlot < 0) {
            eatenSlot = inventory.selectedSlot;
        }
        if (inventory.selectedSlot != bestSlot) {
            inventory.selectedSlot = bestSlot;
        }
        ItemStack held = inventory.getMainHandStack();
        if (held == null || !held.getItem().isFood()) {
            return;
        }
        client.interactionManager.interactItem(player, client.world, held);
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null && eatenSlot >= 0) {
            client.player.inventory.selectedSlot = eatenSlot;
        }
        eatenSlot = -1;
    }
}

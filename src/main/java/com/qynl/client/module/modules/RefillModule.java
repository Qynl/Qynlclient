package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import org.lwjgl.glfw.GLFW;

/**
 * Refill — refills your hotbar with food and potions from inventory.
 * Press the key or set to auto mode.
 */
public class RefillModule extends Module {
    private int cooldown = 0;

    public RefillModule() {
        super("Refill", "Refills your hotbar with food and potions from your inventory.",
                Category.ASSIST);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("mode", "Mode", "Manual", "Manual", "Auto"));
        addSetting(Setting.range("delay", "Swap delay", 5.0, 2, 15, 1, "t"));
        addSetting(Setting.range("threshold", "Refill at", 3.0, 1, 8, 1, "items"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.gameMode == null) return;
        if (client.player.isDeadOrDying() || client.screen != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        String mode = getStringSetting("mode");
        int threshold = (int) getDoubleSetting("threshold");

        // Auto mode
        if ("Auto".equals(mode)) {
            refillHotbar(client, threshold);
        }
        // Manual mode: press the toggle key to refill once
        // (handled by onEnable calling refill once in manual mode)
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if ("Manual".equals(getStringSetting("mode"))) {
            refillHotbar(client, (int) getDoubleSetting("threshold"));
        }
    }

    private void refillHotbar(Minecraft client, int threshold) {
        var player = client.player;
        Inventory inv = player.getInventory();

        // Check slots 0-8 (hotbar): if empty or stack is low, try to refill
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            ItemStack stack = inv.getItem(hotbarSlot);
            if (stack.isEmpty()) continue;

            boolean shouldRefill = (stack.getMaxStackSize() > 1 && stack.getCount() <= threshold);
            if (!shouldRefill) continue;

            // Find a matching item in inventory (slots 9-35)
            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack invStack = inv.getItem(invSlot);
                if (invStack.isEmpty()) continue;
                if (!ItemStack.isSameItemSameComponents(stack, invStack)) continue;

                int fromSlot = invSlot;
                int toSlot = hotbarSlot < 9 ? 36 + hotbarSlot : hotbarSlot;
                int containerId = player.containerMenu.containerId;

                // Swap
                client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, player);
                client.gameMode.handleInventoryMouseClick(containerId, toSlot, 0, ClickType.PICKUP, player);
                if (!player.containerMenu.getCarried().isEmpty()) {
                    client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, player);
                }

                cooldown = (int) getDoubleSetting("delay");
                return; // One swap per tick to avoid spam
            }
        }

        // Also fill empty hotbar slots with food/potions
        for (int hotbarSlot = 0; hotbarSlot < 9; hotbarSlot++) {
            if (!inv.getItem(hotbarSlot).isEmpty()) continue;

            for (int invSlot = 9; invSlot < 36; invSlot++) {
                ItemStack invStack = inv.getItem(invSlot);
                if (invStack.isEmpty()) continue;
                if (isFood(invStack) || isPotion(invStack)) {
                    int fromSlot = invSlot;
                    int toSlot = 36 + hotbarSlot;
                    int containerId = player.containerMenu.containerId;

                    client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, player);
                    client.gameMode.handleInventoryMouseClick(containerId, toSlot, 0, ClickType.PICKUP, player);
                    if (!player.containerMenu.getCarried().isEmpty()) {
                        client.gameMode.handleInventoryMouseClick(containerId, fromSlot, 0, ClickType.PICKUP, player);
                    }

                    cooldown = (int) getDoubleSetting("delay");
                    return;
                }
            }
        }
    }

    private boolean isFood(ItemStack stack) {
        return stack.get(DataComponents.FOOD) != null
                && stack.getItem() != Items.CHORUS_FRUIT
                && stack.getItem() != Items.ROTTEN_FLESH
                && stack.getItem() != Items.SPIDER_EYE
                && stack.getItem() != Items.POISONOUS_POTATO;
    }

    private boolean isPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null;
    }
}
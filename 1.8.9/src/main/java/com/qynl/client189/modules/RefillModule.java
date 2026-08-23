package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import org.lwjgl.input.Keyboard;

/**
 * Refill — refills your hotbar with food and potions from the inventory.
 * Uses the same shift-click path a real player uses (one item per tick,
 * with a small delay), so the server only ever sees ordinary inventory
 * clicks. Modes: food only, potions only, or both.
 */
public class RefillModule extends Module {
    private int cooldown = 0;

    public RefillModule() {
        super("Refill", "Refills your hotbar with food and potions from the inventory.", Category.UTILITY);
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("mode", "Mode", "Food", "Food", "Potion", "All"));
        addSetting(Setting.range("delay", "Delay", 2.0, 1, 6, 1, "t"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        PlayerInventory inv = client.player.inventory;

        // Find a healing item in the main inventory (slots 9–35, not hotbar).
        int fromSlot = -1;
        for (int i = 9; i < 36; i++) {
            ItemStack stack = inv.main[i];
            if (matches(stack)) {
                fromSlot = i;
                break;
            }
        }
        if (fromSlot < 0) {
            return;
        }

        // Find an empty hotbar slot to fill.
        int toSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.main[i] == null) {
                toSlot = i;
                break;
            }
        }
        if (toSlot < 0) {
            return;
        }

        // Shift-click: vanilla moves a main-inventory stack into the first
        // empty hotbar slot — exactly what a player does to refill.
        int syncId = client.player.playerScreenHandler.syncId;
        client.interactionManager.clickSlot(syncId, fromSlot, 0, 1, client.player);
        cooldown = (int) getDoubleSetting("delay");
    }

    private boolean matches(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        boolean isPotion = stack.getItem() instanceof PotionItem;
        boolean isFood = stack.getItem().isFood();
        switch (getStringSetting("mode")) {
            case "Potion": return isPotion;
            case "All":    return isPotion || isFood;
            default:       return isFood;
        }
    }
}

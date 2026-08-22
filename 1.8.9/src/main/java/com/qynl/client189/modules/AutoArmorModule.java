package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * AutoArmor — equips the best armor found in your inventory automatically.
 * Pieces are compared by their protection value and moved into the armor
 * slots with ordinary shift-click inventory actions, exactly like a player
 * equipping gear by hand (just faster and never fumbling).
 */
public class AutoArmorModule extends Module {
    private int cooldown = 0;

    public AutoArmorModule() {
        super("AutoArmor", "Automatically equip the best armor you have — no fiddly inventory dragging.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_J);
        addSetting(Setting.range("swapDelay", "Swap delay", 20.0, 5, 40, 5, "t"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive() || client.player.isUsingItem()) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        PlayerInventory inventory = client.player.inventory;
        // Best piece for each armor index: 0 = head, 1 = chest, 2 = legs, 3 = feet.
        int[] bestIndex = new int[]{-1, -1, -1, -1};
        float[] bestScore = new float[]{-1.0F, -1.0F, -1.0F, -1.0F};

        for (int i = 0; i < 36; i++) {
            ItemStack stack = inventory.main[i];
            if (stack == null || !(stack.getItem() instanceof ArmorItem)) {
                continue;
            }
            ArmorItem armor = (ArmorItem) stack.getItem();
            int idx = armor.slot; // 0 = head, 1 = chest, 2 = legs, 3 = feet
            if (idx < 0 || idx > 3) {
                continue;
            }
            float score = armor.protection;
            if (score > bestScore[idx]) {
                bestScore[idx] = score;
                bestIndex[idx] = i;
            }
        }

        for (int idx = 0; idx < 4; idx++) {
            if (bestIndex[idx] < 0) {
                continue;
            }
            // Vanilla's armor array is ordered [feet, legs, chest, head].
            ItemStack equipped = inventory.armor[3 - idx];
            float currentScore = 0.0F;
            if (equipped != null && equipped.getItem() instanceof ArmorItem) {
                currentScore = ((ArmorItem) equipped.getItem()).protection;
            }
            if (bestScore[idx] <= currentScore) {
                continue;
            }

            // Player inventory container slots: hotbar = 36–44, main = 9–35, armor = 5–8.
            int fromSlot = bestIndex[idx] < 9 ? 36 + bestIndex[idx] : bestIndex[idx];
            int toSlot = 5 + idx;
            int syncId = client.player.playerScreenHandler.syncId;

            // Shift-click moves the piece into the armor slot (and swaps a
            // worse equipped piece back out).
            client.interactionManager.clickSlot(syncId, fromSlot, 0, 1, client.player);
            cooldown = (int) getDoubleSetting("swapDelay");
            return;
        }
    }
}

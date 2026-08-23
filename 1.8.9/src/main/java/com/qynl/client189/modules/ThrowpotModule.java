package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import org.lwjgl.input.Keyboard;

/**
 * Throwpot — press the bind to throw, drink or eat your best healing item.
 * The item is pulled from the inventory into a hotbar slot (normal slot
 * switch + shift-click) and used through the vanilla item-use path, so the
 * server sees ordinary item use. Modes: Dynamic picks the best of all,
 * Splash/Drink/Eat force a category.
 */
public class ThrowpotModule extends Module {
    private int cooldown = 0;

    public ThrowpotModule() {
        super("Throwpot", "Press the bind to throw / drink / eat your best healing item.", Category.UTILITY);
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("mode", "Mode", "Dynamic", "Dynamic", "Splash", "Drink", "Eat"));
        addSetting(Setting.range("cooldown", "Cooldown", 10.0, 2, 40, 1, "t"));
    }

    /** The bind triggers the throw instead of toggling the module. */
    @Override
    public boolean handleKey() {
        KeyBinding kb = getKeyBinding();
        if (kb != null && kb.wasPressed()) {
            use(MinecraftClient.getInstance());
            return false;
        }
        return false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    private void use(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive() || client.player.isUsingItem()) {
            return;
        }
        if (cooldown > 0) {
            return;
        }

        String mode = getStringSetting("mode");
        PlayerInventory inv = client.player.inventory;

        // 1. Find the best healing item anywhere (hotbar first, then inventory).
        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inv.main.length; i++) {
            ItemStack stack = inv.main[i];
            int score = score(stack, mode);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot < 0 || bestScore <= 0) {
            return;
        }

        // 2. Move it into the selected hotbar slot if it is not there yet.
        if (bestSlot >= 9) {
            int syncId = client.player.playerScreenHandler.syncId;
            client.interactionManager.clickSlot(syncId, bestSlot, 0, 1, client.player);
            // It lands in the first empty hotbar slot; grab it next tick.
            return;
        }

        // 3. Select the slot and use the item (throw / drink / eat).
        inv.selectedSlot = bestSlot;
        ItemStack held = inv.getMainHandStack();
        if (held == null || held.getItem() == null) {
            return;
        }
        client.interactionManager.interactItem(client.player, client.world, held);
        client.player.swingHand();
        cooldown = (int) getDoubleSetting("cooldown");
    }

    /** Higher = better healing item for the selected mode; 0 = not usable. */
    private int score(ItemStack stack, String mode) {
        if (stack == null || stack.getItem() == null) {
            return 0;
        }
        // 1.8.9 has a single PotionItem; splash variants are flagged by
        // their damage value (PotionItem.isThrowable).
        boolean splash = stack.getItem() instanceof PotionItem
                && PotionItem.isThrowable(stack.getDamage());
        boolean potion = stack.getItem() instanceof PotionItem;
        boolean food = stack.getItem().isFood();

        boolean wantSplash = "Splash".equals(mode) || "Dynamic".equals(mode);
        boolean wantDrink = "Drink".equals(mode) || "Dynamic".equals(mode);
        boolean wantEat = "Eat".equals(mode) || "Dynamic".equals(mode);

        if (splash && wantSplash) return 300;
        if (potion && wantDrink) return 200;
        if (food && wantEat) {
            // Golden apples are the best food for healing.
            if (stack.getItem() == Items.GOLDEN_APPLE) return 150;
            return 100;
        }
        return 0;
    }
}

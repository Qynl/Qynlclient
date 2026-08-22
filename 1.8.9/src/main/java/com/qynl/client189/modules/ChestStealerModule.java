package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.slot.Slot;
import net.minecraft.screen.ScreenHandler;
import org.lwjgl.input.Keyboard;

/**
 * ChestStealer — open a chest (or any container) and it automatically takes
 * everything for you, one shift-click at a time with a small human-like
 * pause between clicks. Helps players who find click-dragging stacks into
 * their inventory difficult or tiring.
 */
public class ChestStealerModule extends Module {
    private static final int CLICK_DELAY = 3;
    private int cooldown = 0;

    public ChestStealerModule() {
        super("ChestStealer", "Open a chest and it automatically takes everything for you.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            return;
        }
        if (!(client.currentScreen instanceof HandledScreen) || !client.player.isAlive()) {
            cooldown = 0;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        HandledScreen screen = (HandledScreen) client.currentScreen;
        ScreenHandler handler = screen.screenHandler;
        PlayerInventory playerInventory = client.player.inventory;
        int syncId = handler.syncId;

        // Container slots are the ones that do not belong to the player.
        for (Slot slot : handler.slots) {
            if (slot.inventory == playerInventory) {
                continue;
            }
            if (slot.hasStack()) {
                // Shift-click (action 1) moves the stack into the inventory.
                client.interactionManager.clickSlot(syncId, slot.id, 0, 1, client.player);
                cooldown = CLICK_DELAY;
                return;
            }
        }
    }
}

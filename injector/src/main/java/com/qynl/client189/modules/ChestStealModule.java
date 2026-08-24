package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.lwjgl.input.Keyboard;

/**
 * ChestStealer — open a chest (or any container) and it automatically takes
 * everything for you, one shift-click at a time with a small human-like
 * pause between clicks. Helps players who find click-dragging stacks into
 * their inventory difficult or tiring.
 *
 * <p>Configurable: the click delay can be tuned, an optional
 * <i>valuables-only</i> mode skips common junk blocks (cobblestone, dirt,
 * gravel, sand, wood…), and <i>auto-close</i> shuts the container the moment
 * it is emptied.</p>
 */
public class ChestStealModule extends Module {
    private int cooldown = 0;

    public ChestStealModule() {
        super("ChestSteal", "Open a chest and it automatically takes everything for you.",
                Category.UTILITY);
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("delay", "Click delay", 3.0, 1, 8, 1, "t"));
        addSetting(Setting.options("valuables", "Only valuables", "Off", "Off", "On"));
        addSetting(Setting.options("autoClose", "Auto close", "Off", "Off", "On"));
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

        boolean valuablesOnly = "On".equals(getStringSetting("valuables"));

        // Container slots are the ones that do not belong to the player.
        for (Slot slot : handler.slots) {
            if (slot.inventory == playerInventory) {
                continue;
            }
            if (slot.hasStack()) {
                if (valuablesOnly && isJunk(slot.getStack())) {
                    continue;
                }
                // Shift-click (action 1) moves the stack into the inventory.
                client.interactionManager.clickSlot(syncId, slot.id, 0, 1, client.player);
                cooldown = (int) getDoubleSetting("delay");
                return;
            }
        }

        // Nothing left to steal — optionally close the container.
        if ("On".equals(getStringSetting("autoClose"))) {
            client.player.closeHandledScreen();
            client.openScreen(null);
        }
    }

    /** Common junk blocks most players do not want to haul out of a chest. */
    private boolean isJunk(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return true;
        }
        String name = stack.getItem().getTranslationKey();
        return name != null && (name.contains("cobblestone")
                || name.contains("dirt")
                || name.contains("gravel")
                || name.contains("sand")
                || name.contains("log")
                || name.contains("planks")
                || name.contains("rotten_flesh")
                || name.contains("stick")
                || name.contains("sapling")
                || name.contains("bone")
                || name.contains("flint")
                || name.contains("string"));
    }
}

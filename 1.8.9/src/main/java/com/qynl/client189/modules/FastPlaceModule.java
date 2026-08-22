package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * FastPlace — shortens only the local block-placement cooldown.
 *
 * <p>Silent means this does not automate clicks, move the camera, or alter
 * outgoing packets. It simply lets vanilla process another held right-click
 * sooner when the server accepts it.</p>
 */
public class FastPlaceModule extends Module {
    private static FastPlaceModule instance;

    public FastPlaceModule() {
        super("FastPlace", "Shortens the local block-placement delay without visual or packet changes.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("speed", "Speed", "Fast", "Legit", "Fast", "Instant"));
    }

    public static FastPlaceModule getInstance() {
        return instance;
    }

    /**
     * Returns the maximum local cooldown allowed for the current mode, or -1
     * when the module should not touch the client's cooldown this tick.
     */
    public static int maxBlockPlaceDelay(MinecraftClient client) {
        if (instance == null || !instance.isEnabled() || client == null || client.player == null) {
            return -1;
        }

        ItemStack held = client.player.inventory.getMainHandStack();
        if (held == null || !(held.getItem() instanceof BlockItem)) {
            return -1;
        }

        String speed = instance.getStringSetting("speed");
        if ("Instant".equals(speed)) {
            return 0;
        }
        if ("Legit".equals(speed)) {
            return 2;
        }
        return 1;
    }
}

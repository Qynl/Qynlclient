package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

/**
 * DurabilityWarnModule for 1.8.9 — warns with a sound and a HUD flash when a
 * tool is about to break. Mirrors the 1.21.1 DurabilityWarn module.
 */
public class DurabilityWarnModule extends Module {
    private boolean warned = false;

    public DurabilityWarnModule() {
        super("DurabilityWarn", "Warn with a sound and a HUD flash when your tool is about to break.",
                Category.ASSIST);
    }

    public boolean isWarning(MinecraftClient client) {
        if (client.player == null) {
            return false;
        }
        ItemStack stack = client.player.getMainHandStack();
        if (stack == null || !stack.isDamageable()) {
            return false;
        }
        int left = stack.getMaxDamage() - stack.getDamage();
        return left <= 8;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        boolean warning = isWarning(client);
        if (warning && !warned) {
            warned = true;
            client.world.playSound(client.player.x, client.player.y, client.player.z,
                    "random.anvil_land", 1.0F, 1.0F, false);
        } else if (!warning) {
            warned = false;
        }
    }
}

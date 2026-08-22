package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import org.lwjgl.input.Keyboard;

/** AutoRespawn — respawns instantly when you die, no clicking required. */
public class AutoRespawnModule extends Module {

    public AutoRespawnModule() {
        super("AutoRespawn", "Respawn instantly when you die — no need to find the button.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_NONE);
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        if (client.currentScreen instanceof DeathScreen) {
            client.player.requestRespawn();
            client.openScreen(null);
        }
    }
}

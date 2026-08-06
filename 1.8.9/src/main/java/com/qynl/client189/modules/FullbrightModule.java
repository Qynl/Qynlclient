package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

public class FullbrightModule extends Module {
    private float oldGamma;

    public FullbrightModule() {
        super("Fullbright", "Makes everything bright so you can see in the dark.", Category.RENDER);
        bindKey(Keyboard.KEY_L);
    }

    @Override
    public void onEnable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) { oldGamma = client.options.gamma; client.options.gamma = 100.0F; }
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) client.options.gamma = oldGamma;
    }
}

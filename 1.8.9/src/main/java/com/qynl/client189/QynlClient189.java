package com.qynl.client189;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;

public class QynlClient189 implements ClientModInitializer {
    public static final String MOD_ID = "qynlclient189";
    public static final String VERSION = "1.0.0";

    private static QynlClient189 instance;
    private final ModuleManager moduleManager = new ModuleManager();
    private QynlClientConfig config;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = QynlClientConfig.load();
        moduleManager.registerDefaults();
        moduleManager.loadFromConfig(config);
        System.out.println("[QynlClient-1.8.9] v" + VERSION + " initialized");
    }

    public void onClientTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && client.world != null) {
            moduleManager.tick(client);
        }
    }

    public static QynlClient189 getInstance() { return instance; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public QynlClientConfig getConfig() { return config; }
    public void openGui() { MinecraftClient.getInstance().openScreen(new ClickGuiScreen()); }
}

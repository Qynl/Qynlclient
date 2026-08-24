package com.qynl.client189;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;

public class QynlClient189 implements ClientModInitializer {
    public static final String MOD_ID = "qynlclient189";
    public static final String VERSION = "1.0.0";

    private static QynlClient189 instance;
    private final ModuleManager moduleManager = new ModuleManager();
    private QynlClientConfig config;
    private KeyBinding clickGuiKey;

    // ── central lifecycle tracking (world change / death / disconnect) ──
    private Object lastWorld = null;
    private boolean lastPlayerAlive = true;

    // ── TPS estimate from the client tick cadence (server lag throttles
    //    the client timer, so tick gaps reflect the server's pace) ──
    private long lastTickTime = -1;
    private double tpsEstimate = 20.0;

    @Override
    public void onInitializeClient() {
        instance = this;
        config = QynlClientConfig.load();
        // Right-Shift opens the ClickGUI (the GUI itself closes on RShift too).
        clickGuiKey = new KeyBinding("key.qynlclient189.clickgui", Keyboard.KEY_RSHIFT, "category.qynlclient189");
        registerKeyBinding(clickGuiKey);
        moduleManager.registerDefaults();
        moduleManager.loadFromConfig(config);
        System.out.println("[Qyn-L 1.8.9] v" + VERSION + " initialized");
    }

    /** Appends a key binding so Minecraft polls it every tick (same pattern as Module.bindKey). */
    private static void registerKeyBinding(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.options != null) {
            KeyBinding[] old = mc.options.keysAll;
            KeyBinding[] next = new KeyBinding[old.length + 1];
            System.arraycopy(old, 0, next, 0, old.length);
            next[old.length] = kb;
            mc.options.keysAll = next;
        }
    }

    public void onClientTick() {
        MinecraftClient client = MinecraftClient.getInstance();

        // ── lifecycle: world change / death / disconnect ───────────────
        boolean died = client.player != null && lastPlayerAlive && !client.player.isAlive();
        boolean worldChanged = client.world != lastWorld;
        if (died || worldChanged) {
            moduleManager.onWorldChange(client);
        }
        if (client.world == null && lastWorld != null) {
            moduleManager.onDisconnect(client);
        }
        lastWorld = client.world;
        lastPlayerAlive = client.player == null || client.player.isAlive();

        // ── TPS estimate ────────────────────────────────────────────────
        long now = System.currentTimeMillis();
        if (lastTickTime > 0) {
            double gap = now - lastTickTime;
            if (gap > 1.0) {
                double tps = Math.min(20.0, 1000.0 / gap);
                tpsEstimate += (tps - tpsEstimate) * 0.2;
            }
        }
        lastTickTime = now;

        if (client.player != null && client.world != null) {
            if (clickGuiKey != null && clickGuiKey.wasPressed()) {
                openGui();
                return;
            }
            moduleManager.tick(client);
        }
    }

    /** Client-side TPS estimate (0–20), for the HUD info widget. */
    public double getTps() { return tpsEstimate; }

    public static QynlClient189 getInstance() { return instance; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public QynlClientConfig getConfig() { return config; }
    public void openGui() { MinecraftClient.getInstance().openScreen(new ClickGuiScreen()); }
}

package com.qynl.client189;

import com.qynl.client189.modules.*;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void registerDefaults() {
        // ── Combat ──────────────────────────────────────────────
        register(new AimAssistModule());
        register(new AutoClickerModule());
        register(new ReachModule());
        register(new VelocityModule());
        register(new SprintModule());
        register(new WTapModule());
        register(new BlockHitModule());
        register(new HindsightModule());
        register(new QynlModule());
        register(new CriticalsModule());
        register(new AegisModule());
        register(new StrafeAssistModule());
        register(new DirectorModule());
        // ── Render ──────────────────────────────────────────────
        register(new SearchModule());
        register(new NameTagsModule());
        register(new TracersModule());
        register(new StorageESPModule());
        register(new FullbrightModule());
        register(new NoHurtCamModule());
        register(new NoViewBobModule());
        register(new ZoomModule());
        register(new EchoModule());
        // ── Utility ─────────────────────────────────────────────
        register(new ClutchModule());
        register(new ScaffoldModule());
        register(new ChestStealModule());
        register(new BlinkModule());
        register(new RefillModule());
        register(new ThrowpotModule());
        register(new VersionAssistModule());
        // ── Other ───────────────────────────────────────────────
        register(new TextGuiModule());
        register(new FriendsModule());
        register(new StreamerModeModule());
    }

    private void register(Module module) { modules.add(module); }

    /** Sets a module's enabled state (used by the combat Director). */
    public void setEnabled(String name, boolean enabled) {
        Module module = find(name);
        if (module != null && module.isEnabled() != enabled) {
            module.setEnabled(enabled);
        }
    }

    public void tick(MinecraftClient client) {
        for (Module module : modules) {
            if (module.handleKey()) saveToConfig();
            if (module.isEnabled()) module.onTick(client);
        }
    }

    /** Lifecycle: the world changed (or the player died). Modules holding
     *  buffered packets drain their queues so stale coordinates from a
     *  previous life/world are never flushed after a respawn/teleport. */
    public void onWorldChange(MinecraftClient client) {
        for (Module module : modules) module.onWorldChange(client);
    }

    /** Lifecycle: disconnected from a server (world became null). */
    public void onDisconnect(MinecraftClient client) {
        for (Module module : modules) module.onDisconnect(client);
    }

    public List<Module> getModules() { return modules; }
    public Module find(String name) { for (Module m : modules) if (m.getName().equals(name)) return m; return null; }
    public boolean isEnabled(String name) { Module m = find(name); return m != null && m.isEnabled(); }

    public void loadFromConfig(QynlClientConfig config) {
        for (Module module : modules) {
            boolean state = config.getModuleState(module.getName(), module.isEnabled());
            if (state != module.isEnabled()) module.setEnabled(state);
            int key = config.getModuleKey(module.getName(), module.getKeyCode());
            if (key != module.getKeyCode()) module.setKeyCode(key);
            for (Map.Entry<String, String> entry : config.getModuleSettings(module.getName()).entrySet()) {
                module.applySetting(entry.getKey(), entry.getValue());
            }
        }
    }

    public void saveToConfig() {
        QynlClientConfig config = QynlClient189.getInstance().getConfig();
        if (config == null) return;
        for (Module module : modules) {
            // While the combat Director runs, managed modules are in its
            // dynamic (tactic-driven) state — persist the user's real loadout
            // instead, so a save during a fight never corrupts the config.
            Boolean dirState = DirectorModule.userState(module.getName());
            boolean state = dirState != null ? dirState.booleanValue() : module.isEnabled();
            config.setModuleState(module.getName(), state);
            config.setModuleKey(module.getName(), module.getKeyCode());
            Map<String, String> dirSettings = DirectorModule.userSettings(module.getName());
            for (Setting<?> setting : module.getSettings()) {
                String value = dirSettings != null && dirSettings.containsKey(setting.getKey())
                        ? dirSettings.get(setting.getKey())
                        : setting.valueAsString();
                config.setModuleSetting(module.getName(), setting.getKey(), value);
            }
        }
        config.save();
    }
}

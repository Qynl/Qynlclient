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
        register(new PhantomModule());
        register(new HindsightModule());
        register(new StrafeAssistModule());
        // ── Render ──────────────────────────────────────────────
        register(new SearchModule());
        register(new NameTagsModule());
        register(new TracersModule());
        register(new StorageESPModule());
        register(new FullbrightModule());
        register(new NoHurtCamModule());
        register(new NoViewBobModule());
        register(new ZoomModule());
        // ── Utility ─────────────────────────────────────────────
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

    public void tick(MinecraftClient client) {
        for (Module module : modules) {
            if (module.handleKey()) saveToConfig();
            if (module.isEnabled()) module.onTick(client);
        }
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
            config.setModuleState(module.getName(), module.isEnabled());
            config.setModuleKey(module.getName(), module.getKeyCode());
            for (Setting<?> setting : module.getSettings()) {
                config.setModuleSetting(module.getName(), setting.getKey(), setting.valueAsString());
            }
        }
        config.save();
    }
}

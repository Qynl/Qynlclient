package com.qynl.client189;

import com.qynl.client189.modules.*;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public void registerDefaults() {
        register(new BlockHitModule());
        register(new AimAssistModule());
        register(new ReachAssistModule());
        register(new AutoClickerModule());
        register(new TriggerBotModule());
        register(new StrafeAssistModule());
        register(new VelocityAssistModule());
        register(new CritAssistModule());
        register(new NinjaBridgeModule());
        register(new FullbrightModule());
        register(new StreamerModeModule());
        // Info HUD modules (mirror of the 1.21.1 client)
        register(new InfoHudModule());
        register(new TargetInfoModule());
        register(new EffectTimersModule());
        register(new DeathCoordsModule());
        register(new CoordConvertModule());
        register(new DurabilityWarnModule());
        register(new KeystrokesModule());
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

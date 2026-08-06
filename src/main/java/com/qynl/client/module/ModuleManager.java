package com.qynl.client.module;

import com.qynl.client.QynlClient;
import com.qynl.client.QynlClientConfig;
import com.qynl.client.module.modules.AimAssistModule;
import com.qynl.client.module.modules.AntiAfkModule;
import com.qynl.client.module.modules.AutoSwordModule;
import com.qynl.client.module.modules.AutoTotemModule;
import com.qynl.client.module.modules.AutoArmorModule;
import com.qynl.client.module.modules.AutoClickerModule;
import com.qynl.client.module.modules.AutoEatModule;
import com.qynl.client.module.modules.AutoFishModule;
import com.qynl.client.module.modules.AutoJumpModule;
import com.qynl.client.module.modules.AutoMineModule;
import com.qynl.client.module.modules.AutoRespawnModule;
import com.qynl.client.module.modules.AutoSprintModule;
import com.qynl.client.module.modules.AutoStepModule;
import com.qynl.client.module.modules.AutoTorchModule;
import com.qynl.client.module.modules.AutoToolModule;
import com.qynl.client.module.modules.AutoWalkModule;
import com.qynl.client.module.modules.ChestStealerModule;
import com.qynl.client.module.modules.ClickGuiModule;
import com.qynl.client.module.modules.CoordConvertModule;
import com.qynl.client.module.modules.DeathCoordsModule;
import com.qynl.client.module.modules.SafeWalkModule;
import com.qynl.client.module.modules.DurabilityWarnModule;
import com.qynl.client.module.modules.EffectTimersModule;
import com.qynl.client.module.modules.FullbrightModule;
import com.qynl.client.module.modules.InfoHudModule;
import com.qynl.client.module.modules.InvWalkModule;
import com.qynl.client.module.modules.KeystrokesModule;
import com.qynl.client.module.modules.NoFallModule;
import com.qynl.client.module.modules.AntiDrownModule;
import com.qynl.client.module.modules.AutoClimbModule;
import com.qynl.client.module.modules.AutoPotionModule;
import com.qynl.client.module.modules.ReachAssistModule;
import com.qynl.client.module.modules.ScaffoldWalkModule;
import com.qynl.client.module.modules.AutoSwimModule;
import com.qynl.client.module.modules.TargetInfoModule;
import com.qynl.client.module.modules.ToggleSneakModule;
import com.qynl.client.module.modules.VelocityAssistModule;
import com.qynl.client.module.modules.ZoomModule;
import net.minecraft.client.Minecraft;

import java.util.Map;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public void registerDefaults() {
		register(new FullbrightModule());
		register(new ZoomModule());
		register(new AutoSprintModule());
		register(new ToggleSneakModule());
		register(new AutoJumpModule());
		register(new AutoMineModule());
		register(new AutoToolModule());
		register(new AutoWalkModule());
		register(new SafeWalkModule());
		register(new AutoArmorModule());
		register(new ChestStealerModule());
		register(new DeathCoordsModule());
		register(new AutoClickerModule());
		register(new NoFallModule());
		register(new AutoSwimModule());
		register(new InvWalkModule());
		register(new AutoEatModule());
		register(new AutoRespawnModule());
		register(new AutoFishModule());
		register(new ReachAssistModule());
		register(new AimAssistModule());
		register(new AutoStepModule());
		register(new AutoTorchModule());
		register(new ScaffoldWalkModule());
		register(new AntiDrownModule());
		register(new AutoClimbModule());
		register(new AutoPotionModule());
		register(new AutoTotemModule());
		register(new AutoSwordModule());
		register(new AntiAfkModule());
		register(new KeystrokesModule());
		register(new InfoHudModule());
		register(new EffectTimersModule());
		register(new DurabilityWarnModule());
		register(new TargetInfoModule());
		register(new CoordConvertModule());
		register(new VelocityAssistModule());
		register(new ClickGuiModule());
	}

	private void register(Module module) {
		modules.add(module);
	}

	public void tick(Minecraft client) {
		for (Module module : modules) {
			if (module.handleKey(client)) {
				saveToConfig();
			}
			if (module.isEnabled()) {
				module.onTick(client);
			}
		}
	}

	public List<Module> getModules() {
		return modules;
	}

	public Module find(String name) {
		for (Module module : modules) {
			if (module.getName().equals(name)) {
				return module;
			}
		}
		return null;
	}

	public boolean isEnabled(String name) {
		Module module = find(name);
		return module != null && module.isEnabled();
	}

	public void loadFromConfig(QynlClientConfig config) {
		for (Module module : modules) {
			boolean state = config.getModuleState(module.getName(), module.isEnabled());
			if (state != module.isEnabled()) {
				module.setEnabled(state);
			}
			int key = config.getModuleKey(module.getName(), module.getKeyCode());
			if (key != module.getKeyCode()) {
				module.setKeyCode(key);
			}
			for (Map.Entry<String, String> entry : config.getModuleSettings(module.getName()).entrySet()) {
				module.applySetting(entry.getKey(), entry.getValue());
			}
		}
	}

	public void saveToConfig() {
		QynlClientConfig config = QynlClient.getInstance().getConfig();
		if (config == null) {
			return;
		}
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

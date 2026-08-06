package com.qynl.legacy.module;

import com.qynl.legacy.LegacyConfig;
import com.qynl.legacy.module.modules.*;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public void registerDefaults() {
		register(new BlockHitModule());
		register(new AimAssistModule());
		register(new ReachAssistModule());
		register(new VelocityAssistModule());
		register(new AutoSprintModule());
		register(new FullbrightModule());
	}

	private void register(Module m) { modules.add(m); }

	public void tick(MinecraftClient client) {
		for (Module m : modules) {
			if (m.isEnabled()) m.onTick(client);
		}
	}

	public void handleKey(int keyCode) {
		for (Module m : modules) {
			if (m.getKeyCode() == keyCode) {
				m.toggle();
				LegacyConfig.save(this);
				return;
			}
		}
	}

	public List<Module> getModules() { return modules; }

	public Module find(String name) {
		for (Module m : modules) if (m.getName().equals(name)) return m;
		return null;
	}

	public boolean isEnabled(String name) {
		Module m = find(name);
		return m != null && m.isEnabled();
	}
}

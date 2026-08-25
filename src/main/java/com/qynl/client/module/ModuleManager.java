package com.qynl.client.module;

import com.qynl.client.QynlClient;
import com.qynl.client.QynlClientConfig;
import com.qynl.client.module.modules.AegisModule;
import com.qynl.client.module.modules.AimAssistModule;
import com.qynl.client.module.modules.AutoClickerModule;
import com.qynl.client.module.modules.AutoSprintModule;
import com.qynl.client.module.modules.BlinkModule;
import com.qynl.client.module.modules.BlockHitModule;
import com.qynl.client.module.modules.ChestStealerModule;
import com.qynl.client.module.modules.ClutchModule;
import com.qynl.client.module.modules.CriticalsModule;
import com.qynl.client.module.modules.DirectorModule;
import com.qynl.client.module.modules.EchoModule;
import com.qynl.client.module.modules.FriendsModule;
import com.qynl.client.module.modules.FullbrightModule;
import com.qynl.client.module.modules.HindsightModule;
import com.qynl.client.module.modules.NameTagsModule;
import com.qynl.client.module.modules.NoHurtCamModule;
import com.qynl.client.module.modules.NoViewBobModule;
import com.qynl.client.module.modules.QynlModule;
import com.qynl.client.module.modules.ReachAssistModule;
import com.qynl.client.module.modules.RefillModule;
import com.qynl.client.module.modules.ScaffoldWalkModule;
import com.qynl.client.module.modules.SearchModule;
import com.qynl.client.module.modules.StorageESPModule;
import com.qynl.client.module.modules.StrafeAssistModule;
import com.qynl.client.module.modules.StreamerModeModule;
import com.qynl.client.module.modules.TextGuiModule;
import com.qynl.client.module.modules.ThrowpotModule;
import com.qynl.client.module.modules.TracersModule;
import com.qynl.client.module.modules.VelocityAssistModule;
import com.qynl.client.module.modules.VersionAssistModule;
import com.qynl.client.module.modules.WTapModule;
import com.qynl.client.module.modules.ZoomModule;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModuleManager {
	private final List<Module> modules = new ArrayList<>();

	public void registerDefaults() {
		// ── Combat ──────────────────────────────────────────────
		register(new AimAssistModule());
		register(new AutoClickerModule());
		register(new ReachAssistModule());
		register(new VelocityAssistModule());
		register(new AutoSprintModule());
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
		register(new ScaffoldWalkModule());
		register(new ChestStealerModule());
		register(new BlinkModule());
		register(new RefillModule());
		register(new ThrowpotModule());
		register(new VersionAssistModule());
		// ── Other ───────────────────────────────────────────────
		register(new TextGuiModule());
		register(new FriendsModule());
		register(new StreamerModeModule());
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

	/** Lifecycle: the world changed (or the player died). Modules holding
	 *  buffered packets drain their queues so stale coordinates from a
	 *  previous life/world are never flushed after a respawn/teleport. */
	public void onWorldChange(Minecraft client) {
		for (Module module : modules) {
			module.onWorldChange(client);
		}
	}

	/** Lifecycle: disconnected from a server (world became null). */
	public void onDisconnect(Minecraft client) {
		for (Module module : modules) {
			module.onDisconnect(client);
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
				try {
					module.applySetting(entry.getKey(), entry.getValue());
				} catch (RuntimeException ignored) {
					// One corrupt setting must never block client startup.
				}
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

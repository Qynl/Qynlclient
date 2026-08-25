package com.qynl.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class QynlClientConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("qynlclient-config");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * Bump when defaults change in a way that makes old saved values stale
	 * or harmful. v2 (2.1.7): keys + settings reset once — old builds saved
	 * e.g. ChestStealer=T, AimAssist OnAttack/Hostile and AutoSprint off,
	 * which resurrected exactly the bugs users kept hitting. Module states
	 * (on/off) are kept.
	 */
	private static final int CONFIG_VERSION = 2;

	private final Map<String, Boolean> moduleStates = new LinkedHashMap<>();
	private final Map<String, Integer> moduleKeys = new LinkedHashMap<>();
	private final Map<String, Map<String, String>> moduleSettings = new LinkedHashMap<>();

	public void setModuleState(String name, boolean enabled) {
		moduleStates.put(name, enabled);
	}

	public boolean getModuleState(String name, boolean fallback) {
		return moduleStates.getOrDefault(name, fallback);
	}

	public void setModuleKey(String name, int keyCode) {
		moduleKeys.put(name, keyCode);
	}

	public int getModuleKey(String name, int fallback) {
		return moduleKeys.getOrDefault(name, fallback);
	}

	public void setModuleSetting(String module, String key, String value) {
		moduleSettings.computeIfAbsent(module, k -> new LinkedHashMap<>()).put(key, value);
	}

	public Map<String, String> getModuleSettings(String module) {
		return moduleSettings.getOrDefault(module, Map.of());
	}

	public void save() {
		JsonObject root = new JsonObject();
		root.addProperty("version", CONFIG_VERSION);
		JsonObject modules = new JsonObject();
		for (Map.Entry<String, Boolean> entry : moduleStates.entrySet()) {
			modules.addProperty(entry.getKey(), entry.getValue());
		}
		root.add("modules", modules);
		JsonObject keys = new JsonObject();
		for (Map.Entry<String, Integer> entry : moduleKeys.entrySet()) {
			keys.addProperty(entry.getKey(), entry.getValue());
		}
		root.add("keys", keys);
		JsonObject settings = new JsonObject();
		for (Map.Entry<String, Map<String, String>> entry : moduleSettings.entrySet()) {
			JsonObject module = new JsonObject();
			entry.getValue().forEach(module::addProperty);
			settings.add(entry.getKey(), module);
		}
		root.add("settings", settings);
		try {
			Files.writeString(path(), GSON.toJson(root));
		} catch (IOException e) {
			LOGGER.warn("Could not save QynlClient config", e);
		}
	}

	public static QynlClientConfig load() {
		QynlClientConfig cfg = new QynlClientConfig();
		try {
			String json = Files.readString(path());
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			int version = root.has("version") ? root.get("version").getAsInt() : 1;
			if (version >= CONFIG_VERSION) {
				if (root.has("modules") && root.get("modules").isJsonObject()) {
					JsonObject modules = root.get("modules").getAsJsonObject();
					modules.entrySet().forEach(entry ->
							cfg.moduleStates.put(entry.getKey(), entry.getValue().getAsBoolean()));
				}
				if (root.has("keys") && root.get("keys").isJsonObject()) {
					JsonObject keys = root.get("keys").getAsJsonObject();
					keys.entrySet().forEach(entry ->
							cfg.moduleKeys.put(entry.getKey(), entry.getValue().getAsInt()));
				}
				if (root.has("settings") && root.get("settings").isJsonObject()) {
					JsonObject settings = root.get("settings").getAsJsonObject();
					settings.entrySet().forEach(module -> {
						if (module.getValue().isJsonObject()) {
							Map<String, String> values = new LinkedHashMap<>();
							module.getValue().getAsJsonObject().entrySet().forEach(e ->
									values.put(e.getKey(), e.getValue().getAsString()));
							cfg.moduleSettings.put(module.getKey(), values);
						}
					});
				}
			}
			// v1 configs: module states, keys and settings are all dropped so
			// every module starts with the current defaults (AutoSprint on,
			// AimAssist Always/Players, no stale keybindings like ChestStealer=T).
			// This one-time reset clears every stale default that made the
			// client feel dead.
		} catch (Exception ignored) {
			// First run or unreadable config: start fresh with defaults.
		}
		return cfg;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("qynlclient.json");
	}
}

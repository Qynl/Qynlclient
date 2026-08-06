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

	private final Map<String, Boolean> moduleStates = new LinkedHashMap<>();
	private final Map<String, Integer> moduleKeys = new LinkedHashMap<>();

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

	public void save() {
		JsonObject root = new JsonObject();
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
		} catch (Exception ignored) {
			// First run or unreadable config: start fresh with defaults.
		}
		return cfg;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("qynlclient.json");
	}
}

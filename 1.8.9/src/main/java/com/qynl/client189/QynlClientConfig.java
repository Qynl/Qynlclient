package com.qynl.client189;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class QynlClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Boolean> moduleStates = new LinkedHashMap<>();
    private final Map<String, Integer> moduleKeys = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> moduleSettings = new LinkedHashMap<>();

    public void setModuleState(String name, boolean enabled) { moduleStates.put(name, enabled); }
    public boolean getModuleState(String name, boolean fallback) { return moduleStates.containsKey(name) ? moduleStates.get(name) : fallback; }
    public void setModuleKey(String name, int keyCode) { moduleKeys.put(name, keyCode); }
    public int getModuleKey(String name, int fallback) { return moduleKeys.containsKey(name) ? moduleKeys.get(name) : fallback; }

    public void setModuleSetting(String module, String key, String value) {
        if (!moduleSettings.containsKey(module)) moduleSettings.put(module, new LinkedHashMap<>());
        moduleSettings.get(module).put(key, value);
    }

    public Map<String, String> getModuleSettings(String module) {
        Map<String, String> m = moduleSettings.get(module);
        return m != null ? m : new LinkedHashMap<>();
    }

    public void save() {
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Map.Entry<String, Boolean> e : moduleStates.entrySet()) modules.addProperty(e.getKey(), e.getValue());
        root.add("modules", modules);
        JsonObject keys = new JsonObject();
        for (Map.Entry<String, Integer> e : moduleKeys.entrySet()) keys.addProperty(e.getKey(), e.getValue());
        root.add("keys", keys);
        JsonObject settings = new JsonObject();
        for (Map.Entry<String, Map<String, String>> e : moduleSettings.entrySet()) {
            JsonObject mod = new JsonObject();
            for (Map.Entry<String, String> s : e.getValue().entrySet()) mod.addProperty(s.getKey(), s.getValue());
            settings.add(e.getKey(), mod);
        }
        root.add("settings", settings);
        try { Files.write(path(), GSON.toJson(root).getBytes()); }
        catch (IOException ex) { System.err.println("[QynlClient-1.8.9] Could not save config: " + ex.getMessage()); }
    }

    public static QynlClientConfig load() {
        QynlClientConfig cfg = new QynlClientConfig();
        try {
            String json = new String(Files.readAllBytes(path()));
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (root.has("modules") && root.get("modules").isJsonObject()) {
                JsonObject mods = root.get("modules").getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : mods.entrySet()) cfg.moduleStates.put(e.getKey(), e.getValue().getAsBoolean());
            }
            if (root.has("keys") && root.get("keys").isJsonObject()) {
                JsonObject k = root.get("keys").getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : k.entrySet()) cfg.moduleKeys.put(e.getKey(), e.getValue().getAsInt());
            }
            if (root.has("settings") && root.get("settings").isJsonObject()) {
                JsonObject s = root.get("settings").getAsJsonObject();
                for (Map.Entry<String, JsonElement> mod : s.entrySet()) {
                    if (mod.getValue().isJsonObject()) {
                        Map<String, String> vals = new LinkedHashMap<>();
                        for (Map.Entry<String, JsonElement> se : mod.getValue().getAsJsonObject().entrySet())
                            vals.put(se.getKey(), se.getValue().getAsString());
                        cfg.moduleSettings.put(mod.getKey(), vals);
                    }
                }
            }
        } catch (Exception ignored) {}
        return cfg;
    }

    private static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("qynlclient189.json"); }
}

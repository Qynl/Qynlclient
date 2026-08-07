package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VersionAssist — play on modern servers (1.9 – 1.21.x) while running 1.8.9.
 *
 * <p>The client ships with <b>ViaFabric</b> (ViaVersion) embedded as a
 * bundled mod. That alone gives you protocol translation, but two client-side
 * options decide whether it actually works against arbitrary servers:</p>
 *
 * <ul>
 *   <li><b>enable-client-side</b> — ViaFabric's own default is OFF, which
 *       means the client only works when the server itself runs ViaVersion.
 *       VersionAssist turns it ON, so your 1.8.9 client translates its own
 *       traffic to whatever protocol the target server speaks (the server
 *       version is auto-detected from the server list ping).</li>
 *   <li><b>send-connection-details</b> — kept OFF so the server is never
 *       told "this client is running ViaFabric".</li>
 * </ul>
 *
 * <p>All ViaFabric access uses reflection so the module compiles and runs
 * gracefully whether or not the ViaFabric jar and its nested ViaVersion
 * dependency are on the compile classpath at build time.</p>
 */
public class VersionAssistModule extends Module {
    private static VersionAssistModule instance;

    /** Common version name → protocol id. -1 = auto-detect. */
    private static final Map<String, Integer> VERSIONS = new LinkedHashMap<>();

    static {
        VERSIONS.put("Auto", -1);
        VERSIONS.put("1.8.9", 47);
        VERSIONS.put("1.12.2", 340);
        VERSIONS.put("1.16.5", 754);
        VERSIONS.put("1.17.1", 756);
        VERSIONS.put("1.18.2", 758);
        VERSIONS.put("1.19.2", 760);
        VERSIONS.put("1.19.4", 762);
        VERSIONS.put("1.20.1", 763);
        VERSIONS.put("1.20.4", 765);
        VERSIONS.put("1.20.6", 766);
        VERSIONS.put("1.21", 767);
        VERSIONS.put("1.21.1", 767);
        VERSIONS.put("1.21.4", 769);
    }

    private String appliedVersion = "";
    /** Cached reflective handles — resolved once, reused. */
    private Object cachedConfig;
    private Method isClientSideEnabledMethod;
    private Method setClientSideEnabledMethod;
    private Method getClientSideVersionMethod;
    private Method setClientSideVersionMethod;
    private Method isSendConnectionDetailsMethod;
    private Method setSendConnDetailsMethod;
    private Method saveMethod;
    private boolean reflectionReady;

    public VersionAssistModule() {
        super("VersionAssist",
                "Play on 1.9–1.21 servers from 1.8.9 — embedded ViaVersion translates "
                        + "automatically. Pick a target version or leave it on Auto.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_V);
        addSetting(Setting.options("version", "Target version", "Auto",
                VERSIONS.keySet().toArray(new String[0])));
        setEnabled(true);
    }

    public static VersionAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    public static String targetName() {
        if (instance == null) return "Auto";
        String v = instance.getStringSetting("version");
        return v.isEmpty() ? "Auto" : v;
    }

    @Override public void onEnable() { applyConfig(); }

    @Override
    public void onTick(MinecraftClient client) {
        if (!targetName().equals(appliedVersion) || !isApplied()) {
            applyConfig();
        }
    }

    /** Resolves reflective handles once the ViaFabric config is available. */
    private boolean ensureReflection() {
        if (reflectionReady && cachedConfig != null) return true;
        try {
            // com.viaversion.fabric.common.ViaFabric.config  (static field)
            Class<?> viaFabricClass = Class.forName("com.viaversion.fabric.common.ViaFabric");
            Field configField = viaFabricClass.getField("config");
            Object cfg = configField.get(null);
            if (cfg == null) return false;
            cachedConfig = cfg;
            Class<?> cfgClass = cfg.getClass();

            isClientSideEnabledMethod  = cfgClass.getMethod("isClientSideEnabled");
            setClientSideEnabledMethod = cfgClass.getMethod("setClientSideEnabled", boolean.class);
            getClientSideVersionMethod = cfgClass.getMethod("getClientSideVersion");
            setClientSideVersionMethod = cfgClass.getMethod("setClientSideVersion", int.class);
            isSendConnectionDetailsMethod = cfgClass.getMethod("isSendConnectionDetails");

            // VFConfig extends Config, which has set(String,Object) and save()
            try {
                setSendConnDetailsMethod = cfgClass.getMethod("set", String.class, Object.class);
                saveMethod = cfgClass.getMethod("save");
            } catch (NoSuchMethodException e) {
                // Older ViaFabric might not expose these — skip gracefully.
            }
            reflectionReady = true;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isApplied() {
        if (!ensureReflection()) return false;
        try {
            return (boolean) isClientSideEnabledMethod.invoke(cachedConfig);
        } catch (Throwable t) {
            return false;
        }
    }

    private void applyConfig() {
        appliedVersion = targetName();
        if (!ensureReflection()) return;
        try {
            // Enable client-side translation (the key setting).
            if (!(boolean) isClientSideEnabledMethod.invoke(cachedConfig)) {
                setClientSideEnabledMethod.invoke(cachedConfig, true);
            }

            // Target version.
            Integer id = VERSIONS.get(appliedVersion);
            if (id != null) {
                int current = (int) getClientSideVersionMethod.invoke(cachedConfig);
                if (current != id) {
                    setClientSideVersionMethod.invoke(cachedConfig, id);
                }
            }

            // Stealth: disable "send-connection-details".
            if ((boolean) isSendConnectionDetailsMethod.invoke(cachedConfig)) {
                if (setSendConnDetailsMethod != null) {
                    setSendConnDetailsMethod.invoke(cachedConfig, "send-connection-details", false);
                }
            }

            // Persist to disk.
            if (saveMethod != null) {
                saveMethod.invoke(cachedConfig);
            }
        } catch (Throwable t) {
            System.err.println("[QynlClient-1.8.9] VersionAssist could not write ViaFabric config: " + t);
        }
    }
}

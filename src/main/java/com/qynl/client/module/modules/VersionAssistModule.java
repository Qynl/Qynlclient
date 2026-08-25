package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VersionAssist — play on newer servers (1.21.2 – 1.21.x) while running
 * 1.21.1. The client ships with <b>ViaFabric</b> (ViaVersion) as a bundled
 * mod; this module turns on ViaFabric's client-side translation so your
 * 1.21.1 client talks to whatever protocol the target server speaks.
 *
 * <p>All ViaFabric access uses reflection so the module compiles and runs
 * gracefully whether or not the ViaFabric jar is present.</p>
 */
public class VersionAssistModule extends Module {
    private static VersionAssistModule instance;

    /** Common version name → protocol id. -1 = auto-detect. */
    private static final Map<String, Integer> VERSIONS = new LinkedHashMap<>();

    static {
        VERSIONS.put("Auto", -1);
        VERSIONS.put("1.21.1", 767);
        VERSIONS.put("1.21.2", 768);
        VERSIONS.put("1.21.3", 768);
        VERSIONS.put("1.21.4", 769);
        VERSIONS.put("1.21.5", 770);
        VERSIONS.put("1.21.6", 771);
        VERSIONS.put("1.21.7", 772);
        VERSIONS.put("1.21.8", 773);
        VERSIONS.put("1.21.9", 774);
    }

    private String appliedVersion = "";
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
                "Play on 1.21.2+ servers from 1.21.1 — embedded ViaVersion translates "
                        + "automatically. Pick a target version or leave it on Auto.",
                Category.UTILITY);
        instance = this;
        bindKey(GLFW.GLFW_KEY_V);
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
    public void onTick(Minecraft client) {
        if (!targetName().equals(appliedVersion) || !isApplied()) {
            applyConfig();
        }
    }

    /** Resolves reflective handles once the ViaFabric config is available. */
    private boolean ensureReflection() {
        if (reflectionReady && cachedConfig != null) return true;
        try {
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
            if (!(boolean) isClientSideEnabledMethod.invoke(cachedConfig)) {
                setClientSideEnabledMethod.invoke(cachedConfig, true);
            }

            Integer id = VERSIONS.get(appliedVersion);
            if (id != null) {
                int current = (int) getClientSideVersionMethod.invoke(cachedConfig);
                if (current != id) {
                    setClientSideVersionMethod.invoke(cachedConfig, id);
                }
            }

            if ((boolean) isSendConnectionDetailsMethod.invoke(cachedConfig)) {
                if (setSendConnDetailsMethod != null) {
                    setSendConnDetailsMethod.invoke(cachedConfig, "send-connection-details", false);
                }
            }

            if (saveMethod != null) {
                saveMethod.invoke(cachedConfig);
            }
        } catch (Throwable t) {
            System.err.println("[Qyn-L] VersionAssist could not write ViaFabric config: " + t);
        }
    }
}

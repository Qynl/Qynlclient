package com.qynl.client189;

import com.qynl.client189.access.IKeyBindingAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private KeyBinding keyBinding;
    private String keyLabel = "";
    private int keyCode = -1;
    private boolean enabled;

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected void bindKey(int keyCode) {
        String keyName = "key.qynlclient189." + name.toLowerCase().replace(" ", "_");
        this.keyBinding = new KeyBinding(keyName, keyCode, "category.qynlclient189");
        this.keyCode = keyCode;
        updateKeyLabel(keyCode);
        registerKeyBinding(this.keyBinding);
    }

    private static void registerKeyBinding(KeyBinding kb) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) {
            KeyBinding[] old = mc.options.keysAll;
            KeyBinding[] next = new KeyBinding[old.length + 1];
            System.arraycopy(old, 0, next, 0, old.length);
            next[old.length] = kb;
            mc.options.keysAll = next;
        }
    }

    public void setKeyCode(int keyCode) {
        if (keyBinding == null) return;
        this.keyCode = keyCode;
        if (keyCode <= 0) {
            ((IKeyBindingAccess) keyBinding).qynlSetCode(0);
            keyLabel = "None";
        } else {
            ((IKeyBindingAccess) keyBinding).qynlSetCode(keyCode);
            updateKeyLabel(keyCode);
        }
    }

    public int getKeyCode() { return keyCode; }

    private void updateKeyLabel(int keyCode) {
        String name = Keyboard.getKeyName(keyCode);
        keyLabel = name != null ? name.toUpperCase() : "KEY_" + keyCode;
    }

    public boolean handleKey() {
        if (keyBinding != null && keyBinding.wasPressed()) {
            toggle();
            return true;
        }
        return false;
    }

    public void toggle() { setEnabled(!enabled); }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick(MinecraftClient client) {}

    /** Fired by the central tick when the world reference changes or the
     *  player dies — the place to drain buffered packets. Runs even while
     *  the module is disabled, so state never outlives a world. */
    public void onWorldChange(MinecraftClient client) {}

    /** Fired when the world becomes null (disconnect / leaving). */
    public void onDisconnect(MinecraftClient client) {}

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public KeyBinding getKeyBinding() { return keyBinding; }
    public String getKeyLabel() { return keyLabel; }
    public boolean isEnabled() { return enabled; }

    protected void addSetting(Setting<?> setting) { settings.put(setting.getKey(), setting); }
    public Setting<?> getSetting(String key) { return settings.get(key); }
    public Collection<Setting<?>> getSettings() { return settings.values(); }
    public boolean hasSettings() { return !settings.isEmpty(); }
    public String getStringSetting(String key) { Setting<?> s = settings.get(key); return s == null ? "" : String.valueOf(s.getValue()); }
    public double getDoubleSetting(String key) { Setting<?> s = settings.get(key); return s == null ? 0 : s.asDouble(); }
    public void applySetting(String key, String value) { Setting<?> s = settings.get(key); if (s != null) s.setFromString(value); }
}

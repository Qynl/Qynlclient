package com.qynl.client.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Module {
	private final String name;
	private final String description;
	private final Category category;
	private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
	private KeyMapping keyMapping;
	private String keyLabel = "";
	private int keyCode = -1;
	private int defaultKeyCode = -1;
	private boolean keyLabelResolved;
	private boolean enabled;

	public Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	/** Registers a toggle keybind with the game's Controls screen.
	 *  GLFW is NOT called during construction — the label is resolved
	 *  lazily on first access, well after Minecraft has initialised. */
	protected void bindKey(int keyCode) {
		String keyName = name.toLowerCase().replace(" ", "_");
		keyMapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.qynlclient." + keyName,
				InputConstants.Type.KEYSYM,
				keyCode,
				"category.qynlclient"
		));
		this.keyCode = keyCode;
		this.defaultKeyCode = keyCode;
		// Defer GLFW: keyLabel stays "" until first getKeyLabel() call.
		keyLabelResolved = false;
	}

	/** Changes the toggle key at runtime (used by the in-game keybind editor). */
	public void setKeyCode(int keyCode) {
		if (keyMapping == null) {
			return;
		}
		this.keyCode = keyCode;
		if (keyCode <= 0) {
			keyMapping.setKey(InputConstants.UNKNOWN);
			keyLabel = "None";
			keyLabelResolved = true;
		} else {
			keyMapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
			keyLabelResolved = false; // lazy-resolve on next getKeyLabel()
		}
		// 1.21.1's KeyMapping.setKey() only writes the key field — the static
		// key→mapping lookup (MAP) is NOT rebuilt, so the old key keeps firing
		// the module after an unbind/rebind until the next game restart.
		// Rebuild it and clear any stale pressed state so changes apply now.
		keyMapping.setDown(false);
		KeyMapping.resetMapping();
	}

	public int getKeyCode() {
		return keyCode;
	}

	/** The key this module was constructed with — used so a stale config
	 *  value that merely equals the default is never re-applied over a
	 *  vanilla Controls unbind. */
	public int getDefaultKeyCode() {
		return defaultKeyCode;
	}

	/**
	 * Returns a human-readable key name.  The first call resolves the name
	 * via GLFW (which is guaranteed to be initialised by that point).
	 * The result is cached so GLFW is only called once per module.
	 */
	public String getKeyLabel() {
		if (!keyLabelResolved) {
			keyLabelResolved = true;
			if (keyCode <= 0) {
				keyLabel = "None";
			} else {
				String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
				keyLabel = glfwName != null ? glfwName.toUpperCase() : "KEY_" + keyCode;
			}
		}
		return keyLabel;
	}

	public boolean handleKey(Minecraft client) {
		if (keyMapping != null && keyMapping.consumeClick()) {
			toggle();
			return true;
		}
		return false;
	}

	public void toggle() {
		boolean next = !enabled;
		setEnabled(next);
		playToggleSound(next);
	}

	/** Plays a short click so toggling is audible — an accessibility aid. */
	private void playToggleSound(boolean on) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getSoundManager() == null) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, on ? 1.0F : 0.55F));
	}

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public void onEnable() {}
	public void onDisable() {}
	public void onTick(Minecraft client) {}

	/** Fired by the central tick when the world reference changes or the
	 *  player dies — the place to drain buffered packets. Runs even while
	 *  the module is disabled, so state never outlives a world. */
	public void onWorldChange(Minecraft client) {}

	/** Fired when the world becomes null (disconnect / leaving). */
	public void onDisconnect(Minecraft client) {}

	public String getName()        { return name; }
	public String getDescription() { return description; }
	public Category getCategory()  { return category; }
	public KeyMapping getKeyMapping() { return keyMapping; }
	public boolean isEnabled()     { return enabled; }

	// ── settings ────────────────────────────────────────────────

	protected void addSetting(Setting<?> setting) {
		settings.put(setting.getKey(), setting);
	}

	public Setting<?> getSetting(String key) {
		return settings.get(key);
	}

	public Collection<Setting<?>> getSettings() {
		return settings.values();
	}

	public boolean hasSettings() {
		return !settings.isEmpty();
	}

	public String getStringSetting(String key) {
		Setting<?> s = settings.get(key);
		return s == null ? "" : String.valueOf(s.getValue());
	}

	public double getDoubleSetting(String key) {
		Setting<?> s = settings.get(key);
		return s == null ? 0 : s.asDouble();
	}

	public void applySetting(String key, String value) {
		Setting<?> s = settings.get(key);
		if (s != null) s.setFromString(value);
	}
}
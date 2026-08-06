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
	private boolean enabled;

	public Module(String name, String description, Category category) {
		this.name = name;
		this.description = description;
		this.category = category;
	}

	/** Registers a toggle keybind with the game's Controls screen. */
	protected void bindKey(int keyCode) {
		String keyName = name.toLowerCase().replace(" ", "_");
		keyMapping = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.qynlclient." + keyName,
				InputConstants.Type.KEYSYM,
				keyCode,
				"category.qynlclient"
		));
		this.keyCode = keyCode;
		updateKeyLabel(keyCode);
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
		} else {
			keyMapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
			updateKeyLabel(keyCode);
		}
	}

	public int getKeyCode() {
		return keyCode;
	}

	private void updateKeyLabel(int keyCode) {
		String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
		keyLabel = glfwName != null ? glfwName.toUpperCase() : "KEY_" + keyCode;
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

	public void onEnable() {
	}

	public void onDisable() {
	}

	public void onTick(Minecraft client) {
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public KeyMapping getKeyMapping() {
		return keyMapping;
	}

	public String getKeyLabel() {
		return keyLabel;
	}

	public boolean isEnabled() {
		return enabled;
	}

	// ------------------------------------------------------------------
	// Settings (per-module options shown in the in-game Settings screen)
	// ------------------------------------------------------------------

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
		if (s != null) {
			s.setFromString(value);
		}
	}
}

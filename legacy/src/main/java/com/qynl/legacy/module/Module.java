package com.qynl.legacy.module;

import net.minecraft.client.MinecraftClient;

/** Minimal module — a toggleable assist with a keybind. */
public abstract class Module {
	protected final String name;
	protected final String description;
	private int keyCode;
	private boolean enabled;

	public Module(String name, String description, int keyCode) {
		this.name = name;
		this.description = description;
		this.keyCode = keyCode;
	}

	public String getName() { return name; }
	public String getDescription() { return description; }

	public int getKeyCode() { return keyCode; }
	public void setKeyCode(int code) { this.keyCode = code; }

	public boolean isEnabled() { return enabled; }

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		if (enabled) onEnable(); else onDisable();
	}

	public void toggle() { setEnabled(!enabled); }

	public void onEnable() {}
	public void onDisable() {}
	public void onTick(MinecraftClient client) {}
}

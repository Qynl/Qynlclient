package com.qynl.legacy;

import com.qynl.legacy.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class QynlLegacyClient implements ClientModInitializer {
	public static final String MOD_ID = "qynlclient-legacy";
	public static final String VERSION = "1.0.0";

	private static QynlLegacyClient instance;

	private final ModuleManager moduleManager = new ModuleManager();

	@Override
	public void onInitializeClient() {
		instance = this;

		moduleManager.registerDefaults();
		LegacyConfig.load(moduleManager);

		ClientTickEvents.END_CLIENT_TICK.register(client -> moduleManager.tick(client));
		System.out.println("[QynlClient Legacy] v" + VERSION + " ready — assist modules loaded.");
	}

	public static QynlLegacyClient getInstance() {
		return instance;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}
}

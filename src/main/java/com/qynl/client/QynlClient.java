package com.qynl.client;

import com.qynl.client.hud.HudRenderer;
import com.qynl.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QynlClient implements ClientModInitializer {
	public static final String MOD_ID = "qynlclient";
	public static final String VERSION = "1.4.0";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static QynlClient instance;

	private final ModuleManager moduleManager = new ModuleManager();
	private final HudRenderer hudRenderer = new HudRenderer();
	private QynlClientConfig config;

	@Override
	public void onInitializeClient() {
		instance = this;

		config = QynlClientConfig.load();
		moduleManager.registerDefaults();
		moduleManager.loadFromConfig(config);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			moduleManager.tick(client);
			hudRenderer.handleClick(client);
		});

		LOGGER.info("QynlClient v{} initialized", VERSION);
	}

	public static QynlClient getInstance() {
		return instance;
	}

	public ModuleManager getModuleManager() {
		return moduleManager;
	}

	public HudRenderer getHudRenderer() {
		return hudRenderer;
	}

	public QynlClientConfig getConfig() {
		return config;
	}
}

package com.qynl.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.qynl.client.hud.ClickGuiScreen;
import com.qynl.client.hud.HudRenderer;
import com.qynl.client.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QynlClient implements ClientModInitializer {
	public static final String MOD_ID = "qynlclient";
	public static final String VERSION = "2.1.29";

	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static QynlClient instance;

	private final ModuleManager moduleManager = new ModuleManager();
	private final HudRenderer hudRenderer = new HudRenderer();
	private QynlClientConfig config;
	private KeyMapping clickGuiKey;

	// ── central lifecycle tracking (world change / death / disconnect) ──
	private Object lastWorld = null;
	private boolean lastPlayerAlive = true;

	// ── TPS estimate from the client tick cadence ──
	private long lastTickTime = -1;
	private double tpsEstimate = 20.0;

	@Override
	public void onInitializeClient() {
		instance = this;

		config = QynlClientConfig.load();
		// Right-Shift opens the ClickGUI (the GUI itself closes on RShift too).
		clickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.qynlclient.clickgui",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.qynlclient"
		));
		moduleManager.registerDefaults();
		moduleManager.loadFromConfig(config);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// ── lifecycle: world change / death / disconnect ───────
			boolean died = client.player != null && lastPlayerAlive && !client.player.isAlive();
			boolean worldChanged = client.level != lastWorld;
			if (died || worldChanged) {
				moduleManager.onWorldChange(client);
			}
			// Announce the running build once per world join — the definitive
			// "am I actually running this jar" check. If the watermark says
			// v2.1.5 and this message appears, the new jar is live.
			if (worldChanged && client.level != null && client.player != null) {
				client.player.displayClientMessage(
					net.minecraft.network.chat.Component.literal(
							"\u00a7aQynlClient \u00a7fv" + VERSION + " \u00a77\u2014 Right-Shift toggles the ClickGUI"),
						false);
			}
			if (client.level == null && lastWorld != null) {
				moduleManager.onDisconnect(client);
			}
			lastWorld = client.level;
			lastPlayerAlive = client.player == null || client.player.isAlive();

			// ── TPS estimate ────────────────────────────────────────
			long now = System.currentTimeMillis();
			if (lastTickTime > 0) {
				double gap = now - lastTickTime;
				if (gap > 1.0) {
					double tps = Math.min(20.0, 1000.0 / gap);
					tpsEstimate += (tps - tpsEstimate) * 0.2;
				}
			}
			lastTickTime = now;

			if (client.player != null && client.level != null) {
				// Right-Shift TOGGLES the ClickGUI: opens when nothing is open,
				// closes it when it is the open screen. Closing happens here in
				// the tick hook, never in the screen's key handler — a press
				// fed to both would close then instantly reopen the GUI (the
				// "impossible to close" bug). A stuck-open GUI is catastrophic:
				// vanilla skips ALL in-game attack/use input while any screen is
				// open, so the player's left AND right click both go dead.
				if (clickGuiKey != null && clickGuiKey.consumeClick()) {
					// Close any of the client's own screens (ClickGUI, module
					// detail, keybind/settings editors) and open the GUI when
					// nothing is open.
					if (client.screen != null
							&& client.screen.getClass().getName().startsWith("com.qynl.client.hud.")) {
						client.setScreen(null);
					} else if (client.screen == null) {
						openGui();
					}
					return;
				}
				moduleManager.tick(client);
			}
		});

		LOGGER.info("QynlClient v{} initialized", VERSION);
	}

	/** Client-side TPS estimate (0–20), for the HUD info widget. */
	public double getTps() {
		return tpsEstimate;
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

	public void openGui() {
		Minecraft.getInstance().setScreen(new ClickGuiScreen());
	}
}

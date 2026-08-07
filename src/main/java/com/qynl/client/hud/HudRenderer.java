package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.module.modules.CoordConvertModule;
import com.qynl.client.module.modules.DeathCoordsModule;
import com.qynl.client.module.modules.DurabilityWarnModule;
import com.qynl.client.module.modules.EffectTimersModule;
import com.qynl.client.module.modules.InfoHudModule;
import com.qynl.client.module.modules.KeystrokesModule;
import com.qynl.client.module.modules.TargetInfoModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HudRenderer {
	public static final int MODULE_BG = 0x66000000;
	public static final int ACCENT = 0xFF6EE7A0;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFF9CA3AF;
	public static final int RED = 0xFFFF6B6B;

	private final List<int[]> moduleRects = new ArrayList<>();
	private final List<Module> moduleRowModules = new ArrayList<>();
	private boolean lastClickState = false;

	public void render(GuiGraphics guiGraphics, Minecraft client) {
		moduleRects.clear();
		moduleRowModules.clear();

		ModuleManager modules = QynlClient.getInstance().getModuleManager();

		renderTitle(guiGraphics, client);
		int rightY = renderRightColumn(guiGraphics, client, modules);
		renderModuleList(guiGraphics, client, modules);
		renderKeystrokes(guiGraphics, client, modules);
		renderDurabilityWarn(guiGraphics, client, modules);
		// rightY unused placeholder kept simple: effects drawn inside right column
	}

	private void renderTitle(GuiGraphics g, Minecraft client) {
		Component title = Component.literal("QynlClient");
		g.drawString(client.font, title, 4, 4, ACCENT, true);
		g.drawString(client.font, Component.literal("v" + QynlClient.VERSION),
				4 + client.font.width(title) + 6, 6, GRAY, false);
	}

	private int renderRightColumn(GuiGraphics g, Minecraft client, ModuleManager modules) {
		int y = 18;
		int rightX = client.getWindow().getGuiScaledWidth() - 8;

		InfoHudModule info = (InfoHudModule) modules.find("InfoHUD");
		if (info != null && info.isEnabled()) {
			List<String> lines = List.of(
					info.fps(client), info.coords(client), info.direction(client),
					info.biome(client), info.time(client), info.ping(client));
			for (String line : lines) {
				if (line.isEmpty()) {
					continue;
				}
				int width = client.font.width(line);
				g.fill(rightX - width - 6, y - 1, rightX, y + 9, MODULE_BG);
				g.drawString(client.font, line, rightX - width - 3, y, WHITE, false);
				y += 11;
			}
			y += 4;
		}

		TargetInfoModule target = (TargetInfoModule) modules.find("TargetInfo");
		if (target != null && target.isEnabled()) {
			String line = target.getInfo(client);
			if (!line.isEmpty()) {
				int width = client.font.width(line);
				g.fill(rightX - width - 6, y - 1, rightX, y + 9, MODULE_BG);
				g.drawString(client.font, line, rightX - width - 3, y, ACCENT, false);
				y += 11;
			}
			y += 4;
		}

		EffectTimersModule effects = (EffectTimersModule) modules.find("EffectTimers");
		if (effects != null && effects.isEnabled()) {
			for (MobEffectInstance effect : effects.getEffects(client)) {
				String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
				String time = formatTime(effect.getDuration());
				String line = name + roman(effect.getAmplifier()) + "  " + time;
				int width = client.font.width(line);
				boolean shortTime = effect.getDuration() > 0 && effect.getDuration() / 20 <= 5;
				int color = shortTime ? RED : WHITE;
				g.fill(rightX - width - 6, y - 1, rightX, y + 9, MODULE_BG);
				g.drawString(client.font, line, rightX - width - 3, y, color, false);
				y += 11;
			}
		}

		DeathCoordsModule death = (DeathCoordsModule) modules.find("DeathCoords");
		if (death != null && death.isEnabled()) {
			String line = death.getHudLine(client);
			if (!line.isEmpty()) {
				int width = client.font.width(line);
				g.fill(rightX - width - 6, y - 1, rightX, y + 9, MODULE_BG);
				g.drawString(client.font, line, rightX - width - 3, y, RED, false);
				y += 11;
			}
		}

		CoordConvertModule convert = (CoordConvertModule) modules.find("CoordConvert");
		if (convert != null && convert.isEnabled()) {
			String line = convert.getInfo(client);
			if (!line.isEmpty()) {
				int width = client.font.width(line);
				g.fill(rightX - width - 6, y - 1, rightX, y + 9, MODULE_BG);
				g.drawString(client.font, line, rightX - width - 3, y, GRAY, false);
				y += 11;
			}
		}
		return y;
	}

	private void renderModuleList(GuiGraphics g, Minecraft client, ModuleManager modules) {
		List<Module> enabled = new ArrayList<>();
		for (Module module : modules.getModules()) {
			if (module.isEnabled() && !"ClickGUI".equals(module.getName())) {
				enabled.add(module);
			}
		}
		if (enabled.isEmpty()) {
			return;
		}

		int maxWidth = 0;
		for (Module module : enabled) {
			String label = module.getName() + modeSuffix(module) + keySuffix(module);
			maxWidth = Math.max(maxWidth, client.font.width(label) + 18);
		}

		int x = 4;
		int y = 26;
		int rowHeight = client.font.lineHeight + 6;
		for (Module module : enabled) {
			g.fill(x, y, x + maxWidth + 4, y + rowHeight, MODULE_BG);
			g.fill(x, y, x + 2, y + rowHeight, ACCENT);
			g.fill(x + 8, y + rowHeight / 2 - 2, x + 12, y + rowHeight / 2 + 2, ACCENT);

			// Name
			int nameColor = WHITE;
			int nameX = x + 16;
			g.drawString(client.font, Component.literal(module.getName()), nameX, y + 3, nameColor, false);

			// Mode suffix (e.g. "· Silent") — shows what the assist is doing
			int cursorX = nameX + client.font.width(module.getName());
			String modeText = modeSuffix(module);
			if (!modeText.isEmpty()) {
				g.drawString(client.font, Component.literal(modeText), cursorX, y + 3, ACCENT, false);
				cursorX += client.font.width(modeText);
			}

			// Key suffix
			String key = module.getKeyLabel();
			String keyText = key.isEmpty() || "None".equals(key) ? "" : key;
			if (!keyText.isEmpty()) {
				int keyX = x + maxWidth - client.font.width(keyText) - 3;
				g.drawString(client.font, Component.literal(keyText), keyX, y + 3, GRAY, false);
			}

			moduleRects.add(new int[]{x, y, x + maxWidth + 4, y + rowHeight});
			moduleRowModules.add(module);
			y += rowHeight;
		}
	}

	/** Shows the active mode of assist modules, e.g. "· Silent". */
	private String modeSuffix(Module module) {
		Setting<?> mode = module.getSetting("mode");
		if (mode == null) {
			return "";
		}
		String v = String.valueOf(mode.getValue());
		// Only show when it's a meaningful non-default option
		if (v.isEmpty() || "Default".equals(v)) {
			return "";
		}
		return " \u00b7 " + v;
	}

	private String keySuffix(Module module) {
		String key = module.getKeyLabel();
		return key.isEmpty() || "None".equals(key) ? "" : "  " + key;
	}

	private void renderKeystrokes(GuiGraphics g, Minecraft client, ModuleManager modules) {
		KeystrokesModule keystrokes = (KeystrokesModule) modules.find("Keystrokes");
		if (keystrokes == null || !keystrokes.isEnabled()) {
			return;
		}

		int size = 22;
		int gap = 2;
		int startX = 6;
		int startY = client.getWindow().getGuiScaledHeight() - 6 - size * 2 - gap - size / 2;

		// WASD
		drawKey(g, client, keystrokes, KeystrokesModule.KEY_W, startX + size + gap, startY, size, size, "W");
		int rowY = startY + size + gap;
		drawKey(g, client, keystrokes, KeystrokesModule.KEY_A, startX, rowY, size, size, "A");
		drawKey(g, client, keystrokes, KeystrokesModule.KEY_S, startX + size + gap, rowY, size, size, "S");
		drawKey(g, client, keystrokes, KeystrokesModule.KEY_D, startX + (size + gap) * 2, rowY, size, size, "D");
		// Space bar
		int spaceY = rowY + size + gap;
		drawKey(g, client, keystrokes, KeystrokesModule.KEY_SPACE, startX, spaceY, size * 3 + gap * 2, size / 2, "");

		// Mouse + CPS
		int mouseX = startX + (size + gap) * 3 + 10;
		drawKey(g, client, keystrokes, KeystrokesModule.MOUSE_L, mouseX, rowY, 34, size, "L " + keystrokes.getCps(client, KeystrokesModule.MOUSE_L));
		drawKey(g, client, keystrokes, KeystrokesModule.MOUSE_R, mouseX + 36, rowY, 34, size, "R " + keystrokes.getCps(client, KeystrokesModule.MOUSE_R));
	}

	private void drawKey(GuiGraphics g, Minecraft client, KeystrokesModule keystrokes,
						 int key, int x, int y, int width, int height, String label) {
		boolean down = keystrokes.isKeyDown(client, key);
		int bg = down ? ACCENT : MODULE_BG;
		g.fill(x, y, x + width, y + height, bg);
		if (down) {
			g.fill(x, y, x + width, y + 1, 0xFF2E7D4F);
		}
		int color = down ? 0xFF0B1B12 : WHITE;
		Component text = Component.literal(label);
		int textWidth = client.font.width(text);
		g.drawString(client.font, text,
				x + (width - textWidth) / 2,
				y + (height - client.font.lineHeight) / 2,
				color, false);
	}

	private void renderDurabilityWarn(GuiGraphics g, Minecraft client, ModuleManager modules) {
		DurabilityWarnModule warn = (DurabilityWarnModule) modules.find("DurabilityWarn");
		if (warn == null || !warn.isEnabled() || !warn.isWarning(client)) {
			return;
		}
		Component text = Component.literal("\u26a0 LOW TOOL DURABILITY");
		int width = client.font.width(text);
		int x = (client.getWindow().getGuiScaledWidth() - width) / 2;
		int y = 24;
		g.fill(x - 6, y - 2, x + width + 6, y + 10, 0xCC4A0000);
		g.drawString(client.font, text, x, y, RED, false);
	}

	/** Poll-based click detection: left-click on an enabled module row toggles it. */
	public void handleClick(Minecraft client) {
		if (client.screen != null || client.player == null) {
			lastClickState = false;
			return;
		}
		long handle = client.getWindow().getWindow();
		boolean nowDown = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (nowDown && !lastClickState) {
			double scale = client.getWindow().getGuiScale();
			double mouseX = client.mouseHandler.xpos() / scale;
			double mouseY = client.mouseHandler.ypos() / scale;
			for (int i = 0; i < moduleRects.size(); i++) {
				int[] rect = moduleRects.get(i);
				if (mouseX >= rect[0] && mouseX <= rect[2] && mouseY >= rect[1] && mouseY <= rect[3]) {
					moduleRowModules.get(i).toggle();
					QynlClient.getInstance().getModuleManager().saveToConfig();
					break;
				}
			}
		}
		lastClickState = nowDown;
	}

	private String formatTime(int ticks) {
		if (ticks <= 0) {
			return "0:00";
		}
		int seconds = (ticks + 19) / 20;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private String roman(int amplifier) {
		return switch (amplifier) {
			case 0 -> "";
			case 1 -> " II";
			case 2 -> " III";
			case 3 -> " IV";
			default -> " V";
		};
	}
}

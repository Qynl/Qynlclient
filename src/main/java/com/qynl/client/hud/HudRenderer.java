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
import com.qynl.client.module.modules.StreamerModeModule;
import com.qynl.client.module.modules.TargetInfoModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HUD renderer for 1.21.1 — clean, single-panel design.
 *
 * <p>Top-left: the title and one soft panel holding every enabled module,
 * each row clickable (hover highlights the row). Right side: one soft panel
 * per info section (InfoHUD, TargetInfo, EffectTimers, DeathCoords,
 * CoordConvert) with an accent edge instead of per-line boxes. Everything is
 * hidden while StreamerMode is active so recordings stay OBS-safe.</p>
 */
public class HudRenderer {
	public static final int PANEL = 0x40000000;
	public static final int PANEL_HOVER = 0x14FFFFFF;
	public static final int ACCENT = 0xFF6EE7A0;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFF9CA3AF;
	public static final int RED = 0xFFFF6B6B;
	public static final int RED_BG = 0xB33D0000;

	private final List<int[]> moduleRects = new ArrayList<>();
	private final List<Module> moduleRowModules = new ArrayList<>();
	private boolean lastClickState = false;

	public void render(GuiGraphics guiGraphics, Minecraft client) {
		moduleRects.clear();
		moduleRowModules.clear();

		ModuleManager modules = QynlClient.getInstance().getModuleManager();

		boolean hideList = StreamerModeModule.shouldHide("all");
		boolean hideHud = StreamerModeModule.shouldHide("hud");

		if (!hideList) {
			renderTitle(guiGraphics, client);
			renderModuleList(guiGraphics, client, modules);
		}
		if (!hideHud) {
			renderRightColumn(guiGraphics, client, modules);
			renderKeystrokes(guiGraphics, client, modules);
			renderDurabilityWarn(guiGraphics, client, modules);
		}
	}

	private void renderTitle(GuiGraphics g, Minecraft client) {
		Component title = Component.literal("QynlClient");
		g.drawString(client.font, title, 4, 4, ACCENT, true);
		g.drawString(client.font, Component.literal("v" + QynlClient.VERSION),
				4 + client.font.width(title) + 6, 6, GRAY, false);
	}

	private int renderRightColumn(GuiGraphics g, Minecraft client, ModuleManager modules) {
		int y = 18;
		int rightX = client.getWindow().getGuiScaledWidth() - 6;

		InfoHudModule info = (InfoHudModule) modules.find("InfoHUD");
		if (info != null && info.isEnabled()) {
			List<String> lines = new ArrayList<>();
			for (String line : List.of(
					info.fps(client), info.coords(client), info.direction(client),
					info.biome(client), info.time(client), info.ping(client))) {
				if (!line.isEmpty()) {
					lines.add(line);
				}
			}
			y = drawRightSection(g, client, lines, WHITE, rightX, y) + 5;
		}

		TargetInfoModule target = (TargetInfoModule) modules.find("TargetInfo");
		if (target != null && target.isEnabled()) {
			String line = target.getInfo(client);
			if (!line.isEmpty()) {
				y = drawRightSection(g, client, List.of(line), ACCENT, rightX, y) + 5;
			}
		}

		EffectTimersModule effects = (EffectTimersModule) modules.find("EffectTimers");
		if (effects != null && effects.isEnabled()) {
			List<String> lines = new ArrayList<>();
			List<Integer> colors = new ArrayList<>();
			for (MobEffectInstance effect : effects.getEffects(client)) {
				String name = Component.translatable(effect.getEffect().value().getDescriptionId()).getString();
				String line = name + roman(effect.getAmplifier()) + "  " + formatTime(effect.getDuration());
				boolean shortTime = effect.getDuration() > 0 && effect.getDuration() / 20 <= 5;
				lines.add(line);
				colors.add(shortTime ? RED : WHITE);
			}
			y = drawRightSection(g, client, lines, colors, rightX, y) + 5;
		}

		DeathCoordsModule death = (DeathCoordsModule) modules.find("DeathCoords");
		if (death != null && death.isEnabled()) {
			String line = death.getHudLine(client);
			if (!line.isEmpty()) {
				y = drawRightSection(g, client, List.of(line), RED, rightX, y) + 5;
			}
		}

		CoordConvertModule convert = (CoordConvertModule) modules.find("CoordConvert");
		if (convert != null && convert.isEnabled()) {
			String line = convert.getInfo(client);
			if (!line.isEmpty()) {
				y = drawRightSection(g, client, List.of(line), GRAY, rightX, y) + 5;
			}
		}
		return y;
	}

	/** Draws one soft panel with right-aligned lines and a left accent edge. */
	private int drawRightSection(GuiGraphics g, Minecraft client, List<String> lines, int color, int rightX, int y) {
		if (lines.isEmpty()) {
			return y;
		}
		return drawRightSection(g, client, lines, Collections.nCopies(lines.size(), color), rightX, y);
	}

	private int drawRightSection(GuiGraphics g, Minecraft client, List<String> lines, List<Integer> colors, int rightX, int y) {
		if (lines.isEmpty()) {
			return y;
		}
		int maxWidth = 0;
		for (String line : lines) {
			maxWidth = Math.max(maxWidth, client.font.width(line));
		}
		int pad = 7;
		int lineH = 11;
		int panelW = maxWidth + pad * 2;
		int panelH = lines.size() * lineH + 4;
		int panelX = rightX - panelW;
		g.fill(panelX, y, rightX + 2, y + panelH, PANEL);
		g.fill(panelX, y, panelX + 2, y + panelH, ACCENT);
		for (int i = 0; i < lines.size(); i++) {
			g.drawString(client.font, lines.get(i), panelX + pad, y + 3 + i * lineH, colors.get(i), false);
		}
		return y + panelH;
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
			String label = module.getName() + modeSuffix(module);
			String key = module.getKeyLabel();
			int w = client.font.width(label);
			if (!key.isEmpty() && !"None".equals(key)) {
				w = Math.max(w, client.font.width(key) + 4);
			}
			maxWidth = Math.max(maxWidth, w);
		}

		int x = 4;
		int y = 22;
		int rowHeight = 11;
		int padX = 7;
		int panelW = maxWidth + padX * 2 + 2;
		int panelH = enabled.size() * rowHeight + 4;

		g.fill(x, y, x + panelW, y + panelH, PANEL);
		g.fill(x, y, x + 2, y + panelH, ACCENT);

		double scale = client.getWindow().getGuiScale();
		int mouseX = (int) (client.mouseHandler.xpos() / scale);
		int mouseY = (int) (client.mouseHandler.ypos() / scale);

		int rowY = y + 2;
		for (Module module : enabled) {
			if (mouseX >= x && mouseX < x + panelW && mouseY >= rowY - 1 && mouseY < rowY + rowHeight - 1) {
				g.fill(x, rowY - 1, x + panelW, rowY + rowHeight - 1, PANEL_HOVER);
			}

			int nameX = x + padX + 2;
			g.drawString(client.font, Component.literal(module.getName()), nameX, rowY, WHITE, false);

			int cursorX = nameX + client.font.width(module.getName());
			String modeText = modeSuffix(module);
			if (!modeText.isEmpty()) {
				g.drawString(client.font, Component.literal(modeText), cursorX, rowY, ACCENT, false);
			}

			String key = module.getKeyLabel();
			if (!key.isEmpty() && !"None".equals(key)) {
				int keyX = x + panelW - padX - client.font.width(key);
				g.drawString(client.font, Component.literal(key), keyX, rowY, GRAY, false);
			}

			moduleRects.add(new int[]{x, rowY - 1, x + panelW, rowY + rowHeight - 1});
			moduleRowModules.add(module);
			rowY += rowHeight;
		}
	}

	/** Shows the active mode of assist modules, e.g. " · Silent". */
	private String modeSuffix(Module module) {
		Setting<?> mode = module.getSetting("mode");
		if (mode == null) {
			return "";
		}
		String v = String.valueOf(mode.getValue());
		if (v.isEmpty() || "Default".equals(v)) {
			return "";
		}
		return " \u00b7 " + v;
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
		int bg = down ? ACCENT : PANEL;
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
		g.fill(x - 6, y - 2, x + width + 6, y + 10, RED_BG);
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

package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGuiScreen — the in-game module manager.
 *
 * <p>Left-click a module to toggle it on/off.
 * Right-click a module row to change its keybind: a "Press a key\u2026"
 * indicator appears, and the next valid key press becomes the new toggle key.
 * Press Esc / Backspace / Delete to remove the keybind.</p>
 */
public class ClickGuiScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int COL_WIDTH = 260;
	private static final int START_Y = 40;

	private final List<Module> rows = new ArrayList<>();
	private final List<Integer> rowY = new ArrayList<>();
	private Module waitingForBind = null;

	public ClickGuiScreen() {
		super(Component.literal("QynlClient \u2014 Module Manager"));
	}

	@Override
	protected void init() {
		rows.clear();
		rowY.clear();
		waitingForBind = null;

		int centerX = this.width / 2;
		int y = START_Y;
		ModuleManager modules = QynlClient.getInstance().getModuleManager();

		for (Category category : Category.values()) {
			List<Module> categoryModules = modules.getModules().stream()
					.filter(m -> m.getCategory() == category).toList();
			if (categoryModules.isEmpty()) {
				continue;
			}
			StringWidget header = new StringWidget(
					Component.literal(category.getLabel()).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN),
					this.font);
			header.setX(centerX - COL_WIDTH / 2);
			header.setY(y);
			this.addRenderableWidget(header);
			y += 14;

			for (Module module : categoryModules) {
				rows.add(module);
				rowY.add(y);
				final Module m = module;
				this.addRenderableWidget(Button.builder(
						rowLabel(m),
						b -> { /* left-click = toggle — handled in mouseClicked below */ })
						.bounds(centerX - COL_WIDTH / 2, y, COL_WIDTH, ROW_HEIGHT - 2).build());
				y += ROW_HEIGHT;
			}
			y += 6;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Keybinds\u2026"), b ->
						this.minecraft.setScreen(new KeybindScreen()))
				.bounds(centerX - 120, y + 10, 76, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Settings\u2026"), b ->
						this.minecraft.setScreen(new ModuleSettingsScreen()))
				.bounds(centerX - 40, y + 10, 76, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
				.bounds(centerX + 40, y + 10, 76, 20).build());
	}

	private Component rowLabel(Module m) {
		String name = m.getName();
		String state = m.isEnabled() ? "ON" : "OFF";
		String key = m.getKeyLabel();
		if (key.isEmpty() || "None".equals(key)) {
			key = "";
		} else {
			key = "[" + key + "]";
		}
		if (waitingForBind == m) {
			key = "\u00ab Press a key\u2026 \u00bb";
		}
		int pad = Math.max(0, 28 - name.length());
		return Component.literal(name + "  " + state + spaces(pad) + key);
	}

	private static String spaces(int n) {
		return " ".repeat(Math.max(0, n));
	}

	private void refreshButtons() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		guiGraphics.drawCenteredString(this.font,
				Component.literal("Left-click = toggle  \u00b7  Right-click = rebind key  \u00b7  right-click outside = close"),
				this.width / 2, 28, 0xFF9CA3AF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int centerX = this.width / 2;
		int halfW = COL_WIDTH / 2;

		if (button == 1) {
			// Right-click: find the module row under the cursor → start rebind.
			for (int i = 0; i < rows.size(); i++) {
				int ry = rowY.get(i);
				if (mouseX >= centerX - halfW && mouseX <= centerX + halfW
						&& mouseY >= ry && mouseY <= ry + ROW_HEIGHT - 2) {
					waitingForBind = rows.get(i);
					refreshButtons();
					return true;
				}
			}
			// Right-click outside any row → close the GUI.
			this.onClose();
			return true;
		}

		// Left-click: toggle the module under the cursor.
		if (button == 0) {
			for (int i = 0; i < rows.size(); i++) {
				int ry = rowY.get(i);
				if (mouseX >= centerX - halfW && mouseX <= centerX + halfW
						&& mouseY >= ry && mouseY <= ry + ROW_HEIGHT - 2) {
					Module m = rows.get(i);
					m.toggle();
					QynlClient.getInstance().getModuleManager().saveToConfig();
					refreshButtons();
					return true;
				}
			}
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (waitingForBind != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE
					|| keyCode == GLFW.GLFW_KEY_BACKSPACE
					|| keyCode == GLFW.GLFW_KEY_DELETE) {
				// Clear the keybind.
				waitingForBind.setKeyCode(-1);
			} else if (keyCode > 0 && !isGameCriticalKey(keyCode)) {
				waitingForBind.setKeyCode(keyCode);
			}
			QynlClient.getInstance().getModuleManager().saveToConfig();
			waitingForBind = null;
			refreshButtons();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Only a handful of keys the game absolutely needs stay reserved.
	 *  Everything else (including F-keys and numbers) is available so
	 *  every player can find a key that works for them. */
	private boolean isGameCriticalKey(int keyCode) {
		return keyCode == GLFW.GLFW_KEY_ENTER
				|| keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| keyCode == GLFW.GLFW_KEY_SLASH
				|| keyCode == GLFW.GLFW_KEY_TAB
				|| keyCode == GLFW.GLFW_KEY_F1   // debug / shift-F1 pie chart
				|| keyCode == GLFW.GLFW_KEY_F2;  // always reserved for the game
	}

	@Override
	public void onClose() {
		waitingForBind = null;
		super.onClose();
		Module clickGui = QynlClient.getInstance().getModuleManager().find("ClickGUI");
		if (clickGui != null && clickGui.isEnabled()) {
			clickGui.setEnabled(false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

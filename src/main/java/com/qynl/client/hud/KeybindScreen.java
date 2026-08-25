package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * KeybindScreen — the in-game keybind editor.
 * Click a module row, then press the key you want to use. Press
 * Esc / Backspace / Delete to remove the keybind. Changes are saved
 * instantly to the config.
 */
public class KeybindScreen extends Screen {
	private static final int ROW_HEIGHT = 22;
	private static final int START_Y = 44;

	private final List<Module> rows = new ArrayList<>();
	private Module waitingModule = null;

	public KeybindScreen() {
		super(Component.literal("QynlClient — Keybinds"));
	}

	@Override
	protected void init() {
		rows.clear();
		waitingModule = null;
		ModuleManager modules = QynlClient.getInstance().getModuleManager();
		int centerX = this.width / 2;
		int y = START_Y;

		StringWidget hint = new StringWidget(
				Component.literal("Click a module, then press the key. Esc / right-click / Backspace = no key."),
				this.font);
		hint.setX(centerX - 150);
		hint.setY(28);
		this.addRenderableWidget(hint);

		for (Module module : modules.getModules()) {
			rows.add(module);
			this.addRenderableWidget(Button.builder(Component.literal(""), b -> startBinding(module))
					.bounds(centerX - 150, y, 300, 20).build());
			y += ROW_HEIGHT;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
				.bounds(centerX - 60, y + 8, 120, 20).build());
	}

	private void startBinding(Module module) {
		waitingModule = module;
	}

	private void setBind(Module module, int keyCode) {
		module.setKeyCode(keyCode);
		waitingModule = null;
		QynlClient.getInstance().getModuleManager().saveToConfig();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (waitingModule != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE
					|| keyCode == GLFW.GLFW_KEY_BACKSPACE
					|| keyCode == GLFW.GLFW_KEY_DELETE) {
				setBind(waitingModule, -1);
			} else if (keyCode > 0 && !isModifierKey(keyCode) && !isGameCriticalKey(keyCode)) {
				setBind(waitingModule, keyCode);
			}
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private boolean isModifierKey(int keyCode) {
		return keyCode >= GLFW.GLFW_KEY_LEFT_SHIFT && keyCode <= GLFW.GLFW_KEY_LAST;
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
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		for (int i = 0; i < rows.size(); i++) {
			Module module = rows.get(i);
			int y = START_Y + i * ROW_HEIGHT;
			guiGraphics.drawString(this.font, Component.literal(module.getName()),
					centerX - 142, y + 6, 0xFFFFFFFF, false);
			if (waitingModule == module) {
				guiGraphics.drawCenteredString(this.font,
						Component.literal("Press a key… (Esc = none)"),
						centerX, y + 6, HudRenderer.ACCENT);
			} else {
				guiGraphics.drawString(this.font, Component.literal("[" + module.getKeyLabel() + "]"),
						centerX + 150 - this.font.width("[" + module.getKeyLabel() + "]"), y + 6,
						module.getKeyMapping() != null && module.getKeyMapping().isUnbound() ? 0xFF6B7280 : 0xFF9CA3AF,
						false);
			}
		}
	}

	/** Right-clicking a module row removes its keybind (leaves it unbound). */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1) {
			int centerX = this.width / 2;
			for (int i = 0; i < rows.size(); i++) {
				double rowY = START_Y + i * ROW_HEIGHT;
				if (mouseX >= centerX - 150 && mouseX <= centerX + 150
						&& mouseY >= rowY && mouseY <= rowY + 20) {
					setBind(rows.get(i), -1);
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		waitingModule = null;
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

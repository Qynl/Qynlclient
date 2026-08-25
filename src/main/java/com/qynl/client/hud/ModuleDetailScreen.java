package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * ModuleDetailScreen — the bigger per-module panel.
 *
 * <p>Opened by right-clicking a module row in the ClickGUI. Shows the
 * module name, its description, the on/off state, the keybind (with
 * set/clear), and every setting of that module. Click a setting to
 * cycle its value; click "Set key" then press a key to rebind.</p>
 */
public class ModuleDetailScreen extends Screen {
	private static final int PANEL_W = 320;
	private static final int ROW_H = 20;
	private static final int GAP = 4;
	private static final int PANEL_BG = 0xC0121212;

	private final Module module;
	private boolean waitingForKey = false;

	public ModuleDetailScreen(Module module) {
		super(Component.literal("Module — " + module.getName()));
		this.module = module;
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = 48;
		int panelLeft = centerX - PANEL_W / 2;

		// Toggle button
		this.addRenderableWidget(Button.builder(componentForState(), b -> {
					module.toggle();
					QynlClient.getInstance().getModuleManager().saveToConfig();
					refresh();
				})
				.bounds(panelLeft, y, PANEL_W, ROW_H + 6).build());
		y += ROW_H + 6 + GAP;

		// Keybind row
		this.addRenderableWidget(Button.builder(
						waitingForKey ? Component.literal("Press a key… (Esc = none)")
								: Component.literal("Keybind: " + bindLabel()),
						b -> waitingForKey = true)
				.bounds(panelLeft, y, PANEL_W / 2 - 4, ROW_H).build());
		this.addRenderableWidget(Button.builder(Component.literal("Clear"),
						b -> {
							module.setKeyCode(-1);
							waitingForKey = false;
							QynlClient.getInstance().getModuleManager().saveToConfig();
							refresh();
						})
				.bounds(panelLeft + PANEL_W / 2 + 4, y, PANEL_W / 2 - 4, ROW_H).build());
		y += ROW_H + GAP + 6;

		// Settings rows
		for (Setting<?> setting : module.getSettings()) {
			final Setting<?> s = setting;
			this.addRenderableWidget(Button.builder(
							Component.literal(s.getLabel() + ": " + s.displayString()),
							b -> {
								s.cycle();
								QynlClient.getInstance().getModuleManager().saveToConfig();
								refresh();
							})
					.bounds(panelLeft, y, PANEL_W, ROW_H).build());
			y += ROW_H + GAP;
		}
		if (!module.hasSettings()) {
			this.addRenderableWidget(Button.builder(Component.literal("No settings for this module"),
							b -> {})
					.bounds(panelLeft, y, PANEL_W, ROW_H).build());
			y += ROW_H + GAP;
		}

		y += 6;
		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> goBack())
				.bounds(centerX - 60, y, 120, ROW_H).build());
	}

	/** Returns to the ClickGUI module list instead of closing the game screen. */
	private void goBack() {
		waitingForKey = false;
		if (this.minecraft != null) {
			this.minecraft.setScreen(new ClickGuiScreen());
		} else {
			super.onClose();
		}
	}

	private Component componentForState() {
		String state = module.isEnabled() ? "ON" : "OFF";
		return Component.literal("[" + (module.isEnabled() ? "ON" : "OFF") + "]  "
				+ module.getName());
	}

	private String bindLabel() {
		String label = module.getKeyLabel();
		return (label == null || label.isEmpty() || "None".equals(label)) ? "None" : label;
	}

	private void refresh() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (waitingForKey) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE
					|| keyCode == GLFW.GLFW_KEY_BACKSPACE
					|| keyCode == GLFW.GLFW_KEY_DELETE) {
				module.setKeyCode(-1);
			} else if (keyCode > 0 && !isGameCriticalKey(keyCode)) {
				module.setKeyCode(keyCode);
			}
			waitingForKey = false;
			QynlClient.getInstance().getModuleManager().saveToConfig();
			refresh();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	/** Keep a handful of keys the game needs reserved; everything else is free. */
	private boolean isGameCriticalKey(int keyCode) {
		return keyCode == GLFW.GLFW_KEY_ENTER
				|| keyCode == GLFW.GLFW_KEY_KP_ENTER
				|| keyCode == GLFW.GLFW_KEY_SLASH
				|| keyCode == GLFW.GLFW_KEY_TAB
				|| keyCode == GLFW.GLFW_KEY_F1
				|| keyCode == GLFW.GLFW_KEY_F2;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

		int centerX = this.width / 2;
		int panelLeft = centerX - PANEL_W / 2;

		// Header
		guiGraphics.drawCenteredString(this.font,
				Component.literal(module.getName()).withStyle(ChatFormatting.BOLD),
				centerX, 14, module.isEnabled() ? HudRenderer.ACCENT : 0xFFFFFFFF);
		guiGraphics.drawCenteredString(this.font,
				Component.literal(module.getDescription()).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY),
				centerX, 28, 0xFF9CA3AF);

		// Panel background
		guiGraphics.fill(panelLeft - 8, 40, centerX + PANEL_W / 2 + 8, this.height - 24, PANEL_BG);

		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1) {
			// Right-click anywhere = go back to the module list
			goBack();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}

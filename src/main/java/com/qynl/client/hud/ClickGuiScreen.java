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

import java.util.List;

public class ClickGuiScreen extends Screen {
	public ClickGuiScreen() {
		super(Component.literal("QynlClient — Module Manager"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int y = 40;
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
			header.setX(centerX - 120);
			header.setY(y);
			this.addRenderableWidget(header);
			y += 14;

			for (Module module : categoryModules) {
				final Module m = module;
				Component label = Component.literal(
						m.getName() + "  [" + (m.isEnabled() ? "ON" : "OFF") + "]" + keyLabel(m));
				this.addRenderableWidget(Button.builder(label, b -> {
					m.toggle();
					modules.saveToConfig();
					refreshButtons();
				}).bounds(centerX - 120, y, 240, 20).build());
				y += 22;
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

	private void refreshButtons() {
		this.clearWidgets();
		this.init();
	}

	private String keyLabel(Module module) {
		if (module.getKeyMapping() == null) {
			return "";
		}
		return "  [" + module.getKeyLabel() + "]";
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
		guiGraphics.drawCenteredString(this.font,
				Component.literal("Click a module to toggle it \u00b7 Keybinds\u2026 / Settings\u2026 below \u00b7 right-click to close"),
				this.width / 2, 28, 0xFF9CA3AF);
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 1) {
			this.onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
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

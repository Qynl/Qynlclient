package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleSettingsScreen — the in-game settings editor.
 * Every module with options (mode, percentages, clicks per second, …) is
 * listed here; click a value to change it. Changes are saved instantly.
 */
public class ModuleSettingsScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private static final int START_Y = 44;
	private static final int COL_WIDTH = 300;

	private final List<Module> rows = new ArrayList<>();

	public ModuleSettingsScreen() {
		super(Component.literal("QynlClient — Module Settings"));
	}

	@Override
	protected void init() {
		rows.clear();
		ModuleManager modules = QynlClient.getInstance().getModuleManager();
		int centerX = this.width / 2;
		int y = START_Y;

		StringWidget hint = new StringWidget(
				Component.literal("Click a value to change it — saved automatically."), this.font);
		hint.setX(centerX - COL_WIDTH / 2);
		hint.setY(28);
		this.addRenderableWidget(hint);

		for (Module module : modules.getModules()) {
			if (!module.hasSettings()) {
				continue;
			}
			rows.add(module);
			StringWidget header = new StringWidget(
					Component.literal(module.getName()).withStyle(ChatFormatting.BOLD, ChatFormatting.GREEN),
					this.font);
			header.setX(centerX - COL_WIDTH / 2);
			header.setY(y);
			this.addRenderableWidget(header);
			y += 13;

			for (Setting<?> setting : module.getSettings()) {
				final Setting<?> s = setting;
				this.addRenderableWidget(Button.builder(
						Component.literal(s.getLabel() + ": " + s.displayString()),
						b -> {
							s.cycle();
							modules.saveToConfig();
							refresh();
						}).bounds(centerX - COL_WIDTH / 2, y, COL_WIDTH, ROW_HEIGHT - 2).build());
				y += ROW_HEIGHT;
			}
			y += 5;
		}

		this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.onClose())
				.bounds(centerX - 60, y + 8, 120, 20).build());
	}

	private void refresh() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
		guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
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
	public boolean isPauseScreen() {
		return false;
	}
}

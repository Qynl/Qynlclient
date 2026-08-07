package com.qynl.client189;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGuiScreen for 1.8.9 — the in-game module manager.
 *
 * <p>Left-click a module to toggle it on/off.
 * Right-click a module row to open its detail panel — the bigger view
 * with the module's description, toggle, keybind editor and all settings.
 * Right-click outside any row closes the GUI.</p>
 */
public class ClickGuiScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int COL_WIDTH = 220;
    private static final int START_Y = 35;

    private final List<Module> rows = new ArrayList<>();
    private final List<Integer> rowY = new ArrayList<>();

    public ClickGuiScreen() { super(); }

    @Override
    public void init() {
        rows.clear();
        rowY.clear();
        this.buttons.clear();

        int centerX = this.width / 2;
        int y = START_Y + 14;
        ModuleManager modules = QynlClient189.getInstance().getModuleManager();

        for (Category category : Category.values()) {
            List<Module> catModules = new ArrayList<>();
            for (Module m : modules.getModules()) {
                if (m.getCategory() == category) catModules.add(m);
            }
            if (catModules.isEmpty()) continue;
            y += 2;

            for (Module module : catModules) {
                rows.add(module);
                rowY.add(y);
                String label = module.getName() + "  " + (module.isEnabled() ? "ON" : "OFF");
                this.buttons.add(new ButtonWidget(rows.size() - 1, centerX - COL_WIDTH / 2, y, COL_WIDTH, ROW_HEIGHT - 2, label));
                y += ROW_HEIGHT;
            }
            y += 4;
        }

        this.buttons.add(new ButtonWidget(999, centerX - 40, y + 8, 80, 20, "Close (RShift)"));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 999) {
            MinecraftClient.getInstance().openScreen(null);
            return;
        }
        if (button.id >= 0 && button.id < rows.size()) {
            Module m = rows.get(button.id);
            m.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            init();
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_RSHIFT || keyCode == Keyboard.KEY_ESCAPE) {
            MinecraftClient.getInstance().openScreen(null);
            return;
        }
        super.keyPressed(character, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        int centerX = this.width / 2;
        int halfW = COL_WIDTH / 2;

        if (button == 1) {
            // Right-click: open the detail panel for the module under the cursor.
            for (int i = 0; i < rows.size(); i++) {
                int ry = rowY.get(i);
                if (mouseX >= centerX - halfW && mouseX <= centerX + halfW
                        && mouseY >= ry && mouseY <= ry + ROW_HEIGHT - 2) {
                    MinecraftClient.getInstance().openScreen(new ModuleDetailScreen189(rows.get(i)));
                    return;
                }
            }
            // Right-click outside any row → close the GUI.
            MinecraftClient.getInstance().openScreen(null);
            return;
        }

        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        int centerX = this.width / 2;
        this.drawCenteredString(this.textRenderer, "QynlClient 1.8.9 - Module Manager", centerX, 12, 0x55FF55);
        this.drawCenteredString(this.textRenderer, "Left-click = toggle  |  Right-click = module settings  |  RShift = close", centerX, 24, 0xAAAAAA);

        int y = START_Y + 14;
        for (Category category : Category.values()) {
            List<Module> catModules = new ArrayList<>();
            for (Module m : QynlClient189.getInstance().getModuleManager().getModules()) {
                if (m.getCategory() == category) catModules.add(m);
            }
            if (catModules.isEmpty()) continue;
            this.drawCenteredString(this.textRenderer, "\u00a7a\u00a7l" + category.getLabel(), centerX, y, 0x55FF55);
            y += 14;
            for (int i = 0; i < catModules.size(); i++) y += ROW_HEIGHT;
            y += 4;
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}

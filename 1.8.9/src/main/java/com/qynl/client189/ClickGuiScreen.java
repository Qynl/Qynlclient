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

    /** How many pixels the module list is scrolled up by (keyboard scroll). */
    private int scrollOffset = 0;
    /** Maximum scroll offset (0 = list fits / at the top). */
    private int maxScroll = 0;

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
                int drawY = y - scrollOffset;
                rows.add(module);
                rowY.add(drawY);
                String label = module.getName() + "  " + (module.isEnabled() ? "ON" : "OFF");
                this.buttons.add(new ButtonWidget(rows.size() - 1, centerX - COL_WIDTH / 2, drawY, COL_WIDTH, ROW_HEIGHT - 2, label));
                y += ROW_HEIGHT;
            }
            y += 4;
        }

        // Bottom of the list (categories + close button) must stay reachable.
        maxScroll = Math.max(0, y - (this.height - 40));
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }

        this.buttons.add(new ButtonWidget(999, centerX - 40, y + 8 - scrollOffset, 80, 20, "Close (RShift)"));
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
        // Keyboard scrolling — the module list is long and 1.8.9 consumes
        // the mouse wheel for the hotbar.
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN
                || keyCode == Keyboard.KEY_PRIOR || keyCode == Keyboard.KEY_NEXT
                || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END) {
            int before = scrollOffset;
            if (keyCode == Keyboard.KEY_UP) {
                scrollOffset = Math.max(0, scrollOffset - ROW_HEIGHT);
            } else if (keyCode == Keyboard.KEY_DOWN) {
                scrollOffset = Math.min(maxScroll, scrollOffset + ROW_HEIGHT);
            } else if (keyCode == Keyboard.KEY_PRIOR) {
                scrollOffset = Math.max(0, scrollOffset - ROW_HEIGHT * 8);
            } else if (keyCode == Keyboard.KEY_NEXT) {
                scrollOffset = Math.min(maxScroll, scrollOffset + ROW_HEIGHT * 8);
            } else if (keyCode == Keyboard.KEY_HOME) {
                scrollOffset = 0;
            } else {
                scrollOffset = maxScroll;
            }
            if (scrollOffset != before) {
                init();
            }
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

package com.qynl.client189;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * ModuleDetailScreen189 — the bigger per-module panel for 1.8.9.
 *
 * <p>Opened by right-clicking a module row in the ClickGUI. Shows the
 * module description, an ON/OFF toggle, the keybind (set/clear) and
 * every setting of that module in one place.</p>
 */
public class ModuleDetailScreen189 extends Screen {
    private static final int PANEL_W = 240;
    private static final int ROW_H = 20;

    private final Module module;
    private boolean waitingForKey = false;
    private final List<Setting<?>> settings = new ArrayList<>();

    private static final int BTN_TOGGLE = 1;
    private static final int BTN_KEY = 2;
    private static final int BTN_CLEAR_KEY = 3;
    private static final int BTN_BACK = 100;
    private static final int BTN_SETTING_START = 50;

    public ModuleDetailScreen189(Module module) {
        super();
        this.module = module;
    }

    @Override
    public void init() {
        this.buttons.clear();
        settings.clear();

        int centerX = this.width / 2;
        int y = 42;

        // Toggle
        this.buttons.add(new ButtonWidget(BTN_TOGGLE, centerX - PANEL_W / 2, y, PANEL_W, ROW_H + 4,
                (module.isEnabled() ? "[ON]  " : "[OFF]  ") + module.getName()));
        y += ROW_H + 4 + 4;

        // Keybind
        String keyText = waitingForKey ? "Press a key... (Esc = none)"
                : "Keybind: " + bindLabel();
        this.buttons.add(new ButtonWidget(BTN_KEY, centerX - PANEL_W / 2, y, PANEL_W / 2 - 2, ROW_H, keyText));
        this.buttons.add(new ButtonWidget(BTN_CLEAR_KEY, centerX + 2, y, PANEL_W / 2 - 2, ROW_H, "Clear"));
        y += ROW_H + 8;

        // Settings
        for (Setting<?> s : module.getSettings()) {
            settings.add(s);
            this.buttons.add(new ButtonWidget(BTN_SETTING_START + settings.size() - 1,
                    centerX - PANEL_W / 2, y, PANEL_W, ROW_H,
                    s.getLabel() + ": " + s.displayString()));
            y += ROW_H + 2;
        }
        if (settings.isEmpty()) {
            this.buttons.add(new ButtonWidget(-1, centerX - PANEL_W / 2, y, PANEL_W, ROW_H,
                    "No settings for this module"));
            y += ROW_H + 2;
        }

        y += 6;
        this.buttons.add(new ButtonWidget(BTN_BACK, centerX - 40, y, 80, ROW_H, "Back"));
    }

    private String bindLabel() {
        String label = module.getKeyLabel();
        return (label == null || label.isEmpty() || "None".equals(label)) ? "None" : label;
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == BTN_BACK) {
            goBack();
            return;
        }
        if (button.id == BTN_TOGGLE) {
            module.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            init();
            return;
        }
        if (button.id == BTN_KEY) {
            waitingForKey = true;
            init();
            return;
        }
        if (button.id == BTN_CLEAR_KEY) {
            module.setKeyCode(-1);
            waitingForKey = false;
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            init();
            return;
        }
        if (button.id >= BTN_SETTING_START && button.id < BTN_SETTING_START + settings.size()) {
            Setting<?> s = settings.get(button.id - BTN_SETTING_START);
            s.cycle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            init();
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (waitingForKey) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK
                    || keyCode == Keyboard.KEY_DELETE) {
                module.setKeyCode(-1);
            } else if (keyCode > 0) {
                module.setKeyCode(keyCode);
            }
            waitingForKey = false;
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            init();
            return;
        }
        super.keyPressed(character, keyCode);
    }

    private void goBack() {
        MinecraftClient.getInstance().openScreen(new ClickGuiScreen());
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        int centerX = this.width / 2;

        // Header
        this.drawCenteredString(this.textRenderer,
                (module.isEnabled() ? "\u00a7a" : "\u00a7f\u00a7l") + module.getName(),
                centerX, 12, module.isEnabled() ? 0xFF6EE7A0 : 0xFFFFFFFF);
        this.drawCenteredString(this.textRenderer, "\u00a77" + module.getDescription(),
                centerX, 26, 0xFF9CA3AF);

        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 1) {
            goBack();
            return;
        }
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}

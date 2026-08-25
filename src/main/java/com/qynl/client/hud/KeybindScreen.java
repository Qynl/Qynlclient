package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Glassmorphism keybind editor. Click a module row, then press the key.
 * Esc / Backspace / Delete (or right-click) removes the keybind.
 */
public class KeybindScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int PANEL_W = 320;
    private static final int MIN_ROWS_VISIBLE = 8;

    private final List<Module> rows = new ArrayList<>();
    private Module waitingModule = null;
    private int scroll = 0;

    public KeybindScreen() {
        super(Component.literal("QynlClient \u2014 Keybinds"));
    }

    @Override
    protected void init() {
        rows.clear();
        waitingModule = null;
        rows.addAll(QynlClient.getInstance().getModuleManager().getModules());
    }

    private int panelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return 44;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x66000000);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, Glass.TEXT);
        g.drawCenteredString(this.font,
                "\u00a77Click a module, then press the key. Esc / right-click / Backspace = no key.",
                this.width / 2, 26, Glass.DIM);

        int visible = Math.min(rows.size(), maxVisibleRows());
        int ph = visible * ROW_H + 14;
        int x = panelX(), y = panelY();

        Glass.panel(g, x, y, PANEL_W, ph, 8.0F);

        int ry = y + 8 - scroll * ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            Module module = rows.get(i);
            if (ry + ROW_H < y || ry > y + ph) {
                ry += ROW_H;
                continue;
            }
            boolean hover = Glass.in(mx, my, x + 2, ry, PANEL_W - 4, ROW_H - 2);
            if (hover) {
                Glass.fillRound(g, x + 4, ry + 1, PANEL_W - 8, ROW_H - 2, 5.0F, Glass.HOVER, 0x1AFFFFFF);
            }
            g.drawString(this.font, module.getName(), x + 12, ry + 6, Glass.TEXT, true);

            if (waitingModule == module) {
                g.drawString(this.font, "Press a key\u2026  (Esc = none)",
                        x + PANEL_W - 12 - this.font.width("Press a key\u2026  (Esc = none)"),
                        ry + 6, Glass.ACCENT, true);
            } else {
                String key = module.getKeyLabel();
                boolean bound = key != null && !key.isEmpty() && !"None".equals(key);
                String label = bound ? "[" + key + "]" : "none";
                g.drawString(this.font, label,
                        x + PANEL_W - 12 - this.font.width(label), ry + 6,
                        bound ? Glass.DIM : Glass.OFF, true);
            }
            ry += ROW_H;
        }

        // Back pill.
        boolean backHover = Glass.in(mx, my, this.width / 2 - 60, y + ph + 12, 120, 18);
        Glass.pill(g, this.width / 2 - 60, y + ph + 12, 120, 18, false, backHover);
        g.drawCenteredString(this.font, "Back", this.width / 2, y + ph + 17,
                backHover ? Glass.TEXT : Glass.DIM);
    }

    private void setBind(Module module, int keyCode) {
        module.setKeyCode(keyCode);
        waitingModule = null;
        QynlClient.getInstance().getModuleManager().saveToConfig();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (waitingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_BACKSPACE
                    || key == GLFW.GLFW_KEY_DELETE) {
                setBind(waitingModule, -1);
            } else if (key > 0 && !isModifierKey(key) && !isGameCriticalKey(key)) {
                setBind(waitingModule, key);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private boolean isModifierKey(int key) {
        return key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_LAST;
    }

    /** A handful of keys the game absolutely needs stay reserved. */
    private boolean isGameCriticalKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_SLASH
                || key == GLFW.GLFW_KEY_TAB
                || key == GLFW.GLFW_KEY_F1
                || key == GLFW.GLFW_KEY_F2;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = panelX(), y = panelY() + 8 - scroll * ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            if (Glass.in((float) mx, (float) my, x + 2, y, PANEL_W - 4, ROW_H - 2)) {
                if (button == 0) {
                    waitingModule = rows.get(i);
                } else if (button == 1) {
                    setBind(rows.get(i), -1);
                }
                return true;
            }
            y += ROW_H;
        }

        int ph = Math.min(rows.size(), maxVisibleRows()) * ROW_H + 14;
        if (Glass.in((float) mx, (float) my, this.width / 2 - 60, panelY() + ph + 12, 120, 18)) {
            onClose();
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Rows that fit above the footer at the current window height. */
    private int maxVisibleRows() {
        int available = this.height - panelY() - 40;
        return Math.max(MIN_ROWS_VISIBLE, available / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (rows.size() > maxVisibleRows()) {
            scroll = Math.max(0, Math.min(rows.size() - maxVisibleRows(), scroll + (dy > 0 ? -1 : 1)));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

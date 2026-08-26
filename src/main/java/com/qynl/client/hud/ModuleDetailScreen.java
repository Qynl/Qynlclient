package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Glassmorphism per-module panel — toggle, keybind (set/clear) and every
 * setting of the module, each as a glass row.
 */
public class ModuleDetailScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int ROW_H = 20;
    private static final int GAP = 4;

    private final Module module;
    private boolean waitingForKey = false;
    private int scroll = 0;
    /** Setting currently being dragged on its slider (null when not dragging). */
    private Setting<?> draggingSetting = null;

    /** Rows: either a label+value pair or a button (toggle / keybind / clear / back). */
    private final List<Object[]> rows = new ArrayList<>(); // {type, setting|module, extra}

    public ModuleDetailScreen(Module module) {
        super(Component.literal("Module \u2014 " + module.getName()));
        this.module = module;
    }

    @Override
    protected void init() {
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        rows.add(new Object[]{"toggle"});
        rows.add(new Object[]{"keybind"});
        if (!module.hasSettings()) {
            rows.add(new Object[]{"nosettings"});
        } else {
            for (Setting<?> setting : module.getSettings()) {
                rows.add(new Object[]{"setting", setting});
            }
        }
        rows.add(new Object[]{"back"});
    }

    private int panelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return 56;
    }

    /** Rows that fit between the header and the bottom of the screen. */
    private int maxVisibleRows() {
        int available = this.height - panelY() - 24;
        return Math.max(3, available / (ROW_H + GAP));
    }

    private int panelH() {
        return Math.min(rows.size(), maxVisibleRows()) * (ROW_H + GAP) + 14;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x66000000);

        // Header: module name + description.
        g.drawCenteredString(this.font, module.getName(), this.width / 2, 12,
                module.isEnabled() ? Glass.ON : Glass.TEXT);
        g.drawCenteredString(this.font, module.getDescription(), this.width / 2, 26, Glass.DIM);

        int x = panelX(), y = panelY();
        Glass.panel(g, x, y, PANEL_W, panelH(), 8.0F);
        int ry = y + 8 - scroll * (ROW_H + GAP);

        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            String type = (String) row[0];
            if (ry + ROW_H < y || ry > y + panelH()) {
                ry += ROW_H + GAP;
                continue;
            }
            boolean hover = Glass.in(mx, my, x + 2, ry, PANEL_W - 4, ROW_H - 2);
            if (hover && !"nosettings".equals(type)) {
                Glass.fillRound(g, x + 4, ry + 1, PANEL_W - 8, ROW_H - 2, 5.0F, Glass.HOVER, 0x1AFFFFFF);
            }
            switch (type) {
                case "toggle" -> {
                    String label = module.isEnabled()
                            ? "\u00a7a[ON]  " + module.getName()
                            : "\u00a77[OFF] " + module.getName();
                    g.drawString(this.font, label, x + 14, ry + 6, Glass.TEXT, true);
                }
                case "keybind" -> {
                    if (waitingForKey) {
                        g.drawString(this.font, "Press a key\u2026  (Esc = none)", x + 14, ry + 6, Glass.ACCENT, true);
                    } else {
                        String key = module.getKeyLabel();
                        boolean bound = key != null && !key.isEmpty() && !"None".equals(key);
                        g.drawString(this.font, "Keybind: " + (bound ? "[" + key + "]" : "none"),
                                x + 14, ry + 6, bound ? Glass.TEXT : Glass.OFF, true);
                        g.drawString(this.font, "Clear",
                                x + PANEL_W - 14 - this.font.width("Clear"), ry + 6, Glass.DIM, true);
                    }
                }
                case "setting" -> {
                    Setting<?> s = (Setting<?>) row[1];
                    String label = s.getLabel() + ":";
                    String value = s.displayString();
                    g.drawString(this.font, label, x + 14, ry + 6, Glass.TEXT, true);
                    g.drawString(this.font, value,
                            x + PANEL_W - 14 - this.font.width(value), ry + 6, Glass.ACCENT, true);
                    if (s.isNumeric()) {
                        int[] tr = sliderTrack(x, ry);
                        double frac = (s.asDouble() - s.getMin())
                                / Math.max(1e-6, s.getMax() - s.getMin());
                        frac = Math.max(0.0, Math.min(1.0, frac));
                        // Track.
                        Glass.fillRound(g, tr[0], tr[1], tr[2], 3, 1.5F,
                                0x55141414, 0x55141414);
                        // Filled portion.
                        int fillW = (int) Math.round(tr[2] * frac);
                        if (fillW > 0) {
                            Glass.fillRound(g, tr[0], tr[1], fillW, 3, 1.5F,
                                    Glass.ACCENT, Glass.ACCENT);
                        }
                        // Handle.
                        int hx = tr[0] + (int) Math.round(tr[2] * frac) - 4;
                        Glass.fillRound(g, hx, tr[1] - 3, 8, 9, 2.0F,
                                0xFFFFFFFF, 0xFFD1D5DB);
                    }
                }
                case "nosettings" -> g.drawString(this.font, "No settings for this module",
                        x + 14, ry + 6, Glass.OFF, true);
                case "back" -> {
                    boolean bh = Glass.in(mx, my, x + PANEL_W / 2 - 60, ry, 120, ROW_H);
                    Glass.pill(g, x + PANEL_W / 2 - 60, ry, 120, ROW_H, false, bh);
                    g.drawCenteredString(this.font, "Back", x + PANEL_W / 2, ry + 6,
                            bh ? Glass.TEXT : Glass.DIM);
                }
            }
            ry += ROW_H + GAP;
        }

        // Scroll hint when rows are clipped.
        if (rows.size() > maxVisibleRows()) {
            String hint = (scroll > 0 ? "\u2191 " : "") + (scroll < rows.size() - maxVisibleRows() ? "\u2193" : "");
            g.drawCenteredString(this.font, hint, this.width / 2, y + panelH() - 10, Glass.DIM);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = panelX(), y = panelY() + 8 - scroll * (ROW_H + GAP);
        for (Object[] row : rows) {
            String type = (String) row[0];
            if (!"nosettings".equals(type) && Glass.in((float) mx, (float) my, x + 2, y, PANEL_W - 4, ROW_H - 2)) {
                switch (type) {
                    case "toggle" -> {
                        if (button == 0) {
                            module.toggle();
                            QynlClient.getInstance().getModuleManager().saveToConfig();
                        }
                        return true;
                    }
                    case "keybind" -> {
                        if (button == 0) {
                            // Right side = Clear, left side = set.
                            float clearX = x + PANEL_W - 14 - this.font.width("Clear");
                            if (mx >= clearX - 6 && mx <= x + PANEL_W - 6) {
                                module.setKeyCode(-1);
                                QynlClient.getInstance().getModuleManager().saveToConfig();
                            } else {
                                waitingForKey = true;
                            }
                        } else if (button == 1) {
                            module.setKeyCode(-1);
                            QynlClient.getInstance().getModuleManager().saveToConfig();
                        }
                        return true;
                    }
                    case "setting" -> {
                        Setting<?> s = (Setting<?>) row[1];
                        if (button == 1) {
                            s.cycleDown();
                            QynlClient.getInstance().getModuleManager().saveToConfig();
                            return true;
                        }
                        if (s.isNumeric()) {
                            int[] tr = sliderTrack(x, y);
                            if (mx >= tr[0] - 4 && mx <= tr[0] + tr[2] + 4) {
                                applySlider(s, (float) mx, tr);
                                draggingSetting = s;
                                QynlClient.getInstance().getModuleManager().saveToConfig();
                                return true;
                            }
                            s.cycle();
                            QynlClient.getInstance().getModuleManager().saveToConfig();
                            return true;
                        }
                        s.cycle();
                        QynlClient.getInstance().getModuleManager().saveToConfig();
                        return true;
                    }
                    case "back" -> {
                        if (button == 0) {
                            goBack();
                        }
                        return true;
                    }
                }
            }
            y += ROW_H + GAP;
        }
        if (button == 1) {
            goBack();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        // Wheel over a slider row fine-tunes the value instead of scrolling.
        Setting<?> hovered = hoveredSetting((float) mx, (float) my);
        if (hovered != null && hovered.isNumeric()) {
            if (dy > 0) {
                hovered.cycle();
            } else {
                hovered.cycleDown();
            }
            QynlClient.getInstance().getModuleManager().saveToConfig();
            return true;
        }
        if (rows.size() > maxVisibleRows()) {
            scroll = Math.max(0, Math.min(rows.size() - maxVisibleRows(), scroll + (dy > 0 ? -1 : 1)));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dragX, double dragY) {
        if (draggingSetting != null && button == 0) {
            int x = panelX();
            int y = panelY() + 8 - scroll * (ROW_H + GAP);
            for (Object[] row : rows) {
                if ("setting".equals(row[0]) && row[1] == draggingSetting) {
                    applySlider(draggingSetting, (float) mx, sliderTrack(x, y));
                    QynlClient.getInstance().getModuleManager().saveToConfig();
                    return true;
                }
                y += ROW_H + GAP;
            }
        }
        return super.mouseDragged(mx, my, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0) {
            draggingSetting = null;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Slider track geometry for a numeric-setting row. */
    private int[] sliderTrack(int x, int ry) {
        return new int[]{x + 100, ry + 13, PANEL_W - 100 - 54};
    }

    private void applySlider(Setting<?> s, double mx, int[] tr) {
        double frac = (mx - tr[0]) / tr[2];
        frac = Math.max(0.0, Math.min(1.0, frac));
        s.setValue(s.getMin() + frac * (s.getMax() - s.getMin()));
    }

    /** The setting row under the cursor, or null. */
    private Setting<?> hoveredSetting(float mx, float my) {
        int x = panelX();
        int y = panelY() + 8 - scroll * (ROW_H + GAP);
        for (Object[] row : rows) {
            if ("setting".equals(row[0])
                    && Glass.in(mx, my, x + 2, y, PANEL_W - 4, ROW_H - 2)) {
                return (Setting<?>) row[1];
            }
            y += ROW_H + GAP;
        }
        return null;
    }

    private void goBack() {
        waitingForKey = false;
        draggingSetting = null;
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ClickGuiScreen());
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (waitingForKey) {
            if (key == GLFW.GLFW_KEY_ESCAPE
                    || key == GLFW.GLFW_KEY_BACKSPACE
                    || key == GLFW.GLFW_KEY_DELETE) {
                module.setKeyCode(-1);
            } else if (key > 0 && !isGameCriticalKey(key)) {
                module.setKeyCode(key);
            }
            waitingForKey = false;
            QynlClient.getInstance().getModuleManager().saveToConfig();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private boolean isGameCriticalKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER
                || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_SLASH
                || key == GLFW.GLFW_KEY_TAB
                || key == GLFW.GLFW_KEY_F1
                || key == GLFW.GLFW_KEY_F2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

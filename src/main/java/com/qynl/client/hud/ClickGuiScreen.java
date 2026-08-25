package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vape glassmorphism ClickGUI — translucent rounded panels, category pills,
 * a scrollable module list with green/grey state colors, glass action
 * buttons. Left-click a module to toggle, right-click for its settings,
 * scroll to browse, Esc or Right-Shift to close.
 */
public class ClickGuiScreen extends Screen {
    private static final int ROW_H = 20;
    private static final int PANEL_W = 260;
    private static final int TAB_W = 56;
    private static final int TAB_H = 18;
    private static final int MIN_ROWS_VISIBLE = 4;

    private Category selectedCategory = Category.COMBAT;
    private int scroll = 0;

    public ClickGuiScreen() {
        super(Component.literal("Qynl"));
    }

    // ── layout geometry ─────────────────────────────────────────

    private int tabsX() {
        return this.width / 2 - (Category.values().length * TAB_W + (Category.values().length - 1) * 6) / 2;
    }

    private int panelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return 66;
    }

    private List<Module> categoryModules() {
        return QynlClient.getInstance().getModuleManager().getModules().stream()
                .filter(m -> m.getCategory() == selectedCategory)
                .toList();
    }

    /** Rows that fit above the footer at the current window height. */
    private int maxVisibleRows() {
        int available = this.height - panelY() - 72;
        return Math.max(MIN_ROWS_VISIBLE, available / ROW_H);
    }

    private int visibleRows(List<Module> list) {
        return Math.min(list.size(), maxVisibleRows());
    }

    /** Panel height: rows + padding + an integrated footer strip. */
    private int panelHeight(List<Module> list) {
        return visibleRows(list) * ROW_H + 14 + 26;
    }

    /** Footer strip top inside the panel. */
    private int footerY(List<Module> list) {
        return panelY() + panelHeight(list) - 26;
    }

    // ── render ──────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // Dimmed backdrop so the world is still faintly visible (glass feel).
        g.fill(0, 0, this.width, this.height, 0x66000000);

        // Two-tone title.
        int titleX = this.width / 2 - (this.font.width("Qynl") + this.font.width("  v" + QynlClient.VERSION)) / 2;
        g.drawString(this.font, "Qynl", titleX, 10, Glass.TEXT, true);
        g.drawString(this.font, "  v" + QynlClient.VERSION, titleX + this.font.width("Qynl"), 10, Glass.ACCENT, true);
        g.drawCenteredString(this.font, "\u00a77L-click toggle \u00b7 R-click settings \u00b7 Esc close",
                this.width / 2, 26, Glass.DIM);

        renderTabs(g, mx, my);
        renderModuleList(g, mx, my);
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        Category[] cats = Category.values();
        int x = tabsX();
        for (Category cat : cats) {
            boolean sel = cat == selectedCategory;
            boolean hover = Glass.in(mx, my, x, 42, TAB_W, TAB_H);
            Glass.pill(g, x, 42, TAB_W, TAB_H, sel, hover);
            g.drawCenteredString(this.font, cat.getLabel(), x + TAB_W / 2, 46,
                    sel ? Glass.ON : (hover ? Glass.TEXT : Glass.DIM));
            x += TAB_W + 6;
        }
    }

    private void renderModuleList(GuiGraphics g, int mx, int my) {
        List<Module> list = categoryModules();
        int ph = panelHeight(list);
        int x = panelX(), y = panelY();
        int fy = footerY(list);

        Glass.panel(g, x, y, PANEL_W, ph, 8.0F);

        int ry = y + 8 - scroll * ROW_H;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            if (ry + ROW_H < y || ry > fy - 4) {
                ry += ROW_H;
                continue;
            }
            boolean hover = Glass.in(mx, my, x + 2, ry, PANEL_W - 4, ROW_H - 2);
            if (hover) {
                Glass.fillRound(g, x + 4, ry + 1, PANEL_W - 8, ROW_H - 2, 5.0F, Glass.HOVER, 0x1AFFFFFF);
            }
            int color = m.isEnabled() ? Glass.ON : Glass.DIM;
            g.drawString(this.font, m.getName(), x + 12, ry + 6, color, true);

            // Mode suffix right after the name (dim).
            String mode = modeSuffix(m);
            if (!mode.isEmpty()) {
                g.drawString(this.font, mode, x + 12 + this.font.width(m.getName()), ry + 6, Glass.DIM, false);
            }

            // Keybind + state dot on the right.
            String key = m.getKeyLabel();
            boolean bound = key != null && !key.isEmpty() && !"None".equals(key);
            String right = (bound ? "[" + key + "] " : "") + (m.isEnabled() ? "ON" : "OFF");
            g.drawString(this.font, right,
                    x + PANEL_W - 12 - this.font.width(right), ry + 6,
                    m.isEnabled() ? Glass.ON : Glass.OFF, true);

            ry += ROW_H;
        }

        if (list.isEmpty()) {
            g.drawCenteredString(this.font, "(none)", this.width / 2, y + ph / 2 - 4, Glass.DIM);
        }

        // Scroll hint when the list is clipped.
        if (list.size() > maxVisibleRows()) {
            String hint = (scroll > 0 ? "\u2191 " : "") + (scroll < list.size() - maxVisibleRows() ? "\u2193" : "");
            g.drawCenteredString(this.font, hint, this.width / 2, fy - 12, Glass.DIM);
        }

        // Divider above the integrated footer.
        Glass.fillRound(g, x + 10, fy - 4, PANEL_W - 20, 1.0F, 0.5F, Glass.BORDER_DIM, Glass.BORDER_DIM);

        // Footer buttons live inside the panel — nothing floats disconnected.
        int totalW = 3 * 78 + 2 * 6;
        int bx = x + (PANEL_W - totalW) / 2;
        drawAction(g, bx, fy + 4, 78, "Keybinds\u2026", Glass.in(mx, my, bx, fy + 4, 78, 18));
        drawAction(g, bx + 84, fy + 4, 78, "Settings\u2026", Glass.in(mx, my, bx + 84, fy + 4, 78, 18));
        drawAction(g, bx + 168, fy + 4, 78, "Close", Glass.in(mx, my, bx + 168, fy + 4, 78, 18));
    }

    private void drawAction(GuiGraphics g, float x, float y, float w, String label, boolean hover) {
        Glass.pill(g, x, y, w, 18, false, hover);
        g.drawCenteredString(this.font, label, (int) (x + w / 2), (int) (y + 5),
                hover ? Glass.TEXT : Glass.DIM);
    }

    private static String modeSuffix(Module m) {
        com.qynl.client.module.Setting<?> mode = m.getSetting("mode");
        if (mode == null) return "";
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) return "";
        return " \u00b7 " + v;
    }

    // ── input ───────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Category tabs.
        Category[] cats = Category.values();
        int x = tabsX();
        for (Category cat : cats) {
            if (Glass.in((float) mx, (float) my, x, 42, TAB_W, TAB_H)) {
                if (cat != selectedCategory) {
                    selectedCategory = cat;
                    scroll = 0;
                }
                return true;
            }
            x += TAB_W + 6;
        }

        List<Module> list = categoryModules();
        int fy = footerY(list);
        int y = panelY() + 8 - scroll * ROW_H;
        for (int i = 0; i < list.size(); i++) {
            // Only rows inside the panel (above the footer divider) are clickable.
            if (y + ROW_H < panelY()) {
                y += ROW_H;
                continue;
            }
            if (y >= fy - 4) break;
            if (Glass.in((float) mx, (float) my, panelX() + 2, y, PANEL_W - 4, ROW_H - 2)) {
                Module m = list.get(i);
                if (btn == 0) {
                    m.toggle();
                    QynlClient.getInstance().getModuleManager().saveToConfig();
                } else if (btn == 1 && this.minecraft != null) {
                    this.minecraft.setScreen(new ModuleDetailScreen(m));
                }
                return true;
            }
            y += ROW_H;
        }

        // Footer actions (inside the panel).
        int bx = panelX() + (PANEL_W - (3 * 78 + 2 * 6)) / 2;
        if (Glass.in((float) mx, (float) my, bx, fy + 4, 78, 18) && this.minecraft != null) {
            this.minecraft.setScreen(new KeybindScreen());
            return true;
        }
        if (Glass.in((float) mx, (float) my, bx + 84, fy + 4, 78, 18) && this.minecraft != null) {
            this.minecraft.setScreen(new ModuleSettingsScreen());
            return true;
        }
        if (Glass.in((float) mx, (float) my, bx + 168, fy + 4, 78, 18)) {
            onClose();
            return true;
        }

        if (btn == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        List<Module> list = categoryModules();
        if (list.size() > maxVisibleRows()) {
            scroll = Math.max(0, Math.min(list.size() - maxVisibleRows(), scroll + (dy > 0 ? -1 : 1)));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

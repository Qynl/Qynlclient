package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Glassmorphism settings editor — every module with options listed in a
 * scrollable glass panel; click a value row to cycle it.
 */
public class ModuleSettingsScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int PANEL_W = 340;
    private static final int MIN_ROWS_VISIBLE = 8;

    private static final class Entry {
        final Module module;
        final Setting<?> setting;
        final boolean header;

        Entry(Module module, Setting<?> setting, boolean header) {
            this.module = module;
            this.setting = setting;
            this.header = header;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int scroll = 0;

    public ModuleSettingsScreen() {
        super(Component.literal("QynlClient \u2014 Module Settings"));
    }

    @Override
    protected void init() {
        entries.clear();
        ModuleManager modules = QynlClient.getInstance().getModuleManager();
        for (Module module : modules.getModules()) {
            if (!module.hasSettings()) {
                continue;
            }
            entries.add(new Entry(module, null, true));
            for (Setting<?> setting : module.getSettings()) {
                entries.add(new Entry(module, setting, false));
            }
        }
    }

    private int panelX() {
        return this.width / 2 - PANEL_W / 2;
    }

    private int panelY() {
        return 44;
    }

    /** Rows that fit above the footer at the current window height. */
    private int maxVisibleRows() {
        int available = this.height - panelY() - 40;
        return Math.max(MIN_ROWS_VISIBLE, available / ROW_H);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0x66000000);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, Glass.TEXT);
        g.drawCenteredString(this.font, "\u00a77Click a value to change it \u00b7 saved automatically",
                this.width / 2, 26, Glass.DIM);

        int visible = Math.min(entries.size(), maxVisibleRows());
        int ph = visible * ROW_H + 14;
        int x = panelX(), y = panelY();

        Glass.panel(g, x, y, PANEL_W, ph, 8.0F);

        int ry = y + 8 - scroll * ROW_H;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (ry + ROW_H < y || ry > y + ph) {
                ry += ROW_H;
                continue;
            }
            if (entry.header) {
                g.drawString(this.font, "\u00a7a" + entry.module.getName(), x + 12, ry + 1, Glass.ON, true);
            } else {
                boolean hover = Glass.in(mx, my, x + 2, ry, PANEL_W - 4, ROW_H - 2);
                if (hover) {
                    Glass.fillRound(g, x + 4, ry + 1, PANEL_W - 8, ROW_H - 2, 5.0F, Glass.HOVER, 0x1AFFFFFF);
                }
                String label = entry.setting.getLabel() + ":";
                String value = entry.setting.displayString();
                g.drawString(this.font, label, x + 14, ry + 4, Glass.TEXT, true);
                g.drawString(this.font, value,
                        x + PANEL_W - 14 - this.font.width(value), ry + 4, Glass.ACCENT, true);
            }
            ry += ROW_H;
        }

        // Back pill.
        boolean backHover = Glass.in(mx, my, this.width / 2 - 60, y + ph + 12, 120, 18);
        Glass.pill(g, this.width / 2 - 60, y + ph + 12, 120, 18, false, backHover);
        g.drawCenteredString(this.font, "Back", this.width / 2, y + ph + 17,
                backHover ? Glass.TEXT : Glass.DIM);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = panelX(), y = panelY() + 8 - scroll * ROW_H;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (entry.header) {
                y += ROW_H;
                continue;
            }
            if (Glass.in((float) mx, (float) my, x + 2, y, PANEL_W - 4, ROW_H - 2) && button == 0) {
                entry.setting.cycle();
                QynlClient.getInstance().getModuleManager().saveToConfig();
                return true;
            }
            y += ROW_H;
        }

        int ph = Math.min(entries.size(), maxVisibleRows()) * ROW_H + 14;
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

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (entries.size() > maxVisibleRows()) {
            scroll = Math.max(0, Math.min(entries.size() - maxVisibleRows(), scroll + (dy > 0 ? -1 : 1)));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

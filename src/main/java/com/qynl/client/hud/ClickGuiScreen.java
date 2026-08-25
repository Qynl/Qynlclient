package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vape-Lite style ClickGUI — dark, clean, minimal.
 *
 * <p>Category tabs across the top. Module list in the center.
 * Left-click = toggle. Right-click = settings panel.</p>
 */
public class ClickGuiScreen extends Screen {
    private static final int ROW_H = 18;
    private static final int COL_W = 240;
    private static final int TABS_Y = 36;

    private Category selectedCategory = Category.COMBAT;
    private final List<Module> rows = new ArrayList<>();
    private final List<Integer> rowYs = new ArrayList<>();

    // ── palette ──
    private static final int BG       = 0xC8101010;
    private static final int ACCENT   = 0xFF55FF55;
    private static final int WHITE    = 0xFFD0D0D0;
    private static final int GRAY     = 0xFF7A7A7A;
    private static final int TAB_BG   = 0xC01A1A1A;
    private static final int TAB_SEL  = 0xC0222B22;
    private static final int ON_COLOR = ACCENT;
    private static final int OFF_COLOR = 0xFF555555;

    public ClickGuiScreen() { super(Component.literal("Qynl")); }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        rows.clear(); rowYs.clear(); this.clearWidgets();

        int cx = this.width / 2, y = TABS_Y + 16;
        int lx = cx - COL_W / 2;

        // ── category tabs ──
        int tabX = cx - (Category.values().length * 58) / 2;
        for (Category cat : Category.values()) {
            boolean sel = cat == selectedCategory;
            int c = sel ? ACCENT : GRAY;
            Button b = Button.builder(Component.literal(cat.getLabel()), btn -> {
                selectedCategory = cat; rebuild();
            }).bounds(tabX, TABS_Y, 54, 14).build();
            this.addRenderableWidget(b);
            tabX += 58;
        }

        // ── modules in selected category ──
        ModuleManager mm = QynlClient.getInstance().getModuleManager();
        List<Module> cats = mm.getModules().stream()
                .filter(m -> m.getCategory() == selectedCategory).toList();
        if (cats.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("(none)"), b -> {})
                    .bounds(cx - 40, y, 80, ROW_H).build());
        }
        for (Module m : cats) {
            rows.add(m);
            rowYs.add(y);
            String label = m.isEnabled() ? "[ON]  " + m.getName() : "[OFF] " + m.getName();
            String key = m.getKeyLabel();
            if (key != null && !key.isEmpty() && !"None".equals(key))
                label = label + "  [" + key + "]";

            int color = m.isEnabled() ? ACCENT : GRAY;
            final String fl = label;
            Button b = Button.builder(Component.literal(fl), btn -> { /* handled in mouseClicked */ })
                    .bounds(lx, y, COL_W, ROW_H - 2).build();
            this.addRenderableWidget(b);
            y += ROW_H;
        }

        // ── bottom buttons ──
        y += 4;
        this.addRenderableWidget(Button.builder(Component.literal("Keybinds\u2026"),
                b -> { if (this.minecraft != null) this.minecraft.setScreen(new KeybindScreen()); })
                .bounds(cx - 122, y, 76, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Settings\u2026"),
                b -> { if (this.minecraft != null) this.minecraft.setScreen(new ModuleSettingsScreen()); })
                .bounds(cx - 38, y, 76, 18).build());
        this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 46, y, 76, 18).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        // full dark background
        g.fill(0, 0, this.width, this.height, BG);
        // title
        String title = "Qynl  v" + QynlClient.VERSION;
        g.drawCenteredString(this.font, title, this.width / 2, 8, ACCENT);
        g.drawCenteredString(this.font,
                "\u00a77L-click toggle  \u00b7  R-click settings  \u00b7  R-click outside close",
                this.width / 2, 22, 0xFF555555);

        // draw category tab backgrounds
        int tabX = this.width / 2 - (Category.values().length * 58) / 2;
        for (Category cat : Category.values()) {
            g.fill(tabX, TABS_Y, tabX + 54, TABS_Y + 14, cat == selectedCategory ? TAB_SEL : TAB_BG);
            if (cat == selectedCategory) g.fill(tabX, TABS_Y + 13, tabX + 54, TABS_Y + 14, ACCENT);
            g.drawCenteredString(this.font, cat.getLabel(), tabX + 27, TABS_Y + 3,
                    cat == selectedCategory ? ACCENT : GRAY);
            tabX += 58;
        }

        super.render(g, mx, my, pt);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        int cx = this.width / 2, halfW = COL_W / 2;

        if (btn == 1) {
            // right-click module row → detail
            for (int i = 0; i < rows.size(); i++) {
                if (mx >= cx - halfW && mx <= cx + halfW && my >= rowYs.get(i) && my <= rowYs.get(i) + ROW_H - 2) {
                    if (this.minecraft != null) this.minecraft.setScreen(new ModuleDetailScreen(rows.get(i)));
                    return true;
                }
            }
            onClose(); return true;
        }
        if (btn == 0) {
            for (int i = 0; i < rows.size(); i++) {
                if (mx >= cx - halfW && mx <= cx + halfW && my >= rowYs.get(i) && my <= rowYs.get(i) + ROW_H - 2) {
                    rows.get(i).toggle();
                    QynlClient.getInstance().getModuleManager().saveToConfig();
                    rebuild(); return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public void onClose() {
        super.onClose();
        Module cg = QynlClient.getInstance().getModuleManager().find("ClickGUI");
        if (cg != null && cg.isEnabled()) cg.setEnabled(false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
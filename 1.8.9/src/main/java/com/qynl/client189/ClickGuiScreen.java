package com.qynl.client189;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * Qyn-L ClickGUI — a clean, minimal, Vape-Lite-style module manager.
 *
 * <p>Three panels, no vanilla button chrome:</p>
 * <ul>
 *   <li><b>Categories</b> (left) — Combat / Render / Utility / Other.</li>
 *   <li><b>Modules</b> (center) — the modules of the selected category.
 *       Left-click toggles, right-click opens its settings.</li>
 *   <li><b>Settings</b> (right) — toggle, keybind and every setting of the
 *       selected module. Click a setting to cycle it; click a bind to
 *       capture a new key; text settings are edited inline.</li>
 * </ul>
 *
 * <p>Keyboard: RShift / Esc closes, arrows navigate, Enter toggles.</p>
 */
public class ClickGuiScreen extends Screen {
    // Layout
    private static final int CAT_X = 12;
    private static final int CAT_W = 118;
    private static final int MOD_X = 140;
    private static final int MOD_W = 230;
    private static final int SET_X = 380;
    private static final int SET_W = 300;
    private static final int PANEL_Y = 18;
    private static final int ROW_H = 21;
    private static final int HEADER_H = 14;

    // Palette
    private static final int BG = 0xB3050A0E;
    private static final int PANEL = 0xF20B1016;
    private static final int PANEL_HOVER = 0x331F2937;
    private static final int PANEL_SELECTED = 0xFF1F2937;
    private static final int BORDER = 0xFF1F2937;
    private static final int TEXT = 0xFFE5E7EB;
    private static final int TEXT_DIM = 0xFF6B7280;
    private static final int GREEN = 0xFF4ADE80;
    private static final int RED = 0xFFFF6B6B;

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;
    private boolean waitingForKey = false;
    private boolean editingText = false;
    private Setting<?> editingSetting = null;
    private String editBuffer = "";
    private int moduleScroll = 0;

    public ClickGuiScreen() {
        super();
    }

    @Override
    public void init() {
        if (selectedModule != null && !selectedModule.getCategory().equals(selectedCategory)) {
            selectedModule = null;
        }
    }

    // ── rendering ───────────────────────────────────────────────

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        renderBackground();
        fill(0, 0, this.width, this.height, BG);

        drawCategories(mouseX, mouseY);
        drawModules(mouseX, mouseY);
        if (selectedModule != null) {
            drawSettings(mouseX, mouseY);
        }

        // Footer
        String footer = "Qyn-L \u00b7 1.8.9 \u00b7 RShift to close";
        this.textRenderer.drawWithShadow(footer, this.width - this.textRenderer.getStringWidth(footer) - 6,
                this.height - 12, TEXT_DIM);
    }

    private void drawCategories(int mouseX, int mouseY) {
        panel(CAT_X, PANEL_Y, CAT_W, Category.values().length * (ROW_H + 2) + HEADER_H + 4);
        this.textRenderer.drawWithShadow("CATEGORIES", CAT_X + 8, PANEL_Y + 3, TEXT_DIM);

        int y = PANEL_Y + HEADER_H + 4;
        for (Category category : Category.values()) {
            boolean hover = mouseX >= CAT_X && mouseX < CAT_X + CAT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean selected = category == selectedCategory;
            if (hover || selected) {
                fill(CAT_X + 1, y, CAT_X + CAT_W - 1, y + ROW_H, selected ? PANEL_SELECTED : PANEL_HOVER);
            }
            if (selected) {
                fill(CAT_X + 1, y + 3, CAT_X + 3, y + ROW_H - 3, GREEN);
            }
            int color = selected ? GREEN : (hover ? TEXT : TEXT_DIM);
            this.textRenderer.drawWithShadow(category.getLabel(), CAT_X + 10, y + 6, color);
            y += ROW_H + 2;
        }
    }

    private List<Module> currentCategoryModules() {
        List<Module> list = new ArrayList<>();
        for (Module m : QynlClient189.getInstance().getModuleManager().getModules()) {
            if (m.getCategory() == selectedCategory) list.add(m);
        }
        return list;
    }

    private void drawModules(int mouseX, int mouseY) {
        List<Module> modules = currentCategoryModules();
        int panelH = HEADER_H + 6 + Math.min(modules.size(), 12) * (ROW_H + 2);
        panel(MOD_X, PANEL_Y, MOD_W, panelH);
        this.textRenderer.drawWithShadow(selectedCategory.getLabel().toUpperCase(), MOD_X + 8, PANEL_Y + 3, TEXT_DIM);

        int y = PANEL_Y + HEADER_H + 4;
        for (int i = moduleScroll; i < modules.size() && i < moduleScroll + 12; i++) {
            Module m = modules.get(i);
            boolean hover = mouseX >= MOD_X && mouseX < MOD_X + MOD_W && mouseY >= y && mouseY < y + ROW_H;
            boolean selected = m == selectedModule;
            if (hover || selected) {
                fill(MOD_X + 1, y, MOD_X + MOD_W - 1, y + ROW_H, selected ? PANEL_SELECTED : PANEL_HOVER);
            }
            // Key label (dim) left of the ON/OFF state.
            String key = keyLabel(m);
            String state = m.isEnabled() ? "ON" : "OFF";
            int stateColor = m.isEnabled() ? GREEN : TEXT_DIM;
            int stateX = MOD_X + MOD_W - 8 - this.textRenderer.getStringWidth(state);
            if (!"NONE".equals(key)) {
                this.textRenderer.drawWithShadow(key, stateX - 6 - this.textRenderer.getStringWidth(key),
                        y + 6, TEXT_DIM);
            }
            this.textRenderer.drawWithShadow(m.getName(), MOD_X + 10, y + 6, selected ? GREEN : TEXT);
            this.textRenderer.drawWithShadow(state, stateX, y + 6, stateColor);
            y += ROW_H + 2;
        }
        if (modules.size() > 12) {
            this.textRenderer.drawWithShadow("scroll \u2191\u2193", MOD_X + MOD_W - 46, PANEL_Y + 3, TEXT_DIM);
        }
    }

    /** Y offset where the settings rows start (below title + description). */
    private int settingsTop() {
        Module m = selectedModule;
        String desc = m == null ? null : m.getDescription();
        boolean hasDesc = desc != null && !desc.isEmpty();
        return PANEL_Y + HEADER_H + 4 + (hasDesc ? 12 : 0);
    }

    private void drawSettings(int mouseX, int mouseY) {
        Module m = selectedModule;
        List<Setting<?>> settings = new ArrayList<>(m.getSettings());
        boolean hasDesc = m.getDescription() != null && !m.getDescription().isEmpty();
        int panelH = HEADER_H + 6 + (hasDesc ? 12 : 0) + (settings.size() + 2) * (ROW_H + 2);
        panel(SET_X, PANEL_Y, SET_W, panelH);

        int titleColor = m.isEnabled() ? GREEN : TEXT;
        this.textRenderer.drawWithShadow(m.getName(), SET_X + 8, PANEL_Y + 3, titleColor);
        // One-line description under the title, truncated to the panel width.
        if (hasDesc) {
            String desc = m.getDescription();
            int maxW = SET_W - 16;
            if (this.textRenderer.getStringWidth(desc) > maxW) {
                while (!desc.isEmpty() && this.textRenderer.getStringWidth(desc + "\u2026") > maxW) {
                    desc = desc.substring(0, desc.length() - 1);
                }
                desc += "\u2026";
            }
            this.textRenderer.drawWithShadow(desc, SET_X + 8, PANEL_Y + 3 + 10, TEXT_DIM);
        }

        int y = settingsTop();

        // Toggle row
        row(SET_X, y, SET_W, ROW_H, mouseX, mouseY);
        this.textRenderer.drawWithShadow("Enabled", SET_X + 10, y + 6, TEXT);
        String state = m.isEnabled() ? "ON" : "OFF";
        this.textRenderer.drawWithShadow(state, SET_X + SET_W - 10 - this.textRenderer.getStringWidth(state), y + 6,
                m.isEnabled() ? GREEN : TEXT_DIM);
        y += ROW_H + 2;

        // Keybind row
        String bindText = waitingForKey ? "Press key... (Esc = none)"
                : "Bind: " + keyLabel(m);
        row(SET_X, y, SET_W, ROW_H, mouseX, mouseY);
        this.textRenderer.drawWithShadow(bindText, SET_X + 10, y + 6, waitingForKey ? GREEN : TEXT_DIM);
        y += ROW_H + 2;

        // Settings
        for (Setting<?> s : settings) {
            row(SET_X, y, SET_W, ROW_H, mouseX, mouseY);
            this.textRenderer.drawWithShadow(s.getLabel(), SET_X + 10, y + 6, TEXT);
            String valueText = editingText && s == editingSetting ? editBuffer + "\u2584" : s.displayString();
            int valueColor = s.isText() ? TEXT_DIM : GREEN;
            this.textRenderer.drawWithShadow(valueText,
                    SET_X + SET_W - 10 - this.textRenderer.getStringWidth(valueText), y + 6, valueColor);
            y += ROW_H + 2;
        }
    }

    private String keyLabel(Module m) {
        String label = m.getKeyLabel();
        return (label == null || label.isEmpty() || "None".equals(label)) ? "NONE" : label;
    }

    // ── interaction ─────────────────────────────────────────────

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        editingText = false;
        editingSetting = null;
        waitingForKey = false;

        // Categories
        int y = PANEL_Y + HEADER_H + 4;
        for (Category category : Category.values()) {
            if (in(mouseX, mouseY, CAT_X, y, CAT_W, ROW_H)) {
                if (category != selectedCategory) {
                    selectedCategory = category;
                    selectedModule = null;
                    moduleScroll = 0;
                }
                return;
            }
            y += ROW_H + 2;
        }

        // Modules
        List<Module> modules = currentCategoryModules();
        y = PANEL_Y + HEADER_H + 4;
        for (int i = moduleScroll; i < modules.size() && i < moduleScroll + 12; i++) {
            if (in(mouseX, mouseY, MOD_X, y, MOD_W, ROW_H)) {
                Module m = modules.get(i);
                if (button == 1) {
                    selectedModule = m;
                } else {
                    m.toggle();
                    QynlClient189.getInstance().getModuleManager().saveToConfig();
                }
                return;
            }
            y += ROW_H + 2;
        }

        // Settings panel
        if (selectedModule != null) {
            handleSettingsClick(mouseX, mouseY, button);
        }
    }

    private void handleSettingsClick(int mouseX, int mouseY, int button) {
        Module m = selectedModule;
        List<Setting<?>> settings = new ArrayList<>(m.getSettings());
        int y = settingsTop();

        // Toggle
        if (in(mouseX, mouseY, SET_X, y, SET_W, ROW_H)) {
            m.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            return;
        }
        y += ROW_H + 2;

        // Keybind
        if (in(mouseX, mouseY, SET_X, y, SET_W, ROW_H)) {
            waitingForKey = true;
            return;
        }
        y += ROW_H + 2;

        // Settings
        for (Setting<?> s : settings) {
            if (in(mouseX, mouseY, SET_X, y, SET_W, ROW_H)) {
                if (s.isText()) {
                    editingText = true;
                    editingSetting = s;
                    editBuffer = s.valueAsString();
                } else {
                    s.cycle();
                }
                QynlClient189.getInstance().getModuleManager().saveToConfig();
                return;
            }
            y += ROW_H + 2;
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_RSHIFT || keyCode == Keyboard.KEY_ESCAPE) {
            MinecraftClient.getInstance().openScreen(null);
            return;
        }

        if (waitingForKey) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE) {
                selectedModule.setKeyCode(-1);
            } else if (keyCode > 0 && selectedModule != null) {
                selectedModule.setKeyCode(keyCode);
            }
            waitingForKey = false;
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            return;
        }

        if (editingText) {
            if (keyCode == Keyboard.KEY_RETURN) {
                applyTextEdit();
                editingText = false;
                editingSetting = null;
            } else if (keyCode == Keyboard.KEY_BACK && !editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            } else if (character >= 32) {
                editBuffer += character;
            }
            return;
        }

        // Navigation
        if (keyCode == Keyboard.KEY_DOWN) {
            List<Module> modules = currentCategoryModules();
            if (!modules.isEmpty()) {
                selectedModule = modules.get(Math.min(modules.size() - 1,
                        modules.indexOf(selectedModule) + 1));
                if (selectedModule != null && modules.indexOf(selectedModule) >= moduleScroll + 12) {
                    moduleScroll++;
                }
            }
            return;
        }
        if (keyCode == Keyboard.KEY_UP) {
            List<Module> modules = currentCategoryModules();
            if (selectedModule != null) {
                int idx = modules.indexOf(selectedModule);
                if (idx > 0) {
                    selectedModule = modules.get(idx - 1);
                    if (idx - 1 < moduleScroll) moduleScroll--;
                }
            } else if (!modules.isEmpty()) {
                selectedModule = modules.get(0);
            }
            return;
        }
        if (keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_LEFT) {
            Category[] cats = Category.values();
            int idx = selectedCategory.ordinal() + (keyCode == Keyboard.KEY_RIGHT ? 1 : -1);
            if (idx >= 0 && idx < cats.length) {
                selectedCategory = cats[idx];
                selectedModule = null;
                moduleScroll = 0;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && selectedModule != null) {
            selectedModule.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
        }
    }

    private void applyTextEdit() {
        if (selectedModule == null || editingSetting == null) return;
        editingSetting.setFromString(editBuffer.trim());
        QynlClient189.getInstance().getModuleManager().saveToConfig();
    }

    // ── helpers ─────────────────────────────────────────────────

    private void panel(int x, int y, int w, int h) {
        fill(x, y, x + w, y + h, PANEL);
        fill(x, y, x + w, y + 1, BORDER);
        fill(x, y + h - 1, x + w, y + h, BORDER);
        fill(x, y, x + 1, y + h, BORDER);
        fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    private void row(int x, int y, int w, int h, int mouseX, int mouseY) {
        if (in(mouseX, mouseY, x, y, w, h)) {
            fill(x + 1, y, x + w - 1, y + h, PANEL_HOVER);
        }
    }

    private boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}

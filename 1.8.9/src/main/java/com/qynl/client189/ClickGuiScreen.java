package com.qynl.client189;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Qyn-L ClickGUI — a clean, minimal, Vape-Lite-style module manager.
 *
 * <p>Three panels, no vanilla button chrome:</p>
 * <ul>
 *   <li><b>Categories</b> (left) — Combat / Render / Utility / Other.</li>
 *   <li><b>Modules</b> (center) — the modules of the selected category.
 *       Left-click toggles, right-click opens its settings. The search box
 *       on top filters across <i>all</i> categories. Mouse wheel scrolls
 *       smoothly (eased), and the panels slide in with a subtle entrance.</li>
 *   <li><b>Settings</b> (right) — toggle, keybind and every setting of the
 *       selected module. Click a setting to cycle it; click a bind to
 *       capture a new key; text settings are edited inline.</li>
 * </ul>
 *
 * <p>Keyboard: RShift closes, Esc clears search / closes, arrows navigate,
 * Enter toggles, typing searches.</p>
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
    private static final int SEARCH_H = 12;
    private static final int SEARCH_Y = PANEL_Y + HEADER_H + 2;
    private static final int ROWS_TOP = SEARCH_Y + SEARCH_H + 3;
    private static final int VISIBLE_ROWS = 12;

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

    // Smooth scroll — target (rows) vs. eased position (float rows).
    private int scrollTarget = 0;
    private float scrollAnim = 0f;

    // Module search (cross-category).
    private String searchBuffer = "";
    private boolean searching = false;

    // Animation state.
    private long openedAt = -1;
    private float openAnim = 0f;
    private float hoverAnim = 0f;
    private boolean hoveredLastFrame = false;

    public ClickGuiScreen() {
        super();
    }

    @Override
    public void init() {
        if (selectedModule != null && !selectedModule.getCategory().equals(selectedCategory)) {
            selectedModule = null;
        }
        searchBuffer = "";
        searching = false;
        scrollTarget = 0;
        scrollAnim = 0f;
        openedAt = -1;
        openAnim = 0f;
        hoverAnim = 0f;
        hoveredLastFrame = false;
    }

    // ── animation helpers ─────────────────────────────────────────

    /** Entrance slide offset: 14 px → 0 over ~220 ms, ease-out cubic. */
    private int slide() {
        float e = 1f - (1f - openAnim) * (1f - openAnim) * (1f - openAnim);
        return (int) ((1f - e) * 14f);
    }

    private void updateAnim() {
        if (openedAt < 0) openedAt = System.currentTimeMillis();
        long t = System.currentTimeMillis() - openedAt;
        openAnim = Math.min(1f, t / 220f);
        hoverAnim += ((hoveredLastFrame ? 1f : 0f) - hoverAnim) * 0.30f;
        if (Math.abs(hoverAnim - (hoveredLastFrame ? 1f : 0f)) < 0.01f) {
            hoverAnim = hoveredLastFrame ? 1f : 0f;
        }
        scrollAnim += (scrollTarget - scrollAnim) * 0.28f;
        if (Math.abs(scrollAnim - scrollTarget) < 0.01f) scrollAnim = scrollTarget;
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, currentCategoryModules().size() - VISIBLE_ROWS);
        if (scrollTarget > max) scrollTarget = max;
        if (scrollTarget < 0) scrollTarget = 0;
    }

    /** Linear ARGB blend — used for the hover fade. */
    private static int blend(int a, int b, float t) {
        int aA = (a >>> 24) & 0xFF, aR = (a >>> 16) & 0xFF, aG = (a >>> 8) & 0xFF, aB = a & 0xFF;
        int bA = (b >>> 24) & 0xFF, bR = (b >>> 16) & 0xFF, bG = (b >>> 8) & 0xFF, bB = b & 0xFF;
        return (Math.round(aA + (bA - aA) * t) << 24)
                | (Math.round(aR + (bR - aR) * t) << 16)
                | (Math.round(aG + (bG - aG) * t) << 8)
                | Math.round(aB + (bB - aB) * t);
    }

    private void beginClip(int x, int y, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc.getFramebuffer();
        int scale = Math.max(1, fb.viewportWidth / this.width);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, fb.viewportHeight - (y + h) * scale, w * scale, h * scale);
    }

    private void endClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    // ── rendering ─────────────────────────────────────────────────

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        updateAnim();
        renderBackground();
        fill(0, 0, this.width, this.height, BG);

        boolean anyHover = drawCategories(mouseX, mouseY);
        anyHover |= drawModules(mouseX, mouseY);
        if (selectedModule != null) {
            anyHover |= drawSettings(mouseX, mouseY);
        }
        hoveredLastFrame = anyHover;

        // Footer
        String footer = "Qyn-L \u00b7 1.8.9 \u00b7 RShift to close";
        this.textRenderer.drawWithShadow(footer, this.width - this.textRenderer.getStringWidth(footer) - 6,
                this.height - 12, TEXT_DIM);
    }

    private boolean drawCategories(int mouseX, int mouseY) {
        int sl = slide();
        boolean any = false;
        panel(CAT_X - sl, PANEL_Y, CAT_W, Category.values().length * (ROW_H + 2) + HEADER_H + 4);
        this.textRenderer.drawWithShadow("CATEGORIES", CAT_X - sl + 8, PANEL_Y + 3, TEXT_DIM);

        int y = PANEL_Y + HEADER_H + 4;
        for (Category category : Category.values()) {
            boolean hover = mouseX >= CAT_X - sl && mouseX < CAT_X - sl + CAT_W && mouseY >= y && mouseY < y + ROW_H;
            boolean selected = category == selectedCategory;
            if (hover || selected) {
                fill(CAT_X - sl + 1, y, CAT_X - sl + CAT_W - 1, y + ROW_H,
                        selected ? PANEL_SELECTED : blend(PANEL, PANEL_HOVER, hoverAnim));
            }
            if (selected) {
                fill(CAT_X - sl + 1, y + 3, CAT_X - sl + 3, y + ROW_H - 3, GREEN);
            }
            int color = selected ? GREEN : (hover ? TEXT : TEXT_DIM);
            this.textRenderer.drawWithShadow(category.getLabel(), CAT_X - sl + 10, y + 6, color);
            any |= hover;
            y += ROW_H + 2;
        }
        return any;
    }

    /** All modules of the selected category — or, while searching, every
     *  module whose name/description matches, across all categories. */
    private List<Module> currentCategoryModules() {
        List<Module> list = new ArrayList<>();
        String q = searchBuffer.trim().toLowerCase();
        for (Module m : QynlClient189.getInstance().getModuleManager().getModules()) {
            if (q.isEmpty()) {
                if (m.getCategory() == selectedCategory) list.add(m);
            } else if (m.getName().toLowerCase().contains(q)
                    || m.getDescription().toLowerCase().contains(q)) {
                list.add(m);
            }
        }
        return list;
    }

    private boolean drawModules(int mouseX, int mouseY) {
        int sl = slide();
        boolean searchActive = !searchBuffer.trim().isEmpty();
        List<Module> modules = currentCategoryModules();
        clampScroll();
        int rows = Math.min(modules.size(), VISIBLE_ROWS);
        int panelH = HEADER_H + 6 + SEARCH_H + 3 + rows * (ROW_H + 2);
        panel(MOD_X - sl, PANEL_Y, MOD_W, panelH);
        this.textRenderer.drawWithShadow(
                searchActive ? "SEARCH" : selectedCategory.getLabel().toUpperCase(),
                MOD_X - sl + 8, PANEL_Y + 3, TEXT_DIM);
        if (searchActive) {
            this.textRenderer.drawWithShadow(String.valueOf(modules.size()),
                    MOD_X - sl + MOD_W - 10 - this.textRenderer.getStringWidth(String.valueOf(modules.size())),
                    PANEL_Y + 3, TEXT_DIM);
        } else if (modules.size() > VISIBLE_ROWS) {
            this.textRenderer.drawWithShadow("scroll \u2191\u2193", MOD_X - sl + MOD_W - 46, PANEL_Y + 3, TEXT_DIM);
        }

        // Search box
        fill(MOD_X - sl + 1, SEARCH_Y, MOD_X - sl + MOD_W - 1, SEARCH_Y + SEARCH_H, 0x331F2937);
        String box = searchBuffer + (searching ? "\u2584" : "");
        if (box.isEmpty()) box = "Search modules...";
        this.textRenderer.drawWithShadow(box, MOD_X - sl + 8, SEARCH_Y + 2,
                (searching || searchActive) ? TEXT : TEXT_DIM);

        // Module rows — eased scroll position, clipped to the panel body so
        // half-visible rows at the edges never bleed over the panels.
        float yf = ROWS_TOP - scrollAnim * (ROW_H + 2);
        boolean any = false;
        beginClip(MOD_X - sl + 1, ROWS_TOP - 1, MOD_W - 2, VISIBLE_ROWS * (ROW_H + 2));
        for (int i = 0; i < modules.size(); i++) {
            float rowY = yf + i * (ROW_H + 2);
            if (rowY + ROW_H < ROWS_TOP - 1) continue;
            if (rowY > ROWS_TOP - 1 + VISIBLE_ROWS * (ROW_H + 2)) break;
            Module m = modules.get(i);
            boolean hover = mouseX >= MOD_X - sl && mouseX < MOD_X - sl + MOD_W
                    && mouseY >= rowY && mouseY < rowY + ROW_H;
            boolean selected = m == selectedModule;
            int y = Math.round(rowY);
            if (hover || selected) {
                fill(MOD_X - sl + 1, y, MOD_X - sl + MOD_W - 1, y + ROW_H,
                        selected ? PANEL_SELECTED : blend(PANEL, PANEL_HOVER, hoverAnim));
            }
            // Key label (dim) left of the ON/OFF state.
            String key = keyLabel(m);
            String state = m.isEnabled() ? "ON" : "OFF";
            int stateColor = m.isEnabled() ? GREEN : TEXT_DIM;
            int stateX = MOD_X - sl + MOD_W - 8 - this.textRenderer.getStringWidth(state);
            if (!"NONE".equals(key)) {
                this.textRenderer.drawWithShadow(key, stateX - 6 - this.textRenderer.getStringWidth(key),
                        y + 6, TEXT_DIM);
            }
            this.textRenderer.drawWithShadow(m.getName(), MOD_X - sl + 10, y + 6, selected ? GREEN : TEXT);
            this.textRenderer.drawWithShadow(state, stateX, y + 6, stateColor);
            any |= hover;
        }
        endClip();
        return any;
    }

    /** Y offset where the settings rows start (below title + description). */
    private int settingsTop() {
        Module m = selectedModule;
        String desc = m == null ? null : m.getDescription();
        boolean hasDesc = desc != null && !desc.isEmpty();
        return PANEL_Y + HEADER_H + 4 + (hasDesc ? 12 : 0);
    }

    private boolean drawSettings(int mouseX, int mouseY) {
        int sl = slide();
        boolean any = false;
        Module m = selectedModule;
        List<Setting<?>> settings = new ArrayList<>(m.getSettings());
        boolean hasDesc = m.getDescription() != null && !m.getDescription().isEmpty();
        int panelH = HEADER_H + 6 + (hasDesc ? 12 : 0) + (settings.size() + 2) * (ROW_H + 2);
        panel(SET_X + sl, PANEL_Y, SET_W, panelH);

        int titleColor = m.isEnabled() ? GREEN : TEXT;
        this.textRenderer.drawWithShadow(m.getName(), SET_X + sl + 8, PANEL_Y + 3, titleColor);
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
            this.textRenderer.drawWithShadow(desc, SET_X + sl + 8, PANEL_Y + 3 + 10, TEXT_DIM);
        }

        int y = settingsTop();

        // Toggle row
        any |= row(SET_X + sl, y, SET_W, ROW_H, mouseX, mouseY);
        this.textRenderer.drawWithShadow("Enabled", SET_X + sl + 10, y + 6, TEXT);
        String state = m.isEnabled() ? "ON" : "OFF";
        this.textRenderer.drawWithShadow(state, SET_X + sl + SET_W - 10 - this.textRenderer.getStringWidth(state), y + 6,
                m.isEnabled() ? GREEN : TEXT_DIM);
        y += ROW_H + 2;

        // Keybind row
        String bindText = waitingForKey ? "Press key... (Esc = none)"
                : "Bind: " + keyLabel(m);
        any |= row(SET_X + sl, y, SET_W, ROW_H, mouseX, mouseY);
        this.textRenderer.drawWithShadow(bindText, SET_X + sl + 10, y + 6, waitingForKey ? GREEN : TEXT_DIM);
        y += ROW_H + 2;

        // Settings
        for (Setting<?> s : settings) {
            any |= row(SET_X + sl, y, SET_W, ROW_H, mouseX, mouseY);
            this.textRenderer.drawWithShadow(s.getLabel(), SET_X + sl + 10, y + 6, TEXT);
            String valueText = editingText && s == editingSetting ? editBuffer + "\u2584" : s.displayString();
            int valueColor = s.isText() ? TEXT_DIM : GREEN;
            this.textRenderer.drawWithShadow(valueText,
                    SET_X + sl + SET_W - 10 - this.textRenderer.getStringWidth(valueText), y + 6, valueColor);
            y += ROW_H + 2;
        }
        return any;
    }

    private String keyLabel(Module m) {
        String label = m.getKeyLabel();
        return (label == null || label.isEmpty() || "None".equals(label)) ? "NONE" : label;
    }

    // ── interaction ───────────────────────────────────────────────

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        editingText = false;
        editingSetting = null;
        waitingForKey = false;
        int sl = slide();

        // Categories (clicking one also exits search mode)
        int y = PANEL_Y + HEADER_H + 4;
        for (Category category : Category.values()) {
            if (in(mouseX, mouseY, CAT_X - sl, y, CAT_W, ROW_H)) {
                if (category != selectedCategory) {
                    selectedCategory = category;
                    selectedModule = null;
                    scrollTarget = 0;
                }
                searchBuffer = "";
                searching = false;
                scrollTarget = 0;
                return;
            }
            y += ROW_H + 2;
        }

        // Search box
        if (in(mouseX, mouseY, MOD_X - sl, SEARCH_Y, MOD_W, SEARCH_H)) {
            searching = true;
            return;
        }

        // Modules
        List<Module> modules = currentCategoryModules();
        int index = moduleRowAt(mouseX, mouseY, modules);
        if (index >= 0 && index < modules.size()) {
            Module m = modules.get(index);
            if (button == 1) {
                selectedModule = m;
            } else {
                m.toggle();
                QynlClient189.getInstance().getModuleManager().saveToConfig();
            }
            return;
        }

        // Settings panel
        if (selectedModule != null) {
            handleSettingsClick(mouseX, mouseY, button);
        }
    }

    /** Row index under the cursor, using the same eased layout as drawing. */
    private int moduleRowAt(int mouseX, int mouseY, List<Module> modules) {
        int sl = slide();
        if (mouseX < MOD_X - sl || mouseX >= MOD_X - sl + MOD_W) return -1;
        float yf = ROWS_TOP - scrollAnim * (ROW_H + 2);
        for (int i = 0; i < modules.size(); i++) {
            float rowY = yf + i * (ROW_H + 2);
            if (mouseY >= rowY && mouseY < rowY + ROW_H) return i;
        }
        return -1;
    }

    private void handleSettingsClick(int mouseX, int mouseY, int button) {
        Module m = selectedModule;
        List<Setting<?>> settings = new ArrayList<>(m.getSettings());
        int sl = slide();
        int y = settingsTop();

        // Toggle
        if (in(mouseX, mouseY, SET_X + sl, y, SET_W, ROW_H)) {
            m.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            return;
        }
        y += ROW_H + 2;

        // Keybind
        if (in(mouseX, mouseY, SET_X + sl, y, SET_W, ROW_H)) {
            waitingForKey = true;
            return;
        }
        y += ROW_H + 2;

        // Settings
        for (Setting<?> s : settings) {
            if (in(mouseX, mouseY, SET_X + sl, y, SET_W, ROW_H)) {
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
    public void handleMouse() {
        super.handleMouse();
        int dwheel = Mouse.getEventDWheel();
        if (dwheel != 0) {
            int dir = dwheel > 0 ? -1 : 1;
            scrollTarget = Math.max(0, scrollTarget + dir * 2);
            clampScroll();
        }
    }

    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == Keyboard.KEY_RSHIFT) {
            MinecraftClient.getInstance().openScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // Esc clears an active search first — close only when it's empty.
            if (searching || !searchBuffer.isEmpty()) {
                searchBuffer = "";
                searching = false;
                scrollTarget = 0;
                return;
            }
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

        // Search input
        if (searching) {
            if (keyCode == Keyboard.KEY_BACK) {
                if (!searchBuffer.isEmpty()) {
                    searchBuffer = searchBuffer.substring(0, searchBuffer.length() - 1);
                    scrollTarget = 0;
                }
            } else if (keyCode == Keyboard.KEY_RETURN) {
                searching = false;
            } else if (character >= 32 && character != 167) {
                searchBuffer += character;
                scrollTarget = 0;
            }
            return;
        }

        // Navigation
        if (keyCode == Keyboard.KEY_DOWN) {
            List<Module> modules = currentCategoryModules();
            if (!modules.isEmpty()) {
                int idx = selectedModule == null ? -1 : modules.indexOf(selectedModule);
                if (idx < modules.size() - 1) {
                    selectedModule = modules.get(idx + 1);
                    if (idx + 1 >= scrollTarget + VISIBLE_ROWS) scrollTarget++;
                } else if (idx == -1) {
                    selectedModule = modules.get(0);
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
                    if (idx - 1 < scrollTarget) scrollTarget--;
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
                scrollTarget = 0;
            }
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && selectedModule != null) {
            selectedModule.toggle();
            QynlClient189.getInstance().getModuleManager().saveToConfig();
            return;
        }

        // Typing any printable character starts a search.
        if (character >= 32 && character != 167) {
            searching = true;
            searchBuffer = String.valueOf(character);
            scrollTarget = 0;
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

    private boolean row(int x, int y, int w, int h, int mouseX, int mouseY) {
        if (in(mouseX, mouseY, x, y, w, h)) {
            fill(x + 1, y, x + w - 1, y + h, blend(PANEL, PANEL_HOVER, hoverAnim));
            return true;
        }
        return false;
    }

    private boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}

package com.qynl.client189;

import com.qynl.client189.modules.StreamerModeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean HUD renderer for 1.8.9.
 *
 * <p>Draws the title and the enabled module list in the top-left corner as a
 * single soft panel with an accent edge, showing the active mode of each
 * assist (e.g. "AimAssist · Silent") and the toggle key on the right.
 * Everything is hidden when StreamerMode is on, so recordings stay
 * OBS-safe.</p>
 *
 * <p>Uses only stable 1.8.9 Yarn APIs: {@link DrawableHelper#fill},
 * {@link TextRenderer#draw} and {@link TextRenderer#getStringWidth}.</p>
 */
public final class HudRenderer189 {

    public static final int PANEL = 0x40000000;
    public static final int ACCENT = 0xFF6EE7A0;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFF9CA3AF;

    private HudRenderer189() {}

    public static void render(MinecraftClient client) {
        if (StreamerModeModule.shouldHide()) return;

        ModuleManager modules = QynlClient189.getInstance().getModuleManager();
        TextRenderer font = client.textRenderer;

        // Title
        String title = "QynlClient";
        font.draw(title, 4, 4, ACCENT);
        font.draw("v" + QynlClient189.VERSION, 4 + font.getStringWidth(title) + 6, 6, GRAY);

        List<Module> enabled = new ArrayList<>();
        for (Module m : modules.getModules()) {
            if (m.isEnabled() && !"ClickGUI".equals(m.getName())) {
                enabled.add(m);
            }
        }
        if (enabled.isEmpty()) return;

        // Compute max label width (name + mode suffix, or the key when wider)
        int maxWidth = 0;
        for (Module m : enabled) {
            String label = m.getName() + modeSuffix(m);
            String key = m.getKeyLabel();
            int w = font.getStringWidth(label);
            if (key != null && !key.isEmpty() && !"None".equals(key)) {
                w = Math.max(w, font.getStringWidth(key) + 4);
            }
            maxWidth = Math.max(maxWidth, w);
        }

        int x = 4;
        int y = 22;
        int rowHeight = 11;
        int padX = 7;
        int panelW = maxWidth + padX * 2 + 2;
        int panelH = enabled.size() * rowHeight + 4;

        // Single panel + left accent edge
        DrawableHelper.fill(x, y, x + panelW, y + panelH, PANEL);
        DrawableHelper.fill(x, y, x + 2, y + panelH, ACCENT);

        int rowY = y + 2;
        for (Module m : enabled) {
            int nameX = x + padX + 2;
            font.draw(m.getName(), nameX, rowY, WHITE);

            // Mode suffix
            String suffix = modeSuffix(m);
            if (!suffix.isEmpty()) {
                font.draw(suffix, nameX + font.getStringWidth(m.getName()), rowY, ACCENT);
            }

            // Key on the right
            String key = m.getKeyLabel();
            if (key != null && !key.isEmpty() && !"None".equals(key)) {
                int keyX = x + panelW - padX - font.getStringWidth(key);
                font.draw(key, keyX, rowY, GRAY);
            }

            rowY += rowHeight;
        }
    }

    /** Shows the active mode of assist modules, e.g. " · Silent". */
    private static String modeSuffix(Module m) {
        Setting<?> mode = m.getSetting("mode");
        if (mode == null) return "";
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) return "";
        return " \u00b7 " + v;
    }
}

package com.qynl.client189;

import com.qynl.client189.modules.StreamerModeModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal HUD renderer for 1.8.9.
 *
 * <p>Draws the enabled module list in the top-left corner, showing the
 * active mode of each assist (e.g. "AimAssist · Silent"). Everything is
 * hidden when StreamerMode is on, so recordings stay OBS-safe.</p>
 *
 * <p>Uses only stable 1.8.9 Yarn APIs: {@link DrawableHelper#fill},
 * {@link TextRenderer#draw} and {@link TextRenderer#getStringWidth}.</p>
 */
public final class HudRenderer189 {

    public static final int BG = 0x66000000;
    public static final int ACCENT = 0xFF6EE7A0;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFF9CA3AF;

    private HudRenderer189() {}

    public static void render(MinecraftClient client) {
        if (StreamerModeModule.shouldHide()) return;

        ModuleManager modules = QynlClient189.getInstance().getModuleManager();
        TextRenderer font = client.textRenderer;

        List<Module> enabled = new ArrayList<>();
        for (Module m : modules.getModules()) {
            if (m.isEnabled() && !"ClickGUI".equals(m.getName())) {
                enabled.add(m);
            }
        }
        if (enabled.isEmpty()) return;

        // Compute max label width
        int maxWidth = 0;
        for (Module m : enabled) {
            int w = font.getStringWidth(m.getName() + modeSuffix(m));
            maxWidth = Math.max(maxWidth, w);
        }

        int x = 4;
        int y = 26;
        int rowHeight = font.fontHeight + 6;

        for (Module m : enabled) {
            // Row background
            fill(client, x, y, x + maxWidth + 22, y + rowHeight, BG);
            // Accent bar
            fill(client, x, y, x + 2, y + rowHeight, ACCENT);

            // Name
            font.draw(m.getName(), x + 16, y + 3, WHITE);

            // Mode suffix
            String suffix = modeSuffix(m);
            if (!suffix.isEmpty()) {
                font.draw(suffix, x + 16 + font.getStringWidth(m.getName()), y + 3, ACCENT);
            }

            // Key on the right
            String key = m.getKeyLabel();
            if (key != null && !key.isEmpty() && !"None".equals(key)) {
                int keyX = x + maxWidth + 22 - font.getStringWidth(key) - 3;
                font.draw(key, keyX, y + 3, GRAY);
            }

            y += rowHeight;
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

    private static void fill(MinecraftClient client, int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }
}

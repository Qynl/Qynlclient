package com.qynl.client189;

import com.qynl.client189.modules.TextGuiModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Qyn-L HUD — renders the Text GUI: the list of enabled modules in the
 * chosen corner, Vape-style (clean text, no panels, mode suffix in grey).
 * Only renders while {@link TextGuiModule} is enabled.
 */
public final class HudRenderer189 {

    private HudRenderer189() {
    }

    public static void render(MinecraftClient client) {
        if (!TextGuiModule.isActive()) {
            return;
        }
        // StreamerMode hides the module list from OBS/recordings.
        if (com.qynl.client189.modules.StreamerModeModule.shouldHide()) {
            return;
        }
        TextRenderer font = client.textRenderer;

        List<Module> enabled = new ArrayList<>();
        for (Module module : QynlClient189.getInstance().getModuleManager().getModules()) {
            if (module.isEnabled()) {
                enabled.add(module);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }

        boolean right = TextGuiModule.isRight();
        int accent = TextGuiModule.textColor();
        int dim = 0xFF6B7280;

        int y = 3;
        for (Module module : enabled) {
            String label = module.getName();
            String mode = modeSuffix(module);
            int color = accent;
            if (right) {
                String full = label + mode;
                int x = client.width - 2 - font.getStringWidth(full);
                font.drawWithShadow(label, x, y, color);
                if (!mode.isEmpty()) {
                    font.drawWithShadow(mode, x + font.getStringWidth(label), y, dim);
                }
            } else {
                font.drawWithShadow(label, 2, y, color);
                if (!mode.isEmpty()) {
                    font.drawWithShadow(mode, 2 + font.getStringWidth(label), y, dim);
                }
            }
            y += 10;
        }
    }

    /** Shows the active mode of a module, e.g. " · Auto". */
    private static String modeSuffix(Module m) {
        Setting<?> mode = m.getSetting("mode");
        if (mode == null) {
            return "";
        }
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) {
            return "";
        }
        return " \u00b7 " + v;
    }
}

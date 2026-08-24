package com.qynl.client189;

import com.qynl.client189.modules.TextGuiModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Qyn-L HUD — renders the Text GUI: the list of enabled modules in the
 * chosen corner, Vape-style (clean text, no panels, mode suffix in grey),
 * plus an optional one-line info widget (FPS / ping / TPS / coordinates).
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

        // Info widget — a single dim line under the list. Shows even when no
        // modules are enabled, so it doubles as a standalone stats readout.
        if (TextGuiModule.infoEnabled()) {
            String info = infoLine(client);
            if (!info.isEmpty()) {
                y += 3;
                if (right) {
                    font.drawWithShadow(info, client.width - 2 - font.getStringWidth(info), y, dim);
                } else {
                    font.drawWithShadow(info, 2, y, dim);
                }
            }
        }
    }

    /** "60 fps · 42 ms · 20 tps · 100 64 -200" — missing data is skipped. */
    private static String infoLine(MinecraftClient client) {
        StringBuilder sb = new StringBuilder();
        sb.append(MinecraftClient.getCurrentFps()).append(" fps");
        if (PingTracker.hasPing()) {
            sb.append(" \u00b7 ").append(PingTracker.getPingMs()).append(" ms");
        }
        QynlClient189 qynl = QynlClient189.getInstance();
        int tps = qynl == null ? 20 : (int) Math.round(qynl.getTps());
        sb.append(" \u00b7 ").append(Math.max(0, Math.min(20, tps))).append(" tps");
        if (client.player != null) {
            sb.append(" \u00b7 ").append((int) Math.floor(client.player.x))
              .append(' ').append((int) Math.floor(client.player.y))
              .append(' ').append((int) Math.floor(client.player.z));
        }
        return sb.toString();
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

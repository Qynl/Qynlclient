package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.module.modules.StreamerModeModule;
import com.qynl.client.module.modules.TextGuiModule;
import com.qynl.client.util.PingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vape-style HUD — clean text only. No boxes, no panels, no hover chrome:
 * a small watermark, the module arraylist with a slide-in animation, and a
 * single dim info line. Everything is drawn with the shadow flag, exactly
 * like Vape's minimal overlay.
 *
 * <p>Driven by {@link TextGuiModule}: it decides whether the arraylist is
 * shown, which side it anchors to (TopLeft / TopRight), the text color and
 * whether the info line is drawn.</p>
 */
public class HudRenderer {
    public static final int ACCENT = 0xFF55FF55;
    private static final int DIM    = 0xFF6B7280;
    private static final int WHITE  = 0xFFE5E7EB;

    /** Module name → when it last became enabled (ms), for the slide-in. */
    private final Map<String, Long> enabledSince = new HashMap<>();
    private final Map<String, Float> slide = new HashMap<>();

    // ── render entry ────────────────────────────────────────────

    public void render(GuiGraphics g, Minecraft mc, float partialTick) {
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (StreamerModeModule.shouldHide("any")) return;

        int w = mc.getWindow().getGuiScaledWidth();

        renderWatermark(g, mc);
        if (TextGuiModule.isActive()) {
            renderArrayList(g, mc, w);
            if (TextGuiModule.infoEnabled()) {
                renderInfo(g, mc, w);
            }
        }
    }

    // ── watermark ───────────────────────────────────────────────

    private void renderWatermark(GuiGraphics g, Minecraft mc) {
        String line = "Qynl  v" + QynlClient.VERSION;
        g.drawString(mc.font, line, 2, 2, ACCENT, true);
    }

    // ── arraylist (enabled modules, Text GUI) ───────────────────

    private void renderArrayList(GuiGraphics g, Minecraft mc, int w) {
        ModuleManager mm = QynlClient.getInstance().getModuleManager();
        List<Module> list = mm.getModules().stream()
                .filter(m -> m.isEnabled()
                        && !"Text GUI".equals(m.getName())
                        && !"StreamerMode".equals(m.getName()))
                .toList();
        if (list.isEmpty()) return;

        boolean right = TextGuiModule.isRight();
        int color = TextGuiModule.textColor();
        long now = System.currentTimeMillis();

        int y = 16;
        for (Module m : list) {
            String name = m.getName();
            String mode = modeSuffix(m);

            // Slide-in animation: newly enabled modules ease in from the edge.
            if (!enabledSince.containsKey(name)) {
                enabledSince.put(name, now);
                slide.put(name, 0.0F);
            }
            float start = (now - enabledSince.get(name)) / 160.0F;
            float progress = Math.min(1.0F, start);
            float ease = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
            float offset = 12.0F * (1.0F - ease);
            slide.put(name, offset);

            String full = name + mode;
            if (right) {
                int x = w - 2 - mc.font.width(full) + (int) offset;
                g.drawString(mc.font, name, x, y, color, true);
                if (!mode.isEmpty()) {
                    g.drawString(mc.font, mode, x + mc.font.width(name), y, DIM, true);
                }
            } else {
                int x = 2 - (int) offset;
                g.drawString(mc.font, name, x, y, color, true);
                if (!mode.isEmpty()) {
                    g.drawString(mc.font, mode, x + mc.font.width(name), y, DIM, true);
                }
            }
            y += 10;
        }

        // Forget modules that turned off so their next enable animates again.
        enabledSince.keySet().removeIf(n -> list.stream().noneMatch(m -> m.getName().equals(n)));
        slide.keySet().removeIf(n -> list.stream().noneMatch(m -> m.getName().equals(n)));
    }

    /** Shows the active mode of a module, e.g. " · Auto" (empty when Default). */
    private static String modeSuffix(Module m) {
        Setting<?> mode = m.getSetting("mode");
        if (mode == null) return "";
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) return "";
        return " \u00b7 " + v;
    }

    // ── info line (FPS / ping / TPS / coords) ───────────────────

    private void renderInfo(GuiGraphics g, Minecraft mc, int w) {
        String line = infoLine(mc);
        if (line.isEmpty()) return;
        boolean right = TextGuiModule.isRight();
        int x = right ? w - 2 - mc.font.width(line) : 2;
        int y = 16 + enabledCount(mc) * 10 + 3;
        g.drawString(mc.font, line, x, y, DIM, true);
    }

    private static int enabledCount(Minecraft mc) {
        return (int) QynlClient.getInstance().getModuleManager().getModules().stream()
                .filter(m -> m.isEnabled()
                        && !"Text GUI".equals(m.getName())
                        && !"StreamerMode".equals(m.getName()))
                .count();
    }

    /** "60 fps · 42 ms · 20 tps · 100 64 -200" — missing data is skipped. */
    private static String infoLine(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        sb.append(mc.getFps()).append(" fps");
        if (PingTracker.hasPing()) {
            sb.append(" \u00b7 ").append(PingTracker.getPingMs()).append(" ms");
        } else {
            int tab = tabPing(mc);
            if (tab >= 0) sb.append(" \u00b7 ").append(tab).append(" ms");
        }
        int tps = (int) Math.round(QynlClient.getInstance().getTps());
        sb.append(" \u00b7 ").append(Math.max(0, Math.min(20, tps))).append(" tps");
        if (mc.player != null) {
            sb.append(" \u00b7 ").append((int) Math.floor(mc.player.getX()))
              .append(' ').append((int) Math.floor(mc.player.getY()))
              .append(' ').append((int) Math.floor(mc.player.getZ()));
        }
        return sb.toString();
    }

    private static int tabPing(Minecraft mc) {
        try {
            if (mc.getConnection() != null && mc.player != null) {
                net.minecraft.client.multiplayer.PlayerInfo info =
                        mc.getConnection().getPlayerInfo(mc.player.getGameProfile().getId());
                if (info != null) return info.getLatency();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}

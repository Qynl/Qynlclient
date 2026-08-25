package com.qynl.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.module.modules.StreamerModeModule;
import com.qynl.client.module.modules.TextGuiModule;
import com.qynl.client.util.PingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vape glassmorphism HUD — frosted-glass panels.
 *
 * <p>Every element lives in a translucent rounded rectangle with a soft
 * vertical gradient and a 1px light border ring (the glass edge). The
 * watermark, the module arraylist and the info widget are separate glass
 * panels; arraylist rows slide in from the edge when a module enables.</p>
 *
 * <p>Driven by {@link TextGuiModule}: it decides whether the arraylist is
 * shown, which side it anchors to (TopLeft / TopRight), the text color and
 * whether the info widget is drawn.</p>
 */
public class HudRenderer {
    public static final int ACCENT = 0xFF55FF55;

    // ── text ──
    private static final int TEXT = 0xFFECECEC;
    private static final int DIM  = 0xFF9CA3AF;

    // ── glass palette ──
    private static final int GLASS_TOP    = 0x99141414; // lighter top edge
    private static final int GLASS_BOTTOM = 0x99070707; // deeper bottom
    private static final int BORDER       = 0x4DFFFFFF; // 30% white ring
    private static final int BORDER_DIM   = 0x1FFFFFFF; // faint bottom edge

    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;
    private static final int ROW_H = 10;
    private static final int RADIUS = 6;

    /** Module name → when it last became enabled (ms), for the slide-in. */
    private final Map<String, Long> enabledSince = new HashMap<>();

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

    // ── watermark glass panel ───────────────────────────────────

    private void renderWatermark(GuiGraphics g, Minecraft mc) {
        String name = "Qynl";
        String ver = " v" + QynlClient.VERSION;
        int tw = mc.font.width(name) + mc.font.width(ver);
        int pw = tw + PAD_X * 2, ph = 10 + PAD_Y * 2;
        float x = 2, y = 2;

        glassPanel(g, x, y, pw, ph, RADIUS);
        g.drawString(mc.font, name, (int) (x + PAD_X), (int) (y + PAD_Y), TEXT, true);
        g.drawString(mc.font, ver, (int) (x + PAD_X + mc.font.width(name)),
                (int) (y + PAD_Y), ACCENT, true);
    }

    // ── arraylist glass panel ───────────────────────────────────

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

        // measure the widest row
        int maxW = 0;
        for (Module m : list) {
            maxW = Math.max(maxW, mc.font.width(m.getName()) + mc.font.width(modeSuffix(m)));
        }

        int pw = maxW + PAD_X * 2 + 2;
        int ph = list.size() * ROW_H + PAD_Y * 2 + 2;
        float x = right ? w - 2 - pw : 2;
        float y = 22;

        glassPanel(g, x, y, pw, ph, RADIUS);

        int ry = (int) (y + PAD_Y + 1);
        for (Module m : list) {
            String name = m.getName();
            String mode = modeSuffix(m);

            // Slide-in: newly enabled modules ease in from the anchored edge.
            float offset = 0.0F;
            if (!enabledSince.containsKey(name)) {
                enabledSince.put(name, now);
            }
            float progress = Math.min(1.0F, (now - enabledSince.get(name)) / 180.0F);
            float ease = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
            offset = 14.0F * (1.0F - ease);

            // right side: name right-aligned, mode to its right (dim)
            if (right) {
                int nameX = (int) (x + pw - PAD_X - 1 - mc.font.width(name) - mc.font.width(mode)) + (int) offset;
                g.drawString(mc.font, name, nameX, ry, color, true);
                if (!mode.isEmpty()) {
                    g.drawString(mc.font, mode, nameX + mc.font.width(name), ry, DIM, true);
                }
            } else {
                int baseX = (int) (x + PAD_X + 1);
                g.drawString(mc.font, name, baseX - (int) offset, ry, color, true);
                if (!mode.isEmpty()) {
                    g.drawString(mc.font, mode, baseX + mc.font.width(name) - (int) offset, ry, DIM, true);
                }
            }
            ry += ROW_H;
        }

        // Forget disabled modules so their next enable animates again.
        enabledSince.keySet().removeIf(n -> list.stream().noneMatch(m -> m.getName().equals(n)));
    }

    /** Shows the active mode of a module, e.g. " · Auto" (empty when Default). */
    private static String modeSuffix(Module m) {
        Setting<?> mode = m.getSetting("mode");
        if (mode == null) return "";
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) return "";
        return " \u00b7 " + v;
    }

    // ── info glass panel (FPS / ping / TPS / coords) ────────────

    private void renderInfo(GuiGraphics g, Minecraft mc, int w) {
        String line = infoLine(mc);
        if (line.isEmpty()) return;
        boolean right = TextGuiModule.isRight();
        int pw = mc.font.width(line) + PAD_X * 2;
        int ph = 10 + PAD_Y * 2;
        int listH = enabledCount(mc) * ROW_H + PAD_Y * 2 + 2;
        float x = right ? w - 2 - pw : 2;
        float y = 22 + listH + 4;

        glassPanel(g, x, y, pw, ph, RADIUS);
        g.drawString(mc.font, line, (int) (x + PAD_X), (int) (y + PAD_Y), DIM, true);
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

    // ── glass rendering ─────────────────────────────────────────

    /** Translucent rounded panel: light border ring + inset gradient fill. */
    private static void glassPanel(GuiGraphics g, float x, float y, float w, float h, float r) {
        if (w <= 2 || h <= 2) return;
        // Border ring drawn as a slightly larger rounded rect underneath.
        fillRound(g, x, y, w, h, r, BORDER, BORDER_DIM);
        // Glass body inset by 1px on every side.
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0.0F, r - 1), GLASS_TOP, GLASS_BOTTOM);
    }

    /**
     * Rounded rectangle via a triangle fan through the position-color
     * shader — vanilla's GUI shader with blending, same as HUD fills.
     * The color interpolates vertically from {@code topColor} to
     * {@code bottomColor} for the frosted-glass gradient.
     */
    private static void fillRound(GuiGraphics g, float x, float y, float w, float h, float r,
                                  int topColor, int bottomColor) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2.0F);
        float cx = x + w / 2.0F, cy = y + h / 2.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

        // Fan center — average color so the gradient reads soft.
        bb.addVertex(cx, cy, 0).setColor(lerpColor(topColor, bottomColor, 0.5F));

        // Perimeter in clockwise order: 4 corner arcs of π/2 each.
        float[][] corners = {
                {x + w - r, y + r},       // top-right
                {x + w - r, y + h - r},   // bottom-right
                {x + r, y + h - r},       // bottom-left
                {x + r, y + r}            // top-left
        };
        float[] starts = {(float) (Math.PI * 1.5), 0.0F, (float) (Math.PI / 2.0), (float) Math.PI};
        final int SEG = 8;
        for (int c = 0; c < 4; c++) {
            float ccx = corners[c][0], ccy = corners[c][1];
            double a0 = starts[c];
            for (int s = 0; s <= SEG; s++) {
                double a = a0 + (Math.PI / 2.0) * s / SEG;
                float px = ccx + (float) Math.cos(a) * r;
                float py = ccy + (float) Math.sin(a) * r;
                int col = lerpColor(topColor, bottomColor,
                        Math.max(0.0F, Math.min(1.0F, (py - y) / h)));
                bb.addVertex(px, py, 0).setColor(col);
            }
        }

        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.disableBlend();
    }

    /** ARGB color lerp (packed ints). */
    private static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24) & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24) & 255;
        return ((Math.round(aa + (ba - aa) * t) & 255) << 24)
                | ((Math.round(ar + (br - ar) * t) & 255) << 16)
                | ((Math.round(ag + (bg - ag) * t) & 255) << 8)
                | (Math.round(ab + (bb - ab) * t) & 255);
    }
}

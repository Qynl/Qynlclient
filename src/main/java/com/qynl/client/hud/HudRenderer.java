package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.modules.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Vape-Lite style HUD — dark translucent panels, green accents,
 * watermark header, compact module arraylist, right-side info stack.
 */
public class HudRenderer {
    // ── palette ──
    private static final int BG       = 0x80000000;
    private static final int HOVER    = 0x20FFFFFF;
    private static final int ACCENT   = 0xFF55FF55;
    private static final int WHITE    = 0xFFD0D0D0;
    private static final int GRAY     = 0xFF7A7A7A;
    private static final int RED      = 0xFFFF5555;
    private static final int YELLOW   = 0xFFFFAA00;
    private static final int DARK_RED = 0xB3140000;

    private final List<int[]>  clickRects = new ArrayList<>();
    private final List<Module> clickMods  = new ArrayList<>();
    private boolean prevClick;

    // ── render entry ────────────────────────────────────────────

    public void render(GuiGraphics g, Minecraft mc) {
        clickRects.clear(); clickMods.clear();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null) return;
        if (StreamerModeModule.shouldHide("any")) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (!StreamerModeModule.shouldHide("all")) {
            renderWatermark(g, mc, w);
            renderArrayList(g, mc, w);
        }
        if (!StreamerModeModule.shouldHide("hud")) {
            renderInfo(g, mc, w, h);
            renderKeystrokes(g, mc);
            renderDuraWarn(g, mc);
        }
    }

    // ── watermark ───────────────────────────────────────────────

    private void renderWatermark(GuiGraphics g, Minecraft mc, int w) {
        String line = "Qynl  v" + QynlClient.VERSION;
        int tw = mc.font.width(line);
        g.fill(1, 1, tw + 8, 13, BG);
        g.drawString(mc.font, line, 4, 3, ACCENT, false);
    }

    // ── arraylist (enabled modules, left side) ──────────────────

    private void renderArrayList(GuiGraphics g, Minecraft mc, int w) {
        ModuleManager mm = QynlClient.getInstance().getModuleManager();
        List<Module> list = mm.getModules().stream()
                .filter(m -> m.isEnabled() && !"ClickGUI".equals(m.getName()))
                .toList();
        if (list.isEmpty()) return;

        // measure
        int maxW = 0;
        for (Module m : list) {
            String s = m.getName();
            Setting<?> mode = m.getSetting("mode");
            if (mode != null && !"Default".equals(String.valueOf(mode.getValue())))
                s += " \u00b7 " + mode.getValue();
            maxW = Math.max(maxW, mc.font.width(s));
        }

        int x = 4, y = 17, rowH = 10, pad = 6;
        int pw = maxW + pad * 2 + 2;
        int ph = list.size() * rowH + 4;

        g.fill(x, y, x + pw, y + ph, BG);
        // left accent edge
        g.fill(x, y, x + 1, y + ph, ACCENT);

        double scale = mc.getWindow().getGuiScale();
        int mx = (int) (mc.mouseHandler.xpos() / scale);
        int my = (int) (mc.mouseHandler.ypos() / scale);

        int ry = y + 2;
        for (Module m : list) {
            String label = m.getName();
            Setting<?> mode = m.getSetting("mode");
            String modeStr = "";
            if (mode != null && !"Default".equals(String.valueOf(mode.getValue())))
                modeStr = " \u00b7 " + mode.getValue();

            boolean hover = mx >= x && mx < x + pw && my >= ry - 1 && my < ry + rowH;
            if (hover) g.fill(x, ry - 1, x + pw, ry + rowH - 1, HOVER);

            g.drawString(mc.font, label, x + pad + 2, ry, hover ? ACCENT : WHITE, false);
            if (!modeStr.isEmpty())
                g.drawString(mc.font, modeStr, x + pad + 2 + mc.font.width(label), ry, GRAY, false);

            clickRects.add(new int[]{x, ry - 1, x + pw, ry + rowH - 1});
            clickMods.add(m);
            ry += rowH;
        }
    }

    // ── right-side info ─────────────────────────────────────────

    private void renderInfo(GuiGraphics g, Minecraft mc, int w, int h) {
        ModuleManager mm = QynlClient.getInstance().getModuleManager();
        InfoHudModule info = (InfoHudModule) mm.find("InfoHUD");
        TargetInfoModule ti = (TargetInfoModule) mm.find("TargetInfo");
        EffectTimersModule fx = (EffectTimersModule) mm.find("EffectTimers");
        DeathCoordsModule  dc = (DeathCoordsModule) mm.find("DeathCoords");
        CoordConvertModule cc = (CoordConvertModule) mm.find("CoordConvert");

        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        if (info != null && info.isEnabled()) {
            for (String s : new String[]{info.fps(mc), info.coords(mc), info.direction(mc),
                    info.biome(mc), info.time(mc), info.ping(mc)})
                if (!s.isEmpty()) { lines.add(s); colors.add(WHITE); }
        }
        if (ti != null && ti.isEnabled() && !ti.getInfo(mc).isEmpty())
            { lines.add(ti.getInfo(mc)); colors.add(ACCENT); }
        if (fx != null && fx.isEnabled()) {
            for (MobEffectInstance ef : fx.getEffects(mc)) {
                String name = net.minecraft.network.chat.Component.translatable(
                        ef.getEffect().value().getDescriptionId()).getString();
                String line = name + roman(ef.getAmplifier()) + "  " + fmt(ef.getDuration());
                boolean low = ef.getDuration() > 0 && ef.getDuration() / 20 <= 5;
                lines.add(line); colors.add(low ? RED : YELLOW);
            }
        }
        if (dc != null && dc.isEnabled() && !dc.getHudLine(mc).isEmpty())
            { lines.add(dc.getHudLine(mc)); colors.add(RED); }
        if (cc != null && cc.isEnabled() && !cc.getInfo(mc).isEmpty())
            { lines.add(cc.getInfo(mc)); colors.add(GRAY); }

        if (lines.isEmpty()) return;

        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, mc.font.width(s));
        int pad = 6, lh = 10, pw = maxW + pad * 2, ph = lines.size() * lh + 4;
        int px = w - pw - 3, py = 1;

        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px + pw - 1, py, px + pw, py + ph, ACCENT); // right accent edge

        for (int i = 0; i < lines.size(); i++)
            g.drawString(mc.font, lines.get(i), px + pad, py + 2 + i * lh, colors.get(i), false);
    }

    // ── keystrokes ──────────────────────────────────────────────

    private void renderKeystrokes(GuiGraphics g, Minecraft mc) {
        if (!QynlClient.getInstance().getModuleManager().isEnabled("Keystrokes")) return;
        KeystrokesModule ks = (KeystrokesModule) QynlClient.getInstance().getModuleManager().find("Keystrokes");
        if (ks == null) return;

        int s = 20, gap = 1, h = mc.getWindow().getGuiScaledHeight();
        int x = 4, y = h - s * 2 - gap - s / 2 - 6;

        drawKey(g, mc, ks, KeystrokesModule.KEY_W,     x + s + gap, y, s, s, "W");
        int r2 = y + s + gap;
        drawKey(g, mc, ks, KeystrokesModule.KEY_A,     x, r2, s, s, "A");
        drawKey(g, mc, ks, KeystrokesModule.KEY_S,     x + s + gap, r2, s, s, "S");
        drawKey(g, mc, ks, KeystrokesModule.KEY_D,     x + (s + gap) * 2, r2, s, s, "D");
        drawKey(g, mc, ks, KeystrokesModule.KEY_SPACE, x, r2 + s + gap, s * 3 + gap * 2, s / 2, "");

        int mx = x + (s + gap) * 3 + 8;
        drawKey(g, mc, ks, KeystrokesModule.MOUSE_L, mx, r2, 34, s,
                "L " + ks.getCps(mc, KeystrokesModule.MOUSE_L));
        drawKey(g, mc, ks, KeystrokesModule.MOUSE_R, mx + 36, r2, 34, s,
                "R " + ks.getCps(mc, KeystrokesModule.MOUSE_R));
    }

    private void drawKey(GuiGraphics g, Minecraft mc, KeystrokesModule ks,
                          int key, int x, int y, int w, int h, String label) {
        boolean down = ks.isKeyDown(mc, key);
        int bg = down ? ACCENT : BG;
        g.fill(x, y, x + w, y + h, bg);
        if (down) g.fill(x, y, x + w, y + 1, 0xFF2E7D4F);
        int c = down ? 0xFF0B1B12 : WHITE;
        Component t = Component.literal(label);
        int tw = mc.font.width(t);
        g.drawString(mc.font, t, x + (w - tw) / 2, y + (h - mc.font.lineHeight) / 2, c, false);
    }

    // ── durability warning ──────────────────────────────────────

    private void renderDuraWarn(GuiGraphics g, Minecraft mc) {
        DurabilityWarnModule dw = (DurabilityWarnModule) QynlClient.getInstance()
                .getModuleManager().find("DurabilityWarn");
        if (dw == null || !dw.isEnabled() || !dw.isWarning(mc)) return;
        String msg = "\u26a0 TOOL ABOUT TO BREAK";
        int tw = mc.font.width(msg);
        int x = (mc.getWindow().getGuiScaledWidth() - tw) / 2;
        g.fill(x - 6, 22, x + tw + 6, 33, DARK_RED);
        g.drawString(mc.font, msg, x, 24, RED, false);
    }

    // ── click handling ──────────────────────────────────────────

    public void handleClick(Minecraft mc) {
        if (mc.screen != null || mc.player == null) { prevClick = false; return; }
        long h = mc.getWindow().getWindow();
        boolean now = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (now && !prevClick) {
            double s = mc.getWindow().getGuiScale();
            double mx = mc.mouseHandler.xpos() / s, my = mc.mouseHandler.ypos() / s;
            for (int i = 0; i < clickRects.size(); i++) {
                int[] r = clickRects.get(i);
                if (mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3]) {
                    clickMods.get(i).toggle();
                    QynlClient.getInstance().getModuleManager().saveToConfig();
                    break;
                }
            }
        }
        prevClick = now;
    }

    // ── helpers ─────────────────────────────────────────────────

    private static String fmt(int ticks) {
        if (ticks <= 0) return "0:00";
        int sec = (ticks + 19) / 20;
        return sec / 60 + ":" + String.format("%02d", sec % 60);
    }

    private static String roman(int amp) {
        return switch (amp) { case 0 -> ""; case 1 -> " II"; case 2 -> " III";
            case 3 -> " IV"; default -> " V"; };
    }
}
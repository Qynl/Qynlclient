package com.qynl.client.hud;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.module.modules.StreamerModeModule;
import com.qynl.client.module.modules.TextGuiModule;
import com.qynl.client.util.PingTracker;	import net.minecraft.client.Minecraft;
	import net.minecraft.client.gui.GuiGraphics;
	import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Vape-Lite style HUD — dark translucent panels, green accents,
 * watermark header, compact module arraylist, right-side info stack.
 *
 * <p>Driven by {@link TextGuiModule}: it decides whether the arraylist is
 * shown, which side it anchors to (TopLeft / TopRight), the text color and
 * whether the FPS / ping / TPS / coordinates info line is drawn.</p>
 */
public class HudRenderer {
    // ── palette ──
    public static final int BG       = 0x80000000;
    public static final int PANEL    = 0xC0121212;
    private static final int HOVER    = 0x20FFFFFF;
    public static final int ACCENT   = 0xFF55FF55;
    private static final int WHITE    = 0xFFD0D0D0;
    private static final int GRAY     = 0xFF7A7A7A;

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
        int tw = mc.font.width(line);
        g.fill(1, 1, tw + 8, 13, BG);
        g.drawString(mc.font, line, 4, 3, ACCENT, false);
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

        // measure
        int maxW = 0;
        for (Module m : list) {
            String s = m.getName();
            Setting<?> mode = m.getSetting("mode");
            if (mode != null && !"Default".equals(String.valueOf(mode.getValue())))
                s += " \u00b7 " + mode.getValue();
            maxW = Math.max(maxW, mc.font.width(s));
        }

        int pad = 6, rowH = 10;
        int pw = maxW + pad * 2 + 2;
        int ph = list.size() * rowH + 4;

        boolean right = TextGuiModule.isRight();
        int x = right ? w - pw - 3 : 4;
        int y = 17;
        // accent edge on the anchor side (left edge for TopLeft, right for TopRight)
        int edgeX = right ? x + pw - 1 : x;

        g.fill(x, y, x + pw, y + ph, BG);
        g.fill(edgeX, y, edgeX + 1, y + ph, ACCENT);

        double scale = mc.getWindow().getGuiScale();
        int mx = (int) (mc.mouseHandler.xpos() / scale);
        int my = (int) (mc.mouseHandler.ypos() / scale);

        int color = TextGuiModule.textColor();

        int ry = y + 2;
        for (Module m : list) {
            String label = m.getName();
            Setting<?> mode = m.getSetting("mode");
            String modeStr = "";
            if (mode != null && !"Default".equals(String.valueOf(mode.getValue())))
                modeStr = " \u00b7 " + mode.getValue();

            boolean hover = mx >= x && mx < x + pw && my >= ry - 1 && my < ry + rowH;
            if (hover) g.fill(x, ry - 1, x + pw, ry + rowH - 1, HOVER);

            g.drawString(mc.font, label, x + pad + 2, ry, hover ? 0xFFFFFFFF : color, false);
            if (!modeStr.isEmpty())
                g.drawString(mc.font, modeStr, x + pad + 2 + mc.font.width(label), ry, GRAY, false);

            clickRects.add(new int[]{x, ry - 1, x + pw, ry + rowH - 1});
            clickMods.add(m);
            ry += rowH;
        }
    }

    // ── right-side info (FPS / ping / TPS / coords) ─────────────

    private void renderInfo(GuiGraphics g, Minecraft mc, int w) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        lines.add(mc.getFps() + " fps");
        colors.add(WHITE);

        int ping = ping(mc);
        lines.add(ping >= 0 ? ping + " ms" : "-- ms");
        colors.add(ping >= 0 && ping < 120 ? WHITE : 0xFFFFAA00);

        int tps = (int) Math.round(QynlClient.getInstance().getTps());
        lines.add(tps + " tps");
        colors.add(tps >= 18 ? WHITE : 0xFFFFAA00);

        String coords = String.format("X %d  Y %d  Z %d",
                (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ());
        lines.add(coords);
        colors.add(WHITE);

        lines.add(direction(mc));
        colors.add(GRAY);

        int maxW = 0;
        for (String s : lines) maxW = Math.max(maxW, mc.font.width(s));
        int pad = 6, lh = 10, pw = maxW + pad * 2, ph = lines.size() * lh + 4;
        int px = w - pw - 3, py = 1;

        g.fill(px, py, px + pw, py + ph, BG);
        g.fill(px + pw - 1, py, px + pw, py + ph, ACCENT); // right accent edge

        for (int i = 0; i < lines.size(); i++)
            g.drawString(mc.font, lines.get(i), px + pad, py + 2 + i * lh, colors.get(i), false);
    }

    private static int ping(Minecraft mc) {
        if (PingTracker.hasPing()) return PingTracker.getPingMs();
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

    private static String direction(Minecraft mc) {
        float yaw = mc.player.getYRot();
        String dir = "N";
        if (yaw < -157.5f || yaw >= 157.5f) dir = "N";
        else if (yaw < -112.5f) dir = "NE";
        else if (yaw < -67.5f) dir = "E";
        else if (yaw < -22.5f) dir = "SE";
        else if (yaw < 22.5f) dir = "S";
        else if (yaw < 67.5f) dir = "SW";
        else if (yaw < 112.5f) dir = "W";
        else if (yaw < 157.5f) dir = "NW";
        String facing = mc.player.getDirection().getName().toUpperCase();
        return "Facing " + facing + " (" + dir + ")";
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
    }}

package com.qynl.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Shared glassmorphism renderer — the frosted-glass look used by the HUD
 * and every ClickGUI screen: translucent rounded rectangles with a soft
 * vertical gradient and a 1px light border ring.
 */
public final class Glass {
    /** Soft mint accent — the Vape-style green, tuned down from neon. */
    public static final int ACCENT = 0xFF4ADE80;
    public static final int TEXT   = 0xFFECECEC;
    public static final int DIM    = 0xFF9CA3AF;
    public static final int ON     = ACCENT;
    public static final int OFF    = 0xFF6B7280;

    /** Frosted fill: translucent enough that the world glows through. */
    public static final int GLASS_TOP    = 0x8C161616;
    public static final int GLASS_BOTTOM = 0x8C080808;
    /** Crisp light edge on top, softer on the bottom — the glass rim. */
    public static final int BORDER       = 0x59FFFFFF;
    public static final int BORDER_DIM   = 0x21FFFFFF;
    public static final int HOVER        = 0x2EFFFFFF;
    /** 1px inner top highlight — the light catching the glass edge. */
    public static final int SHINE = 0x3DFFFFFF;

    public static final float RADIUS = 5.0F;

    private Glass() {
    }

    /** True if the point is inside the rectangle. */
    public static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Translucent rounded panel: light border ring + inset gradient fill + top shine. */
    public static void panel(GuiGraphics g, float x, float y, float w, float h, float r) {
        if (w <= 2 || h <= 2) return;
        fillRound(g, x, y, w, h, r, BORDER, BORDER_DIM);
        fillRound(g, x + 1, y + 1, w - 2, h - 2, Math.max(0.0F, r - 1), GLASS_TOP, GLASS_BOTTOM);
        // Light catches the top edge of the glass.
        if (h >= 4) {
            fillRound(g, x + 2, y + 1, w - 4, 1.5F, 0.75F, SHINE, SHINE);
        }
    }

    /** A compact glass pill (tab / button). Highlighted when {@code active}. */
    public static void pill(GuiGraphics g, float x, float y, float w, float h, boolean active, boolean hover) {
        fillRound(g, x, y, w, h, h / 2.0F,
                active ? 0xCC173A22 : (hover ? 0x66222222 : 0x59141414),
                active ? 0xCC0C2416 : (hover ? 0x55141414 : 0x59070707));
        fillRound(g, x, y, w, h, h / 2.0F,
                active ? 0x8C4ADE80 : BORDER, active ? 0x334ADE80 : BORDER_DIM);
    }

    /**
     * Rounded rectangle via a triangle fan through the position-color
     * shader. The color interpolates vertically from {@code topColor} to
     * {@code bottomColor} for the frosted-glass gradient.
     */
    public static void fillRound(GuiGraphics g, float x, float y, float w, float h, float r,
                                 int topColor, int bottomColor) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2.0F);
        float cx = x + w / 2.0F, cy = y + h / 2.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bb = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);

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
    public static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255, aa = (a >>> 24) & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255, ba = (b >>> 24) & 255;
        return ((Math.round(aa + (ba - aa) * t) & 255) << 24)
                | ((Math.round(ar + (br - ar) * t) & 255) << 16)
                | ((Math.round(ag + (bg - ag) * t) & 255) << 8)
                | (Math.round(ab + (bb - ab) * t) & 255);
    }
}

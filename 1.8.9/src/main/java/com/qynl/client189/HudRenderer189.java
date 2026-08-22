package com.qynl.client189;

import com.qynl.client189.modules.BacktrackModule;
import com.qynl.client189.modules.BlinkModule;
import com.qynl.client189.modules.CoordConvertModule;
import com.qynl.client189.modules.DeathCoordsModule;
import com.qynl.client189.modules.DurabilityWarnModule;
import com.qynl.client189.modules.EffectTimersModule;
import com.qynl.client189.modules.InfoHudModule;
import com.qynl.client189.modules.KeystrokesModule;
import com.qynl.client189.modules.QuantumSuperpositionModule;
import com.qynl.client189.modules.StreamerModeModule;
import com.qynl.client189.modules.TargetInfoModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HUD renderer for 1.8.9 — mirrors the 1.21.1 client's clean, single-panel
 * design.
 *
 * <p>Top-left: the title and one soft panel holding every enabled module,
 * each row clickable (hover highlights the row, left-click toggles,
 * right-click opens the module detail screen). Right side: one soft panel
 * per info section (InfoHUD, TargetInfo, EffectTimers, DeathCoords,
 * CoordConvert) with an accent edge instead of per-line boxes. Bottom-left:
 * keystrokes (WASD + space + mouse CPS). Everything is hidden while
 * StreamerMode is active so recordings stay OBS-safe.</p>
 *
 * <p>Uses only stable 1.8.9 Yarn APIs: {@link DrawableHelper#fill},
 * {@link TextRenderer#draw} and {@link TextRenderer#getStringWidth}.</p>
 */
public final class HudRenderer189 {

    public static final int PANEL = 0x40000000;
    public static final int PANEL_HOVER = 0x14FFFFFF;
    public static final int ACCENT = 0xFF6EE7A0;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int GRAY = 0xFF9CA3AF;
    public static final int RED = 0xFFFF6B6B;
    public static final int RED_BG = 0xB33D0000;

    private static final List<int[]> moduleRects = new ArrayList<>();
    private static final List<Module> moduleRowModules = new ArrayList<>();
    private static boolean lastLeftDown = false;
    private static boolean lastRightDown = false;

    private HudRenderer189() {}

    public static void render(MinecraftClient client) {
        moduleRects.clear();
        moduleRowModules.clear();

        ModuleManager modules = QynlClient189.getInstance().getModuleManager();

        boolean hideList = StreamerModeModule.shouldHide("all");
        boolean hideHud = StreamerModeModule.shouldHide("hud");

        if (!hideList) {
            renderTitle(client);
            renderModuleList(client, modules);
        }
        if (!hideHud) {
            renderRightColumn(client, modules);
            renderKeystrokes(client, modules);
            renderDurabilityWarn(client, modules);
        }
    }

    private static void renderTitle(MinecraftClient client) {
        TextRenderer font = client.textRenderer;
        String title = "QynlClient";
        font.draw(title, 4, 4, ACCENT);
        font.draw("v" + QynlClient189.VERSION, 4 + font.getStringWidth(title) + 6, 6, GRAY);
    }

    private static int renderRightColumn(MinecraftClient client, ModuleManager modules) {
        int y = 18;
        int rightX = client.width - 6;

        InfoHudModule info = (InfoHudModule) modules.find("InfoHUD");
        if (info != null && info.isEnabled()) {
            List<String> lines = new ArrayList<>();
            String[] candidates = {
                    info.fps(client), info.coords(client), info.direction(client),
                    info.biome(client), info.time(client), info.ping(client)
            };
            for (String line : candidates) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            y = drawRightSection(client, lines, WHITE, rightX, y) + 5;
        }

        TargetInfoModule target = (TargetInfoModule) modules.find("TargetInfo");
        if (target != null && target.isEnabled()) {
            String line = target.getInfo(client);
            if (!line.isEmpty()) {
                y = drawRightSection(client, Collections.singletonList(line), ACCENT, rightX, y) + 5;
            }
        }

        QuantumSuperpositionModule quantum = (QuantumSuperpositionModule) modules.find("QuantumSuperposition");
        if (quantum != null && quantum.isEnabled() && quantum.isOverlayOn()) {
            List<String> qLines = quantum.getHudLines();
            if (!qLines.isEmpty()) {
                List<Integer> qColors = new ArrayList<>();
                qColors.add(ACCENT);
                for (int i = 1; i < qLines.size(); i++) {
                    boolean spikeLine = quantum.isSpiking() && i == qLines.size() - 1;
                    qColors.add(spikeLine ? RED : GRAY);
                }
                y = drawRightSection(client, qLines, qColors, rightX, y) + 5;
            }
        }

        BacktrackModule backtrack = (BacktrackModule) modules.find("Backtrack");
        if (backtrack != null && backtrack.isEnabled()) {
            y = drawRightSection(client, backtrack.getHudLines(), GRAY, rightX, y) + 5;
        }

        BlinkModule blink = (BlinkModule) modules.find("Blink");
        if (blink != null && blink.isEnabled()) {
            y = drawRightSection(client, blink.getHudLines(), GRAY, rightX, y) + 5;
        }

        EffectTimersModule effects = (EffectTimersModule) modules.find("EffectTimers");
        if (effects != null && effects.isEnabled()) {
            List<String> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            for (StatusEffectInstance effect : effects.getEffects(client)) {
                String name = I18n.translate(effect.getTranslationKey(), new Object[0]);
                String line = name + roman(effect.getAmplifier()) + "  " + formatTime(effect.getDuration());
                boolean shortTime = effect.getDuration() > 0 && effect.getDuration() / 20 <= 5;
                lines.add(line);
                colors.add(shortTime ? RED : WHITE);
            }
            y = drawRightSection(client, lines, colors, rightX, y) + 5;
        }

        DeathCoordsModule death = (DeathCoordsModule) modules.find("DeathCoords");
        if (death != null && death.isEnabled()) {
            String line = death.getHudLine(client);
            if (!line.isEmpty()) {
                y = drawRightSection(client, Collections.singletonList(line), RED, rightX, y) + 5;
            }
        }

        CoordConvertModule convert = (CoordConvertModule) modules.find("CoordConvert");
        if (convert != null && convert.isEnabled()) {
            String line = convert.getInfo(client);
            if (!line.isEmpty()) {
                y = drawRightSection(client, Collections.singletonList(line), GRAY, rightX, y) + 5;
            }
        }
        return y;
    }

    /** Draws one soft panel with right-aligned lines and a left accent edge. */
    private static int drawRightSection(MinecraftClient client, List<String> lines, int color, int rightX, int y) {
        if (lines.isEmpty()) {
            return y;
        }
        return drawRightSection(client, lines, Collections.nCopies(lines.size(), color), rightX, y);
    }

    private static int drawRightSection(MinecraftClient client, List<String> lines, List<Integer> colors, int rightX, int y) {
        if (lines.isEmpty()) {
            return y;
        }
        TextRenderer font = client.textRenderer;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.getStringWidth(line));
        }
        int pad = 7;
        int lineH = 11;
        int panelW = maxWidth + pad * 2;
        int panelH = lines.size() * lineH + 4;
        int panelX = rightX - panelW;
        DrawableHelper.fill(panelX, y, rightX + 2, y + panelH, PANEL);
        DrawableHelper.fill(panelX, y, panelX + 2, y + panelH, ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            font.draw(lines.get(i), panelX + pad, y + 3 + i * lineH, colors.get(i));
        }
        return y + panelH;
    }

    private static void renderModuleList(MinecraftClient client, ModuleManager modules) {
        TextRenderer font = client.textRenderer;

        List<Module> enabled = new ArrayList<>();
        for (Module module : modules.getModules()) {
            if (module.isEnabled() && !"ClickGUI".equals(module.getName())) {
                enabled.add(module);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }

        int maxWidth = 0;
        for (Module module : enabled) {
            String label = module.getName() + modeSuffix(module);
            String key = module.getKeyLabel();
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

        DrawableHelper.fill(x, y, x + panelW, y + panelH, PANEL);
        DrawableHelper.fill(x, y, x + 2, y + panelH, ACCENT);

        int mouseX = mouseX(client);
        int mouseY = mouseY(client);

        int rowY = y + 2;
        for (Module module : enabled) {
            if (mouseX >= x && mouseX < x + panelW && mouseY >= rowY - 1 && mouseY < rowY + rowHeight - 1) {
                DrawableHelper.fill(x, rowY - 1, x + panelW, rowY + rowHeight - 1, PANEL_HOVER);
            }

            int nameX = x + padX + 2;
            font.draw(module.getName(), nameX, rowY, WHITE);

            int cursorX = nameX + font.getStringWidth(module.getName());
            String modeText = modeSuffix(module);
            if (!modeText.isEmpty()) {
                font.draw(modeText, cursorX, rowY, ACCENT);
            }

            String key = module.getKeyLabel();
            if (key != null && !key.isEmpty() && !"None".equals(key)) {
                int keyX = x + panelW - padX - font.getStringWidth(key);
                font.draw(key, keyX, rowY, GRAY);
            }

            moduleRects.add(new int[]{x, rowY - 1, x + panelW, rowY + rowHeight - 1});
            moduleRowModules.add(module);
            rowY += rowHeight;
        }
    }

    /** Shows the active mode of assist modules, e.g. " · Silent" or " · Auto". */
    private static String modeSuffix(Module m) {
        Setting<?> mode = m.getSetting("mode");
        if (mode == null) {
            mode = m.getSetting("version"); // e.g. VersionAssist's target version
        }
        if (mode == null) {
            return "";
        }
        String v = String.valueOf(mode.getValue());
        if (v.isEmpty() || "Default".equals(v)) {
            return "";
        }
        return " \u00b7 " + v;
    }

    private static void renderKeystrokes(MinecraftClient client, ModuleManager modules) {
        KeystrokesModule keystrokes = (KeystrokesModule) modules.find("Keystrokes");
        if (keystrokes == null || !keystrokes.isEnabled()) {
            return;
        }

        int size = 22;
        int gap = 2;
        int startX = 6;
        int startY = client.height - 6 - size * 2 - gap - size / 2;

        // WASD
        drawKey(client, keystrokes, KeystrokesModule.KEY_W, startX + size + gap, startY, size, size, "W");
        int rowY = startY + size + gap;
        drawKey(client, keystrokes, KeystrokesModule.KEY_A, startX, rowY, size, size, "A");
        drawKey(client, keystrokes, KeystrokesModule.KEY_S, startX + size + gap, rowY, size, size, "S");
        drawKey(client, keystrokes, KeystrokesModule.KEY_D, startX + (size + gap) * 2, rowY, size, size, "D");
        // Space bar
        int spaceY = rowY + size + gap;
        drawKey(client, keystrokes, KeystrokesModule.KEY_SPACE, startX, spaceY, size * 3 + gap * 2, size / 2, "");

        // Mouse + CPS
        int mouseX = startX + (size + gap) * 3 + 10;
        drawKey(client, keystrokes, KeystrokesModule.MOUSE_L, mouseX, rowY, 34, size,
                "L " + keystrokes.getCps(client, KeystrokesModule.MOUSE_L));
        drawKey(client, keystrokes, KeystrokesModule.MOUSE_R, mouseX + 36, rowY, 34, size,
                "R " + keystrokes.getCps(client, KeystrokesModule.MOUSE_R));
    }

    private static void drawKey(MinecraftClient client, KeystrokesModule keystrokes,
                                int key, int x, int y, int width, int height, String label) {
        TextRenderer font = client.textRenderer;
        boolean down = keystrokes.isKeyDown(client, key);
        int bg = down ? ACCENT : PANEL;
        DrawableHelper.fill(x, y, x + width, y + height, bg);
        if (down) {
            DrawableHelper.fill(x, y, x + width, y + 1, 0xFF2E7D4F);
        }
        int color = down ? 0xFF0B1B12 : WHITE;
        int textWidth = font.getStringWidth(label);
        font.draw(label,
                x + (width - textWidth) / 2,
                y + (height - 9) / 2,
                color);
    }

    private static void renderDurabilityWarn(MinecraftClient client, ModuleManager modules) {
        DurabilityWarnModule warn = (DurabilityWarnModule) modules.find("DurabilityWarn");
        if (warn == null || !warn.isEnabled() || !warn.isWarning(client)) {
            return;
        }
        String text = "! LOW TOOL DURABILITY";
        int width = client.textRenderer.getStringWidth(text);
        int x = (client.width - width) / 2;
        int y = 24;
        DrawableHelper.fill(x - 6, y - 2, x + width + 6, y + 10, RED_BG);
        client.textRenderer.draw(text, x, y, RED);
    }

    /**
     * Poll-based click detection, called from the HUD mixin each frame.
     * Left-click on an enabled module row toggles it; right-click opens its
     * detail screen (settings + keybind).
     */
    public static void handleClick(MinecraftClient client) {
        if (client.currentScreen != null || client.player == null) {
            lastLeftDown = false;
            lastRightDown = false;
            return;
        }
        int mouseX = mouseX(client);
        int mouseY = mouseY(client);

        boolean left = Mouse.isButtonDown(0);
        if (left && !lastLeftDown) {
            for (int i = 0; i < moduleRects.size(); i++) {
                int[] rect = moduleRects.get(i);
                if (mouseX >= rect[0] && mouseX <= rect[2] && mouseY >= rect[1] && mouseY <= rect[3]) {
                    moduleRowModules.get(i).toggle();
                    QynlClient189.getInstance().getModuleManager().saveToConfig();
                    break;
                }
            }
        }
        lastLeftDown = left;

        boolean right = Mouse.isButtonDown(1);
        if (right && !lastRightDown) {
            for (int i = 0; i < moduleRects.size(); i++) {
                int[] rect = moduleRects.get(i);
                if (mouseX >= rect[0] && mouseX <= rect[2] && mouseY >= rect[1] && mouseY <= rect[3]) {
                    MinecraftClient.getInstance().openScreen(new ModuleDetailScreen189(moduleRowModules.get(i)));
                    break;
                }
            }
        }
        lastRightDown = right;
    }

    /** Scaled mouse X (GUI pixels, from the left). */
    private static int mouseX(MinecraftClient client) {
        return (int) (Mouse.getX() / scaleFactor(client));
    }

    /** Scaled mouse Y (GUI pixels, from the top). */
    private static int mouseY(MinecraftClient client) {
        return (int) (client.height - Mouse.getY() / scaleFactor(client));
    }

    private static float scaleFactor(MinecraftClient client) {
        return new Window(client).getScaleFactor();
    }

    private static String formatTime(int ticks) {
        if (ticks <= 0) {
            return "0:00";
        }
        int seconds = (ticks + 19) / 20;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int amplifier) {
        switch (amplifier) {
            case 0: return "";
            case 1: return " II";
            case 2: return " III";
            case 3: return " IV";
            default: return " V";
        }
    }
}

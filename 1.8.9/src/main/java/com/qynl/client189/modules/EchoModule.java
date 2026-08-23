package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * ECHO — the soundscape, rendered. Completely different from the combat
 * engines: it never times a click, never touches a packet, never moves a
 * rotation. It only <b>listens</b>.
 *
 * <p>The server broadcasts every sound it knows about — bow shots, pearl
 * throws <i>and pearl landings</i> (the endermen-portal sound), footsteps,
 * potion splashes, chests, explosions — with a world position. ECHO intercepts
 * those packets (read-only, nothing is sent back) and draws each one as a
 * fading marker in 3D, colored by what made it:</p>
 * <ul>
 *   <li><b>red</b> — player activity: bow shots, eating, hurting, splashing</li>
 *   <li><b>magenta</b> — ender-pearl teleports: the landing spot is revealed
 *       the instant a pearl pops, even through walls</li>
 *   <li><b>gold</b> — mobs</li>
 *   <li><b>green</b> — world/utility: chests, doors, note blocks, explosions</li>
 *   <li><b>grey</b> — ambient (digging, liquids, fire)</li>
 * </ul>
 *
 * <p>Because the module sends zero packets, no anti-cheat can ever flag it:
 * the data comes from the server itself, delivered to every client. It turns
 * the whole audible world into a radar.</p>
 */
public class EchoModule extends Module {
    private static final int CAT_PLAYER = 0;
    private static final int CAT_PEARL = 1;
    private static final int CAT_MOB = 2;
    private static final int CAT_UTILITY = 3;
    private static final int CAT_AMBIENT = 4;

    private static final int[] CAT_COLORS = {
            0xFF4A4A, // player — red
            0xFF55FF, // pearl — magenta
            0xFFD700, // mob — gold
            0x4ADE80, // utility — green
            0x9CA3AF  // ambient — grey
    };

    private static EchoModule instance;

    static final class Echo {
        final double x, y, z;
        final long bornMs;
        final int durationMs;
        final int category;
        final float volume;

        Echo(double x, double y, double z, int durationMs, int category, float volume) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.bornMs = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.category = category;
            this.volume = volume;
        }

        float life() {
            long now = System.currentTimeMillis();
            long elapsed = now - bornMs;
            if (elapsed >= durationMs) return 0.0F;
            return 1.0F - (float) elapsed / durationMs;
        }
    }

    private final ArrayDeque<Echo> echoes = new ArrayDeque<>();
    private int tickCounter = 0;

    public EchoModule() {
        super("Echo",
                "Sound ESP — renders every sound the server broadcasts (bow shots, pearl throws AND landings, footsteps, potions, chests) as a fading 3D marker, even through walls. Listens only, sends nothing.",
                Category.RENDER);
        instance = this;
        bindKey(Keyboard.KEY_K);
        addSetting(Setting.options("mode",      "Sounds",     "All",     "All", "Players", "Mobs", "Utility"));
        addSetting(Setting.range("range",       "Range",       64.0,    16,  128,   8, "b"));
        addSetting(Setting.range("duration",    "Duration",     4.0,     1,    8,   1, "s"));
        addSetting(Setting.range("maxSounds",   "Max sounds",  48.0,    16,   96,   8));
        addSetting(Setting.options("tracers",   "Tracers",    "On",     "On",  "Off"));
        addSetting(Setting.options("throughWalls", "Through walls", "Off", "Off", "On"));
    }

    public static EchoModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** Called from the sound-packet mixin (client thread). */
    public static void onSound(String name, double x, double y, double z, float volume) {
        if (instance == null || !instance.isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int category = classify(name);
        if (!instance.categoryEnabled(category)) return;

        double range = instance.getDoubleSetting("range");
        double dx = x - client.player.x;
        double dy = y - client.player.y;
        double dz = z - client.player.z;
        if (dx * dx + dy * dy + dz * dz > range * range) return;

        int duration = Math.max(1, (int) instance.getDoubleSetting("duration")) * 1000;
        int maxSounds = (int) instance.getDoubleSetting("maxSounds");
        instance.echoes.addLast(new Echo(x, y, z, duration, category, volume));
        while (instance.echoes.size() > maxSounds) {
            instance.echoes.pollFirst();
        }
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (++tickCounter % 20 != 0) return;
        Iterator<Echo> it = echoes.iterator();
        while (it.hasNext()) {
            if (it.next().life() <= 0.0F) {
                it.remove();
            }
        }
    }

    /** True when this sound category passes the mode filter. */
    private boolean categoryEnabled(int category) {
        switch (getStringSetting("mode")) {
            case "Players":
                return category == CAT_PLAYER || category == CAT_PEARL;
            case "Mobs":
                return category == CAT_MOB;
            case "Utility":
                return category == CAT_UTILITY;
            default:
                return true; // All
        }
    }

    /** Maps a sound name to a color category. */
    private static int classify(String name) {
        if (name == null) return CAT_AMBIENT;
        if (name.startsWith("mob.endermen.portal")) return CAT_PEARL;
        if (name.startsWith("mob.")) return CAT_MOB;
        if (name.startsWith("game.player.")
                || name.startsWith("random.bow")
                || name.startsWith("random.break")
                || name.startsWith("random.eat")
                || name.startsWith("random.drink")
                || name.startsWith("random.burp")
                || name.startsWith("random.orb")
                || name.startsWith("random.splash")
                || name.startsWith("random.glass")
                || name.startsWith("random.levelup")) {
            return CAT_PLAYER;
        }
        if (name.startsWith("random.chest")
                || name.startsWith("random.door")
                || name.startsWith("random.click")
                || name.startsWith("random.explode")
                || name.startsWith("random.fizz")
                || name.startsWith("random.anvil")
                || name.startsWith("random.wood")
                || name.startsWith("note.")) {
            return CAT_UTILITY;
        }
        return CAT_AMBIENT;
    }

    // ── rendering ────────────────────────────────────────────────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled() || instance.echoes.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        boolean tracers = "On".equals(instance.getStringSetting("tracers"));
        boolean through = "On".equals(instance.getStringSetting("throughWalls"));
        float eyeY = (float) (camY + client.player.getEyeHeight());

        WorldDraw.begin(through);
        for (Echo echo : instance.echoes) {
            float life = echo.life();
            if (life <= 0.0F) continue;
            int color = CAT_COLORS[echo.category];
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            float alpha = life * (0.45F + 0.35F * Math.min(1.0F, echo.volume));

            double ex = echo.x - camX;
            double ey = echo.y - camY;
            double ez = echo.z - camZ;

            // Vertical pole + ground ring mark the exact source position.
            WorldDraw.line(ex, ey, ez, ex, ey + 1.6, ez, r, g, b, alpha);
            drawRing(ex, ey + 0.05, ez, 0.45, r, g, b, alpha * 0.9F);

            if (tracers) {
                WorldDraw.line(0, eyeY - camY, 0, ex, ey + 0.8, ez, r, g, b, alpha * 0.5F);
            }
        }
        WorldDraw.end();
    }

    private static void drawRing(double x, double y, double z, double radius,
                                 float r, float g, float b, float a) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(
                    x + Math.cos(a0) * radius, y, z + Math.sin(a0) * radius,
                    x + Math.cos(a1) * radius, y, z + Math.sin(a1) * radius,
                    r, g, b, a);
        }
    }

    @Override
    public void onDisable() {
        echoes.clear();
    }
}

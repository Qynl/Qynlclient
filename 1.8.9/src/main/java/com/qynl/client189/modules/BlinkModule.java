package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.BlinkMixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Blink (Fake Lag) — briefly holds back your movement packets.
 *
 * <p>While active, outgoing {@link PlayerMoveC2SPacket}s are buffered by
 * {@link BlinkMixin} instead of being sent. The client keeps moving normally,
 * but the server — and every other player — sees you frozen where you were.
 * When the buffer is flushed, the server processes the whole trajectory at
 * once and catches you up in a burst. Opponents who try to combo you hit
 * where you <i>were</i>, which breaks their chain without any visible
 * teleport abuse.</p>
 *
 * <p>Only movement packets are held: attacks, block placement, keep-alives
 * and everything else go straight through, so the connection stays healthy
 * and the module is safe to leave on.</p>
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Auto</b> (default) — holds packets for {@code ticks} (1–5, default
 *       2) and flushes, repeating forever. A constant 1–2 tick fake lag.</li>
 *   <li><b>Hold</b> — holds while enabled, flushes everything on disable
 *       (classic blink). A safety cap auto-flushes so the delay can never
 *       grow into a timeout.</li>
 * </ul>
 */
public class BlinkModule extends Module {
    /** Hard cap on buffered packets (≈5 s of movement) before auto-flush. */
    private static final int MAX_HELD = 100;

    private static BlinkModule instance;
    private final Deque<Packet> held = new ArrayDeque<>();
    private int holdCounter = 0;

    public BlinkModule() {
        super("Blink",
                "Holds your movement packets for a moment so the server sees you frozen — breaks the opponent's combo (fake lag).",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.options("mode", "Mode", "Auto", "Auto", "Hold"));
        addSetting(Setting.range("ticks", "Hold ticks", 2.0, 1, 5, 1));
    }

    // ── static API for the mixin and HUD ──────────────────────────

    public static BlinkModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** True for packets that should be held — movement only. */
    public static boolean shouldHold(Packet packet) {
        return instance != null && instance.isEnabled() && packet instanceof PlayerMoveC2SPacket;
    }

    public static void hold(Packet packet) {
        if (instance != null && instance.held.size() < MAX_HELD) {
            instance.held.addLast(packet);
        }
    }

    public static Packet poll() {
        return instance == null ? null : instance.held.pollFirst();
    }

    public static int buffered() {
        return instance == null ? 0 : instance.held.size();
    }

    // ── module lifecycle ──────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if ("Auto".equals(getStringSetting("mode"))) {
            if (++holdCounter >= (int) getDoubleSetting("ticks")) {
                holdCounter = 0;
                flush(client);
            }
        } else if (held.size() >= MAX_HELD) {
            // Hold-mode safety cap: never let the delay grow into a timeout.
            flush(client);
        }
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        flush(client);
    }

    private void flush(MinecraftClient client) {
        if (client == null) {
            held.clear();
            return;
        }
        BlinkMixin.flush(client.getNetworkHandler());
    }

    /** Live HUD line: mode plus how many packets are buffered. */
    public List<String> getHudLines() {
        List<String> lines = new ArrayList<>();
        int buffered = held.size();
        lines.add("Blink \u00b7 " + getStringSetting("mode") + (buffered > 0 ? " \u00b7 buf " + buffered : ""));
        return lines;
    }
}

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
import java.util.Random;

/**
 * Blink (Fake Lag) — briefly holds back your movement packets.
 *
 * <p>While a hold cycle is active, outgoing {@link PlayerMoveC2SPacket}s are
 * buffered by {@link BlinkMixin} instead of being sent. The client keeps
 * moving normally, but the server — and every other player — sees you frozen
 * where you were. When the buffer is flushed, the server processes the whole
 * trajectory at once and catches you up in a burst. Opponents who try to
 * combo you hit where you <i>were</i>, which breaks their chain.</p>
 *
 * <p>Anti-cheat hardened: holds only happen <b>on the ground while moving</b>
 * (a frozen position mid-air or while idle is the strongest position-desync
 * signature there is), hold length is randomized, and Auto mode is a
 * <b>burst</b> pattern — a short hold followed by a 12–24 tick gap — instead
 * of constant fake lag, which statistical ACs flag instantly.</p>
 *
 * <p>Only movement packets are held: attacks, block placement, keep-alives
 * and everything else go straight through.</p>
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Auto</b> (default) — burst fake lag: random 1–4 tick hold, then a
 *       gap, repeating. Safe enough to leave on.</li>
 *   <li><b>Hold</b> — holds while enabled, flushes everything on disable
 *       (classic blink). A safety cap auto-flushes so the delay can never
 *       grow into a timeout.</li>
 * </ul>
 */
public class BlinkModule extends Module {
    /** Hard cap on buffered packets (≈5 s of movement) before auto-flush. */
    private static final int MAX_HELD = 100;
    private static final Random RANDOM = new Random();

    private static BlinkModule instance;
    private final Deque<Packet> held = new ArrayDeque<>();
    private int holdCounter = 0;
    private int cooldownTicks = 0;
    private boolean holding = false;

    public BlinkModule() {
        super("Blink",
                "Holds your movement packets for a moment so the server sees you frozen — breaks the opponent's combo (fake lag).",
                Category.UTILITY);
        instance = this;
        bindKey(Keyboard.KEY_N);
        addSetting(Setting.options("mode", "Mode", "Auto", "Auto", "Hold"));
        addSetting(Setting.range("ticks", "Hold ticks", 2.0, 1, 5, 1));
    }

    // ── static API for the mixin and HUD ──────────────────────────

    public static BlinkModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** True for packets that should be held — movement only, on ground, moving. */
    public static boolean shouldHold(Packet packet) {
        if (instance == null || !instance.isEnabled() || !instance.holding) return false;
        if (!(packet instanceof PlayerMoveC2SPacket)) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return false;
        // Never desync mid-air and never while idle — no gain, pure flag risk.
        if (!client.player.onGround) return false;
        if (client.player.input.movementForward == 0.0F && client.player.input.movementSideways == 0.0F) {
            return false;
        }
        return true;
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

    /** Central world-change/death hook: never release movement packets that
     *  belong to a previous life/world. */
    @Override
    public void onWorldChange(MinecraftClient client) {
        holding = false;
        flush(client);
    }

    @Override
    public void onTick(MinecraftClient client) {
        // Death/leave is handled centrally by onWorldChange; this guard only
        // keeps the hold from resuming while dead.
        if (client.player == null || !client.player.isAlive()) {
            holding = false;
            flush(client);
            return;
        }

        boolean holdMode = "Hold".equals(getStringSetting("mode"));
        if (holdMode) {
            holding = true;
            if (held.size() >= MAX_HELD) {
                flush(client);
            }
            return;
        }

        // Auto: burst fake lag — hold, flush, then a long randomized gap so
        // the holds never form a constant-rate pattern.
        if (holding) {
            int holdTarget = Math.max(1, (int) getDoubleSetting("ticks") + RANDOM.nextInt(2));
            if (++holdCounter >= holdTarget) {
                holding = false;
                holdCounter = 0;
                flush(client);
                cooldownTicks = 12 + RANDOM.nextInt(13); // 12–24 tick gap
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        } else {
            holding = true;
            holdCounter = 0;
        }
    }

    @Override
    public void onDisable() {
        holding = false;
        cooldownTicks = 0;
        MinecraftClient client = MinecraftClient.getInstance();
        flush(client);
    }

    private void flush(MinecraftClient client) {
        // No handler (disconnecting): drop the buffer instead of keeping
        // stale coordinates that would flush into the next world.
        if (client == null || client.getNetworkHandler() == null) {
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

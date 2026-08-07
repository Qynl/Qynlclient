package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerAbilities;

import org.lwjgl.input.Keyboard;

/**
 * FlyAssist — smooth flight for 1.8.9, with a "silent" mode that works
 * without fly permission and without looking like flight to simple servers.
 *
 * <p>Modes:</p>
 * <ul>
 *   <li><b>Silent</b> (default) — flies with velocity only: no abilities
 *       packet, no "flying" state. Climbing is capped at jump speed and the
 *       movement packets are spoofed as {@code onGround} (see
 *       {@link com.qynl.client189.mixin.ClientPlayerSendMovementMixin}), so
 *       servers see the pattern of a normal jumping/walking player rather
 *       than sustained flight. Best-effort — aggressive anti-cheat may still
 *       catch it.</li>
 *   <li><b>Smooth</b> — same physics, no packet spoofing. Good on servers
 *       with flight allowed (creative, allow-flight) where you just want
 *       buttery, velocity-based movement instead of vanilla's abrupt one.</li>
 *   <li><b>Vanilla</b> — toggles real creative flight (abilities packet).
 *       Only useful where the server grants flight (creative/spectator or
 *       allow-flight); the other two modes need no permission at all.</li>
 * </ul>
 *
 * <p>Movement is computed every tick at the start of the game tick (the
 * client's tick mixin runs before the player's movement update), using
 * vanilla fly-style input math: horizontal input is converted relative to
 * the camera yaw, smoothed with acceleration instead of instant speed, and
 * the Y axis is driven by jump/sneak with auto-hover (no constant falling).
 * Speed and vertical strength are adjustable.</p>
 */
public class FlyAssistModule extends Module {
    private static FlyAssistModule instance;

    /** True while Vanilla mode is forcing the player's abilities. */
    private boolean touchedAbilities;

    public FlyAssistModule() {
        super("FlyAssist",
                "Smooth flight without fly permission — Silent mode spoofs grounded "
                        + "packets so servers see normal movement.",
                Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_K);
        addSetting(Setting.options("mode",     "Mode",        "Silent", "Silent", "Smooth", "Vanilla"));
        addSetting(Setting.range("speed",      "Speed",       1.0, 0.5, 3.0, 0.1, "x"));
        addSetting(Setting.range("vertical",   "Vertical",    1.0, 0.4, 2.5, 0.1, "x"));
        addSetting(Setting.options("hover",    "Auto-hover",  "On",  "On",  "Off"));
        addSetting(Setting.options("spoof",    "Spoof ground","On",  "On",  "Off"));
    }

    public static FlyAssistModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /** True while movement packets should be spoofed as grounded (Silent fly). */
    public static boolean shouldSpoofGround() {
        if (instance == null || !instance.isEnabled()) return false;
        if ("Vanilla".equals(instance.getStringSetting("mode"))) return false;
        return "On".equals(instance.getStringSetting("spoof"));
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && touchedAbilities) {
            try {
                // Stop forcing vanilla fly; allowFlying is left alone because
                // the server owns that flag (creative players keep it).
                client.player.abilities.flying = false;
            } catch (Throwable ignored) {
            }
        }
        touchedAbilities = false;
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (client.currentScreen != null || !client.player.isAlive()) return;

        if ("Vanilla".equals(getStringSetting("mode"))) {
            tickVanilla(client);
        } else {
            tickVelocity(client);
        }
    }

    // ── Vanilla mode: real (permission-gated) creative flight ──────────

    private void tickVanilla(MinecraftClient client) {
        PlayerAbilities abilities = client.player.abilities;
        boolean changed = false;
        if (!abilities.allowFlying) {
            abilities.allowFlying = true;
            touchedAbilities = true;
            changed = true;
        }
        if (!abilities.flying) {
            abilities.flying = true;
            touchedAbilities = true;
            changed = true;
        }
        // In 1.8.9 the server reads flying state from movement packets, so
        // no dedicated abilities packet is needed — just set client-side flags.
    }

    // ── Silent / Smooth mode: velocity flight, no permission needed ────

    private void tickVelocity(MinecraftClient client) {
        boolean silent = "Silent".equals(getStringSetting("mode"));
        double speed = getDoubleSetting("speed");
        double vertical = getDoubleSetting("vertical");
        boolean hover = "On".equals(getStringSetting("hover"));

        // Input: 1 / 0 / -1 per axis.
        double forward = (client.options.keyForward.isPressed() ? 1 : 0)
                - (client.options.keyBack.isPressed() ? 1 : 0);
        double strafe = (client.options.keyRight.isPressed() ? 1 : 0)
                - (client.options.keyLeft.isPressed() ? 1 : 0);

        // Normalize diagonals so S+A moves at full speed, not √2× it.
        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len > 0) {
            forward /= len;
            strafe /= len;
        }

        // Vanilla fly-style world direction from input + camera yaw
        // (0° faces +Z; verified: W at yaw 0 → +Z, D → +X).
        double yawRad = Math.toRadians(client.player.yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetSpeed = 0.54 * speed; // ≈ creative fly speed in blocks/tick
        double targetX = (-forward * sin + strafe * cos) * targetSpeed;
        double targetZ = (forward * cos + strafe * sin) * targetSpeed;

        // Smooth acceleration toward the target — feels like creative fly
        // instead of teleport-y input snaps.
        final double accel = 0.15;
        client.player.velocityX += (targetX - client.player.velocityX) * accel;
        client.player.velocityZ += (targetZ - client.player.velocityZ) * accel;

        // Vertical: jump = up, sneak = down, else hover in place.
        boolean up = client.options.keyJump.isPressed();
        boolean down = client.options.keySneak.isPressed();
        double vSpeed = 0.12 * vertical;

        if (silent && up) {
            // Climb at most at jump speed so the server reads a jump arc,
            // never sustained flight.
            client.player.velocityY = Math.min(0.30, client.player.velocityY + 0.10);
        } else if (up) {
            client.player.velocityY += vSpeed;
            if (client.player.velocityY > 0.40) client.player.velocityY = 0.40;
        } else if (down) {
            client.player.velocityY = -vSpeed;
        } else if (hover) {
            // Cancel gravity smoothly so the player hangs instead of falling.
            client.player.velocityY += (0.0 - client.player.velocityY) * 0.35;
        }
    }
}

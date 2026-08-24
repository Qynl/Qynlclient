package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * VelocityAssist for 1.8.9 — softens knockback with natural variance.
 *
 * <p>Two-layer defense:</p>
 * <ol>
 *   <li>The {@code VelocityMixin} intercepts knockback packets from the
 *       server and reduces them before they reach the player.</li>
 *   <li>As a fallback, {@link #onTick(MinecraftClient)} reduces any
 *       remaining velocity every tick — this catches cases where the
 *       mixin didn't fire (different Yarn mappings, etc.).</li>
 * </ol>
 */
public class VelocityModule extends Module {
    private static VelocityModule instance;
    private static final Random RANDOM = new Random();

    /** Timestamp (millis) when VelocityMixin last dampened a hit. Lets the
     *  per-tick fallback know the mixin already handled this knockback window,
     *  so the configured reduction is never applied twice. */
    private static long lastDampenedAtMs = -1;

    /** Per-knockback factor captured on the rising edge of hurtTime, so the
     *  fallback applies ONE reduction across the whole hurt window instead of
     *  re-rolling a fresh random factor every tick (which over-reduced and
     *  made the effective dampening drift from the configured value). */
    private double fallbackHKeep = 1.0;
    private double fallbackVKeep = 1.0;
    private int fallbackHurtTicks = 0;

    public VelocityModule() {
        super("Velocity", "Reduces knockback naturally — varies slightly each hit to feel legit.", Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.range("horizontal",  "Horizontal %", 45.0, 0, 90, 5, "%"));
        addSetting(Setting.range("vertical",    "Vertical %",   20.0, 0, 90, 5, "%"));
        addSetting(Setting.range("variance",    "Variance",     10.0, 0, 25, 5, "%"));
        addSetting(Setting.range("chance",      "Chance",       75.0, 0, 100, 5, "%"));
    }

    public static VelocityModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    /**
     * Returns horizontal reduction multiplier (0.0 ~ 1.0).
     * Applied per-hit so variance changes each time — looks like natural lag/ping jitter.
     */
    public double horizontalFactor() {
        double base = getDoubleSetting("horizontal") / 100.0;
        double var = getDoubleSetting("variance") / 100.0;
        double factor = base + (RANDOM.nextDouble() - 0.5) * var * 2.0;
        return Math.max(0.0, Math.min(0.95, 1.0 - factor));
    }

    public double verticalFactor() {
        double base = getDoubleSetting("vertical") / 100.0;
        double var = getDoubleSetting("variance") / 100.0;
        double factor = base + (RANDOM.nextDouble() - 0.5) * var * 2.0;
        return Math.max(0.0, Math.min(0.95, 1.0 - factor));
    }

    /**
     * True if this knockback hit should be dampened at all. Real lag hits
     * randomly: some hits take full knockback, which breaks the "every hit
     * reduced" pattern statistical AC checks look for.
     */
    public static boolean rollChance() {
        return instance == null || (RANDOM.nextDouble() * 100.0) < instance.getDoubleSetting("chance");
    }

    /** Called by VelocityMixin right after it dampens a knockback hit. */
    public static void markMixinDampened() {
        lastDampenedAtMs = System.currentTimeMillis();
    }

    /** True while the mixin's dampening of the current hit is still in effect
     *  (the hurtTime knockback window is 10 ticks = 500 ms). */
    private static boolean mixinDampenedRecently(MinecraftClient client) {
        return lastDampenedAtMs >= 0 && System.currentTimeMillis() - lastDampenedAtMs <= 600;
    }

    // ── per-tick fallback: catches knockback the mixin missed ────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        // Only dampen while the player is in the post-hit knockback window
        // (hurtTime counts down from 10 right after being hit). Without this
        // gate the fallback would also slow normal walking and jumping,
        // because movement input produces velocity every tick.
        if (client.player.hurtTime <= 0) {
            fallbackHurtTicks = 0;
            return;
        }

        // If VelocityMixin already dampened this hit, skip — otherwise the
        // configured reduction would be applied a second time on top of it.
        if (mixinDampenedRecently(client)) {
            fallbackHurtTicks = 0;
            return;
        }

        // Rising edge of this knockback: capture the reduction once (with
        // variance), then spread it over the remaining hurt window via the
        // nth-root — the hit reaches the configured keep % exactly, instead
        // of stacking a fresh random factor on every tick.
        if (fallbackHurtTicks == 0) {
            fallbackHurtTicks = Math.max(1, client.player.hurtTime);
            fallbackHKeep = horizontalFactor(); // already 1.0 - reduction%
            fallbackVKeep = verticalFactor();
        }

        // Per-tick multiplier: nth-root so over the hurt window it reaches
        // the captured target exactly.
        double hPerTick = Math.pow(fallbackHKeep, 1.0 / fallbackHurtTicks);
        double vPerTick = Math.pow(fallbackVKeep, 1.0 / fallbackHurtTicks);
        client.player.velocityX *= hPerTick;
        client.player.velocityZ *= hPerTick;
        client.player.velocityY *= vPerTick;
        fallbackHurtTicks--;
    }
}

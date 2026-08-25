package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * VelocityAssist — softens knockback with natural variance.
 *
 * <p>Two-layer defense:</p>
 * <ol>
 *   <li>The {@code VelocityMixin} intercepts knockback packets from the
 *       server and reduces them before they reach the player.</li>
 *   <li>As a fallback, {@link #onTick(Minecraft)} reduces any remaining
 *       velocity every tick — this catches cases where the mixin didn't
 *       fire.</li>
 * </ol>
 */
public class VelocityAssistModule extends Module {
    private static VelocityAssistModule instance;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Timestamp (millis) when VelocityMixin last dampened a hit. Lets the
     *  per-tick fallback know the mixin already handled this knockback window,
     *  so the configured reduction is never applied twice. */
    private static long lastDampenedAtMs = -1;

    /** Per-knockback factor captured on the rising edge of hurtTime, so the
     *  fallback applies ONE reduction across the whole hurt window instead of
     *  re-rolling a fresh random factor every tick. */
    private double fallbackHKeep = 1.0;
    private double fallbackVKeep = 1.0;
    private int fallbackHurtTicks = 0;

    public VelocityAssistModule() {
        super("VelocityAssist",
                "Reduces knockback naturally — varies slightly each hit to feel legit.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_H);
        addSetting(Setting.range("horizontal",  "Horizontal reduce", 45.0, 0, 90, 5, "%"));
        addSetting(Setting.range("vertical",    "Vertical reduce",   20.0, 0, 90, 5, "%"));
        addSetting(Setting.range("variance",    "Variance",          10.0, 0, 25, 5, "%"));
        addSetting(Setting.range("chance",      "Chance",            75.0, 0, 100, 5, "%"));
    }

    public static VelocityAssistModule getInstance() { return instance; }
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
    private static boolean mixinDampenedRecently(Minecraft client) {
        return lastDampenedAtMs >= 0 && System.currentTimeMillis() - lastDampenedAtMs <= 600;
    }

    // ── per-tick fallback: catches knockback the mixin missed ────

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;

        // Only dampen while the player is in the post-hit knockback window.
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

        // Per-hit chance: some knockback windows are left untouched.
        if (fallbackHurtTicks == 0 && !rollChance()) {
            return;
        }

        // Rising edge of this knockback: capture the reduction once (with
        // variance), then spread it over the remaining hurt window via the
        // nth-root — the hit reaches the configured keep % exactly.
        if (fallbackHurtTicks == 0) {
            fallbackHurtTicks = Math.max(1, client.player.hurtTime);
            fallbackHKeep = horizontalFactor();
            fallbackVKeep = verticalFactor();
        }

        double hPerTick = Math.pow(fallbackHKeep, 1.0 / fallbackHurtTicks);
        double vPerTick = Math.pow(fallbackVKeep, 1.0 / fallbackHurtTicks);
        client.player.setDeltaMovement(
                client.player.getDeltaMovement().x * hPerTick,
                client.player.getDeltaMovement().y * vPerTick,
                client.player.getDeltaMovement().z * hPerTick);
        fallbackHurtTicks--;
    }
}

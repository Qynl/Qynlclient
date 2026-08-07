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
public class VelocityAssistModule extends Module {
    private static VelocityAssistModule instance;
    private static final Random RANDOM = new Random();

    public VelocityAssistModule() {
        super("VelocityAssist", "Reduces knockback naturally — varies slightly each hit to feel legit.", Category.ASSIST);
        instance = this;
        bindKey(Keyboard.KEY_H);
        addSetting(Setting.range("horizontal",  "Horizontal %", 60.0, 0, 90, 5, "%"));
        addSetting(Setting.range("vertical",    "Vertical %",   30.0, 0, 90, 5, "%"));
        addSetting(Setting.range("variance",    "Variance",     10.0, 0, 25, 5, "%"));
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

    // ── per-tick fallback: reduces any existing velocity ─────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        // Every tick, apply a gentle ongoing reduction to the player's velocity.
        // This catches knockback that the mixin missed (different Yarn builds, etc.)
        // The per-tick multiplier is small since velocity decays naturally anyway;
        // we add an extra ~10-40% reduction per tick on top of natural decay.
        double hKeep = horizontalFactor(); // already 1.0 - reduction%
        double vKeep = verticalFactor();

        // Only apply if there's actually velocity to reduce
        boolean hasMotion = Math.abs(client.player.velocityX) > 0.001
                || Math.abs(client.player.velocityY) > 0.001
                || Math.abs(client.player.velocityZ) > 0.001;

        if (hasMotion) {
            // Per-tick multiplier: nth-root so over ~5 ticks it reaches the target
            double hPerTick = Math.pow(hKeep, 1.0 / 5.0);
            double vPerTick = Math.pow(vKeep, 1.0 / 5.0);
            client.player.velocityX *= hPerTick;
            client.player.velocityZ *= hPerTick;
            client.player.velocityY *= vPerTick;
        }
    }
}

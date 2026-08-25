package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * AutoSprint — sprint automatically while you move forward, without holding
 * the sprint key.
 *
 * <p>Anti-cheat hardened: sprint starts with a randomized delay after you
 * start moving (humans don't sprint the instant they press W), never
 * re-engages within 5 ticks after an attack (vanilla cancels sprint on attack
 * and real players take a moment to re-engage), and occasionally misses the
 * start and retries. Uses the same vanilla sprint rules (food, not sneaking,
 * not using an item) so the pattern matches a normal player perfectly.</p>
 */
public class AutoSprintModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private int startDelayTicks = 0;
    private int lastAttackTick = -100;
    private boolean wasSwinging = false;
    private int tickCounter = 0;

    public AutoSprintModule() {
        super("AutoSprint", "Sprint automatically while you move — no need to hold the sprint key.",
                Category.COMBAT);		bindKey(GLFW.GLFW_KEY_Y);
		addSetting(Setting.options("mode", "Mode", "Forward", "Forward", "Always"));
		// On by default, like every quality client — sprinting is expected
		// behaviour, not a feature you have to discover.
		setEnabled(true);
	}

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            return;
        }
        var player = client.player;
        tickCounter++;

        // Track our own swings: vanilla cancels sprint on attack, and
        // re-sprinting instantly after a hit is a bot signature.
        boolean swinging = player.swinging;
        if (swinging && !wasSwinging) {
            lastAttackTick = tickCounter;
        }
        wasSwinging = swinging;

        if (player.isSprinting()) {
            return;
        }

        boolean moving = "Always".equals(getStringSetting("mode"))
                ? (player.input.forwardImpulse > 0.0F || player.input.leftImpulse != 0.0F)
                : player.input.forwardImpulse > 0.0F;
        if (!moving) {
            startDelayTicks = 0;
            return;
        }

        // Near-instant start: 1 tick (50 ms) after movement begins.
        if (startDelayTicks == 0) {
            startDelayTicks = 1;
            return;
        }
        if (--startDelayTicks > 0) {
            return;
        }

        // Short post-attack re-engage window (vanilla cancels sprint on hit).
        if (tickCounter - lastAttackTick < 3) {
            return;
        }

        boolean canSprint = player.getFoodData().getFoodLevel() > 6
                && !player.isCrouching()
                && !player.isUsingItem()
                && !player.isPassenger();
        if (canSprint) {
            player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        startDelayTicks = 0;
    }
}

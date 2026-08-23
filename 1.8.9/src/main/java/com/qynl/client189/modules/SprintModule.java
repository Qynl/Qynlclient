package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * Sprint — sprint automatically while you move forward, without holding the
 * sprint key.
 *
 * <p>Anti-cheat hardened: sprint starts with a randomized delay after you
 * start moving (humans don't sprint the instant they press W), never re-engages
 * within 5 ticks after an attack (vanilla cancels sprint on attack and real
 * players take a moment to re-engage), and occasionally misses the start and
 * retries. Uses the same vanilla sprint rules (food, not sneaking, not using
 * an item) so the pattern matches a normal player perfectly.</p>
 */
public class SprintModule extends Module {
    private static final Random RANDOM = new Random();

    private int startDelayTicks = 0;
    private int lastAttackTick = -100;
    private boolean wasSwinging = false;
    private int tickCounter = 0;

    public SprintModule() {
        super("Sprint", "Sprint automatically while you move — no need to hold the sprint key.",
                Category.COMBAT);
        bindKey(Keyboard.KEY_R);
        addSetting(Setting.options("mode", "Mode", "Forward", "Forward", "Always"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        ClientPlayerEntity player = client.player;
        tickCounter++;

        // Track our own swings: vanilla cancels sprint on attack, and
        // re-sprinting instantly after a hit is a bot signature.
        boolean swinging = player.handSwinging;
        if (swinging && !wasSwinging) {
            lastAttackTick = tickCounter;
        }
        wasSwinging = swinging;

        if (player.isSprinting()) {
            return;
        }

        boolean moving = "Always".equals(getStringSetting("mode"))
                ? (player.input.movementForward > 0.0F || player.input.movementSideways != 0.0F)
                : player.input.movementForward > 0.0F;
        if (!moving) {
            startDelayTicks = 0;
            return;
        }

        // Randomized start delay after movement begins (3–7 ticks).
        if (startDelayTicks == 0) {
            startDelayTicks = 3 + RANDOM.nextInt(5);
            return;
        }
        if (--startDelayTicks > 0) {
            return;
        }

        // Respect the post-attack sprint re-engage window.
        if (tickCounter - lastAttackTick < 5) {
            return;
        }
        // Occasionally miss the sprint start and retry next tick.
        if (RANDOM.nextInt(100) < 10) {
            return;
        }

        boolean canSprint = player.getHungerManager().getFoodLevel() > 6
                && !player.isSneaking()
                && !player.isUsingItem()
                && !player.hasVehicle();
        if (canSprint) {
            player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        startDelayTicks = 0;
    }
}

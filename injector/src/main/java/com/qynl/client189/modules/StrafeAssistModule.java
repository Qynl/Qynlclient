package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.ReflectionAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * StrafeAssist — strafes for you while you fight.
 *
 * <p>In 1.8.9 PvP, strafing (moving left/right while circling an enemy)
 * keeps the opponent off-balance. This module presses the strafe keys for
 * you — left, right, or alternating — while an enemy is nearby (or always,
 * if you choose). Direction switches on an adjustable interval with slight
 * humanized randomness, and it can keep sprint active while strafing
 * (1.8.9 otherwise cancels sprint the moment you strafe).</p>
 *
 * <p>All keys are released when the module is disabled or the situation
 * ends, so the player never gets stuck strafing. Friends are never
 * considered enemies.</p>
 */
public class StrafeAssistModule extends Module {
    private static final Random RANDOM = new Random();

    private int switchTicksRemaining = 0;
    private boolean strafingLeft = true;
    private boolean keysHeld = false;

    public StrafeAssistModule() {
        super("StrafeAssist",
                "Auto-strafe left/right in combat — keeps you moving while you focus on aiming.",
                Category.COMBAT);
        bindKey(Keyboard.KEY_Y);
        addSetting(Setting.options("mode",     "Mode",      "Combat", "Combat", "Always"));
        addSetting(Setting.options("direction", "Direction", "Alternate", "Alternate", "Left", "Right"));
        addSetting(Setting.range("interval",  "Switch (t)", 20.0,  6,  40,  1, "t"));
        addSetting(Setting.range("range",     "Enemy range", 4.0,  2.0, 8.0, 0.5, "b"));
        addSetting(Setting.options("sprint",  "Keep sprint", "On",  "On",  "Off"));
        addSetting(Setting.options("attackOnly", "While attacking", "Off", "Off", "On"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            releaseKeys(client);
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            releaseKeys(client);
            return;
        }
        // Never strafe while sneaking or using an item — the combined
        // slow/strafing motion is physically odd and reads as bot movement
        // to Intave's movement heuristics.
        if (client.player.isSneaking() || client.player.isUsingItem()) {
            releaseKeys(client);
            return;
        }

        boolean shouldStrafe;
        if ("Always".equals(getStringSetting("mode"))) {
            shouldStrafe = true;
        } else {
            shouldStrafe = findEnemy(client) != null;
        }

        // Optionally only strafe while the player is attacking.
        if ("On".equals(getStringSetting("attackOnly")) && !client.options.keyAttack.isPressed()) {
            shouldStrafe = false;
        }

        if (!shouldStrafe) {
            releaseKeys(client);
            return;
        }

        // Never strafe in place — only while actually moving or attacking.
        boolean anyMovement = client.player.input.movementForward != 0.0F
                || client.player.input.movementSideways != 0.0F;
        if (!anyMovement && !client.options.keyAttack.isPressed()) {
            releaseKeys(client);
            return;
        }

        // Direction switching on a randomized interval (±30%) — a perfect
        // left/right oscillation at a fixed rate is a detectable pattern.
        if (switchTicksRemaining <= 0) {
            double interval = getDoubleSetting("interval");
            switchTicksRemaining = Math.max(4, (int) (interval * (0.7 + RANDOM.nextDouble() * 0.6)));
            String dir = getStringSetting("direction");
            if ("Left".equals(dir)) {
                strafingLeft = true;
            } else if ("Right".equals(dir)) {
                strafingLeft = false;
            } else if (RANDOM.nextInt(100) >= 10) {
                strafingLeft = !strafingLeft; // Alternate, 10% chance to hold
            }
        } else {
            switchTicksRemaining--;
        }

        // Hold the strafe key (+ sprint so 1.8.9 doesn't cancel it).
        ReflectionAccess.keyBindingSetPressed(client.options.keyLeft, strafingLeft);
        ReflectionAccess.keyBindingSetPressed(client.options.keyRight, !strafingLeft);
        if ("On".equals(getStringSetting("sprint"))) {
            ReflectionAccess.keyBindingSetPressed(client.options.keySprint, true);
        }
        keysHeld = true;
    }

    /** Finds any enemy living entity within the configured range. */
    private LivingEntity findEnemy(MinecraftClient client) {
        double maxDist = getDoubleSetting("range");
        double maxDistSq = maxDist * maxDist;

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;

            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                return living;
            }
        }
        return null;
    }

    private void releaseKeys(MinecraftClient client) {
        if (!keysHeld) return;
        if (client.options != null) {
            ReflectionAccess.keyBindingSetPressed(client.options.keyLeft, false);
            ReflectionAccess.keyBindingSetPressed(client.options.keyRight, false);
            ReflectionAccess.keyBindingSetPressed(client.options.keySprint, false);
        }
        keysHeld = false;
        switchTicksRemaining = 0;
        strafingLeft = true;
    }

    @Override
    public void onDisable() {
        releaseKeys(MinecraftClient.getInstance());
    }
}

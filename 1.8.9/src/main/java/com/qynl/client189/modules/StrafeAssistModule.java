package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * StrafeAssist for 1.8.9 — strafes for you while you fight.
 *
 * <p>In 1.8.9 PvP, strafing (moving left/right while circling an enemy)
 * is essential to dodge and keep the opponent off-balance, but pressing
 * A/D while also aiming and clicking is exactly the kind of multitasking
 * that is hard for some players. This module presses the strafe keys for
 * you — left, right, or alternating — while an enemy is nearby (or always,
 * if you choose). Direction switches on an adjustable interval with slight
 * humanized randomness, and it can keep sprint active while strafing
 * (1.8.9 otherwise cancels sprint the moment you strafe).</p>
 *
 * <p>All keys are released when the module is disabled or the situation
 * ends, so the player never gets stuck strafing.</p>
 */
public class StrafeAssistModule extends Module {
    private static final Random RANDOM = new Random();

    private int strafeTimer = 0;
    private int switchInterval = 20;
    private boolean strafingLeft = true;
    private boolean keysHeld = false;

    public StrafeAssistModule() {
        super("StrafeAssist",
                "Auto-strafe left/right in combat — keeps you moving while you focus on aiming.",
                Category.ASSIST);
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

        // Direction switching with slight humanized randomness.
        switchInterval = (int) getDoubleSetting("interval") + RANDOM.nextInt(4) - 1;
        if (switchInterval < 4) switchInterval = 4;

        strafeTimer++;
        if (strafeTimer >= switchInterval) {
            strafeTimer = 0;
            String dir = getStringSetting("direction");
            if ("Left".equals(dir)) {
                strafingLeft = true;
            } else if ("Right".equals(dir)) {
                strafingLeft = false;
            } else {
                strafingLeft = !strafingLeft; // Alternate
            }
        }

        // Hold the strafe key (+ sprint so 1.8.9 doesn't cancel it).
        ((KeyBindingAccessor) client.options.keyLeft).setPressed(strafingLeft);
        ((KeyBindingAccessor) client.options.keyRight).setPressed(!strafingLeft);
        if ("On".equals(getStringSetting("sprint"))) {
            ((KeyBindingAccessor) client.options.keySprint).setPressed(true);
        }
        keysHeld = true;
    }

    /** Finds any enemy living entity within the configured range. */
    private LivingEntity findEnemy(MinecraftClient client) {
        double maxDist = getDoubleSetting("range");
        double maxDistSq = maxDist * maxDist;
        boolean targetPlayers = true; // strafe around players too

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (living instanceof PlayerEntity && !targetPlayers) continue;

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
            ((KeyBindingAccessor) client.options.keyLeft).setPressed(false);
            ((KeyBindingAccessor) client.options.keyRight).setPressed(false);
            ((KeyBindingAccessor) client.options.keySprint).setPressed(false);
        }
        keysHeld = false;
        strafeTimer = 0;
        strafingLeft = true;
    }

    @Override
    public void onDisable() {
        releaseKeys(MinecraftClient.getInstance());
    }
}

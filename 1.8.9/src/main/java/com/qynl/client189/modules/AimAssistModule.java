package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.SilentAim;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

import java.util.Random;

public class AimAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private Entity target;
    private int reactionTicks;
    private int overrideTicks;
    private double lastMouseX, lastMouseY;
    private double aimYawJitter, aimPitchJitter;
    private int jitterTimer;
    private double jitterPhase;
    private int targetLockTicks;

    public AimAssistModule() {
        super("AimAssist", "Smooth, humanized aim assist. Rotation + Silent modes.", Category.ASSIST);
        bindKey(Keyboard.KEY_O);
        addSetting(Setting.options("mode",     "Mode",       "Rotations", "Rotations", "Silent"));
        addSetting(Setting.range("strength",   "Strength",   100.0, 30, 150, 5, "%"));
        addSetting(Setting.range("maxAngle",   "Max angle",   35.0, 10, 90, 5, "\u00b0"));
        addSetting(Setting.range("maxDist",    "Max range",   5.5,  3, 10, 0.5, "b"));
        addSetting(Setting.options("target",   "Targets",    "Monsters", "Monsters", "Players+Monsters"));
        addSetting(Setting.options("priority", "Priority",   "Crosshair", "Crosshair", "Distance", "Health"));
        addSetting(Setting.range("smoothness", "Smoothness",  70.0, 30, 100, 5, "%"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) { SilentAim.clear(); return; }

        Entity newTarget = null;
        if (client.currentScreen == null && client.options.keyAttack.isPressed()
                && !client.player.isSpectator()) {
            newTarget = findTarget(client);
        }

        updateAimState(client, newTarget);

        if (target == null || !isAimReady()) {
            SilentAim.clear();
            return;
        }

        float[] next = stepTowards(client, client.player.yaw, client.player.pitch);
        if (next == null) { SilentAim.clear(); return; }

        if ("Silent".equals(getStringSetting("mode"))) {
            SilentAim.set(next[0], next[1]);
        } else {
            client.player.yaw = next[0];
            client.player.pitch = next[1];
        }
    }

    @Override
    public void onDisable() { SilentAim.clear(); target = null; }

    // ── aim state machine ───────────────────────────────────────
    private void updateAimState(MinecraftClient client, Entity newTarget) {
        double mx = org.lwjgl.input.Mouse.getX(), my = org.lwjgl.input.Mouse.getY();
        double move = Math.hypot(mx - lastMouseX, my - lastMouseY);
        lastMouseX = mx; lastMouseY = my;

        // Player is moving mouse = override aim assist temporarily
        if (move > 5.0) overrideTicks = 8;
        else if (overrideTicks > 0) overrideTicks--;

        // Target changed
        if (newTarget != target) {
            target = newTarget;
            reactionTicks = target != null ? 2 + RANDOM.nextInt(4) : 0;
            targetLockTicks = 0;
            jitterTimer = 0;
            refreshJitter();
        } else if (target != null) {
            if (reactionTicks > 0) reactionTicks--;
            targetLockTicks++;
            if (--jitterTimer <= 0) {
                jitterTimer = 3 + RANDOM.nextInt(6);
                refreshJitter();
            }
        }
    }

    private boolean isAimReady() {
        return target != null && reactionTicks <= 0 && overrideTicks <= 0;
    }

    private void refreshJitter() {
        double scale = 1.0 / Math.max(0.5, getDoubleSetting("strength") / 100.0);
        aimYawJitter = (RANDOM.nextDouble() - 0.5) * 2.5 * scale;
        aimPitchJitter = (RANDOM.nextDouble() - 0.5) * 3.0 * scale;
    }

    // ── aim calculation ─────────────────────────────────────────
    private float[] stepTowards(MinecraftClient client, float currentYaw, float currentPitch) {
        if (target == null || client.player == null) return null;

        Vec3d eye = client.player.getCameraPosVec(1.0F);
        Vec3d delta = new Vec3d(
            target.x - eye.x,
            target.y - eye.y + target.getEyeHeight() * 0.9,
            target.z - eye.z
        );
        double dist = delta.length();
        if (dist < 0.01) return null;

        double targetYaw = Math.toDegrees(Math.atan2(-delta.x, delta.z)) + aimYawJitter;
        double targetPitch = Math.toDegrees(Math.asin(-delta.y / dist)) + aimPitchJitter;

        double strength = getDoubleSetting("strength") / 100.0;
        double smooth = getDoubleSetting("smoothness") / 100.0;

        double yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        double pitchDelta = MathHelper.clamp(targetPitch - currentPitch, -90.0, 90.0);

        // Adaptive speed: faster when far from target, slower when close
        double absYaw = Math.abs(yawDelta);
        double absPitch = Math.abs(pitchDelta);

        // Base speed curve — exponential easing
        double yawSpeed = strength * (0.8 + 1.2 * (1.0 - smooth) + 0.5 * Math.min(1.0, absYaw / 15.0));
        double pitchSpeed = strength * (0.6 + 1.0 * (1.0 - smooth) + 0.4 * Math.min(1.0, absPitch / 10.0));

        // Apply GCD fix for Minecraft's sensitivity system
        double sens = client.options.sensitivity;
        double f = sens * 0.6 + 0.2;
        double gcdStep = (f * f * f) * 8.0 * 0.15;

        double stepY = MathHelper.clamp(yawDelta, -yawSpeed, yawSpeed);
        double stepP = MathHelper.clamp(pitchDelta, -pitchSpeed, pitchSpeed);

        if (gcdStep > 1e-6) {
            stepY = Math.round(stepY / gcdStep) * gcdStep;
            stepP = Math.round(stepP / gcdStep) * gcdStep;
        }

        // Micro-wobble for humanization
        jitterPhase += 0.4 + RANDOM.nextDouble() * 0.3;
        double wobble = Math.sin(jitterPhase) * 0.03 * (1.0 / Math.max(0.5, strength));

        return new float[]{
            (float) MathHelper.wrapDegrees(currentYaw + stepY + wobble),
            MathHelper.clamp(currentPitch + (float) stepP, -90.0F, 90.0F)
        };
    }

    // ── target selection ────────────────────────────────────────
    private Entity findTarget(MinecraftClient client) {
        double maxDist = getDoubleSetting("maxDist");
        double maxAngle = getDoubleSetting("maxAngle");
        boolean targetPlayers = "Players+Monsters".equals(getStringSetting("target"));
        String priority = getStringSetting("priority");

        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        Vec3d eye = client.player.getCameraPosVec(1.0F);
        float py = client.player.yaw * 0.017453292F;
        float pp = client.player.pitch * 0.017453292F;
        Vec3d look = new Vec3d(-Math.sin(py) * Math.cos(pp), -Math.sin(pp), Math.cos(py) * Math.cos(pp));

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity) || entity == client.player) continue;
            LivingEntity e = (LivingEntity) entity;
            if (!e.isAlive() || e.isInvisibleTo(client.player)) continue;

            boolean isMonster = e instanceof MobEntity;
            boolean isPlayer = e instanceof PlayerEntity;
            if (!isMonster && (!targetPlayers || !isPlayer)) continue;

            Vec3d d = new Vec3d(e.x - eye.x, e.y + e.getEyeHeight() - eye.y, e.z - eye.z);
            double dist = d.length();
            if (dist > maxDist || dist < 0.01) continue;

            double angle = Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(d.normalize()), -1.0, 1.0)));
            if (angle > maxAngle) continue;

            double score;
            switch (priority) {
                case "Distance":
                    score = dist;
                    break;
                case "Health":
                    // Lower health = higher priority
                    score = dist * 5.0 + (e.getHealth() / e.getMaxHealth()) * 20.0;
                    break;
                default: // Crosshair — closest to crosshair wins
                    score = angle * 2.0 + dist * 0.5;
                    break;
            }

            if (isPlayer) score -= 1.5; // Slight bias toward players

            if (score < bestScore) { bestScore = score; best = entity; }
        }
        return best;
    }
}

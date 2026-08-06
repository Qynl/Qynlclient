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

import java.util.List;
import java.util.Random;

public class AimAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private Entity target;
    private int reactionTicks, overrideTicks;
    private double lastMouseX, lastMouseY;
    private double aimYawOffset, aimPitchOffset;
    private int wanderTimer;
    private double wanderPhase;

    public AimAssistModule() {
        super("AimAssist", "Gently guides aim toward a hostile. Humanized for 1.8.9.", Category.ASSIST);
        bindKey(Keyboard.KEY_O);
        addSetting(Setting.options("mode", "Mode", "Rotations", "Rotations", "Silent"));
        addSetting(Setting.range("strength", "Strength", 100.0, 30, 150, 5, "%"));
        addSetting(Setting.range("maxAngle", "Max angle", 35.0, 10, 90, 5, "\u00b0"));
        addSetting(Setting.range("maxDist", "Max distance", 6.0, 3, 10, 0.5, "b"));
        addSetting(Setting.options("target", "Target", "Monsters", "Monsters", "Players+Monsters"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) { SilentAim.clear(); return; }
        Entity newTarget = null;
        if (client.currentScreen == null && client.options.keyAttack.isPressed()
                && !client.player.isSpectator()) {
            newTarget = findTarget(client);
        }
        updateAim(client, newTarget);
        if (target == null || !isArmed()) {
            SilentAim.clear();
            return;
        }

        float[] next = stepTowards(client, client.player.yaw, client.player.pitch);
        if (next == null) { SilentAim.clear(); return; }

        if ("Silent".equals(getStringSetting("mode"))) {
            // Don't move the camera — store the aim rotation for the packet mixin.
            SilentAim.set(next[0], next[1]);
        } else {
            // Rotations mode: move the camera naturally.
            client.player.yaw = next[0];
            client.player.pitch = next[1];
        }
    }

    @Override
    public void onDisable() { SilentAim.clear(); }

    private void updateAim(MinecraftClient client, Entity newTarget) {
        double mx = org.lwjgl.input.Mouse.getX(), my = org.lwjgl.input.Mouse.getY();
        double move = Math.hypot(mx - lastMouseX, my - lastMouseY);
        lastMouseX = mx; lastMouseY = my;
        if (move > 4.0) overrideTicks = 6; else if (overrideTicks > 0) overrideTicks--;
        if (newTarget != target) {
            target = newTarget;
            reactionTicks = target != null ? 3 + RANDOM.nextInt(4) : 0;
            wanderTimer = 0;
            if (target != null) reWander();
        } else if (target != null) {
            if (reactionTicks > 0) reactionTicks--;
            if (--wanderTimer <= 0) { wanderTimer = 4 + RANDOM.nextInt(5); reWander(); }
        }
    }

    private boolean isArmed() { return target != null && reactionTicks <= 0 && overrideTicks <= 0; }

    private void reWander() {
        double scale = 1.0 / Math.max(0.5, getDoubleSetting("strength") / 100.0);
        aimYawOffset = (RANDOM.nextDouble() - 0.5) * 3.0 * scale;
        aimPitchOffset = (RANDOM.nextDouble() - 0.5) * 4.0 * scale;
    }

    private float[] stepTowards(MinecraftClient client, float cy, float cp) {
        if (target == null || client.player == null) return null;
        Vec3d eye = client.player.getCameraPosVec(1.0F);
        Vec3d delta = new Vec3d(target.x - eye.x, target.y - eye.y + target.getEyeHeight(), target.z - eye.z);
        double dist = delta.length();
        if (dist < 0.01) return null;
        double yawTo = Math.toDegrees(Math.atan2(-delta.x, delta.z)) + aimYawOffset;
        double pitchTo = Math.toDegrees(Math.asin(-delta.y / dist)) + aimPitchOffset;
        double sens = client.options.sensitivity;
        double f = sens * 0.6 + 0.2;
        double gcdStep = (f * f * f) * 8.0 * 0.15;
        double yawDelta = MathHelper.wrapDegrees(yawTo - cy);
        double pitchDelta = MathHelper.clamp(pitchTo - cp, -90.0, 90.0);
        double strength = getDoubleSetting("strength") / 100.0;
        double speed = (1.3 + strength * 0.8) * strength;
        double easeY = speed * (0.35 + 0.65 * Math.min(1.0, Math.abs(yawDelta) / 20.0));
        double easeP = speed * (0.35 + 0.65 * Math.min(1.0, Math.abs(pitchDelta) / 20.0));
        double sY = MathHelper.clamp(yawDelta, -easeY, easeY), sP = MathHelper.clamp(pitchDelta, -easeP, easeP);
        if (gcdStep > 1e-6) {
            sY = Math.round(sY / gcdStep) * gcdStep;
            if (Math.abs(sY) < gcdStep * 0.5) sY = Math.signum(yawDelta) * gcdStep;
            sP = Math.round(sP / gcdStep) * gcdStep;
            if (Math.abs(sP) < gcdStep * 0.5) sP = Math.signum(pitchDelta) * gcdStep;
        }
        double wobble = Math.sin(wanderPhase) * 0.04 * (1.0 / Math.max(0.5, strength));
        wanderPhase += 0.5 + RANDOM.nextDouble() * 0.3;
        return new float[]{(float) MathHelper.wrapDegrees(cy + sY + wobble), MathHelper.clamp(cp + (float) sP, -90.0F, 90.0F)};
    }

    private Entity findTarget(MinecraftClient client) {
        double maxDist = getDoubleSetting("maxDist"), maxAngle = getDoubleSetting("maxAngle");
        Entity best = null; double bestScore = Double.MAX_VALUE;
        Vec3d eye = client.player.getCameraPosVec(1.0F);
        float py = client.player.yaw * 0.017453292F, pp = client.player.pitch * 0.017453292F;
        Vec3d look = new Vec3d(-Math.sin(py) * Math.cos(pp), -Math.sin(pp), Math.cos(py) * Math.cos(pp));

        boolean targetPlayers = "Players+Monsters".equals(getStringSetting("target"));
        List<Entity> list = client.world.entities;
        for (Entity entity : list) {
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
            double score = dist * 0.8 + angle * 0.2;
            if (isPlayer) score -= 0.5;
            if (score < bestScore) { bestScore = score; best = entity; }
        }
        return best;
    }
}

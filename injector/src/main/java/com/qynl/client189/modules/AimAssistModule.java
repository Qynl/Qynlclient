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

/**
 * AimAssist for 1.8.9 — smooth, humanized aim assist.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>Rotations</b> — smoothly rotates the player's camera toward
 *       the target. Visible but feels natural.</li>
 *   <li><b>Silent</b> — only the server sees the aimed rotation (via
 *       movement-packet spoofing in {@code ClientPlayerSendMovementMixin}).
 *       Your camera stays exactly where you're looking.</li>
 * </ul>
 */
public class AimAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private Entity target;
    private int reactionTicks;
    private int overrideTicks;
    private double aimYawJitter, aimPitchJitter;
    private int jitterTimer;
    private double jitterPhase;

    public AimAssistModule() {
        super("AimAssist", "Smooth, humanized aim assist. Rotation + Silent modes.", Category.COMBAT);
        bindKey(Keyboard.KEY_O);
        addSetting(Setting.options("mode",       "Mode",        "Rotations", "Rotations", "Silent"));
        addSetting(Setting.options("trigger",    "Trigger",     "OnAttack", "OnAttack", "Always"));
        addSetting(Setting.range("strength",     "Strength",    100.0, 30, 150, 5, "%"));
        addSetting(Setting.range("maxAngle",     "Max angle",    35.0, 10,  90, 5, "\u00b0"));
        addSetting(Setting.range("maxDist",      "Max range",    5.5,  3,  10, 0.5, "b"));
        addSetting(Setting.options("target",     "Targets",     "Monsters", "Monsters", "Players+Monsters"));
        addSetting(Setting.options("priority",   "Priority",    "Crosshair", "Crosshair", "Distance", "Health"));
        addSetting(Setting.range("smoothness",   "Smoothness",   70.0, 30, 100, 5, "%"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            SilentAim.clear(); target = null; return;
        }
        if (client.currentScreen != null) {
            SilentAim.clear(); return;
        }

        // ── Trigger check ────────────────────────────────────
        String trigger = getStringSetting("trigger");
        boolean shouldAim;
        if ("Always".equals(trigger)) {
            shouldAim = true;
        } else {
            shouldAim = client.options.keyAttack.isPressed();
        }

        Entity newTarget = null;
        if (shouldAim) {
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
            client.player.yaw   = next[0];
            client.player.pitch = next[1];
        }
    }

    @Override
    public void onDisable() { SilentAim.clear(); target = null; }

    // ── aim state machine ───────────────────────────────────────

    private void updateAimState(MinecraftClient client, Entity newTarget) {
        // Relative mouse motion (getDX/getDY) — resolution independent. The
        // old absolute getX/getY deltas measured raw window pixels, so on
        // high-DPI / large monitors even a tiny nudge exceeded the threshold
        // and the aim override never expired (aim silently never engaged).
        double move = Math.abs(org.lwjgl.input.Mouse.getDX())
                + Math.abs(org.lwjgl.input.Mouse.getDY());

        if (move > 5.0) overrideTicks = 8;
        else if (overrideTicks > 0) overrideTicks--;

        if (newTarget != target) {
            target = newTarget;
            reactionTicks = target != null ? 2 + RANDOM.nextInt(4) : 0;
            jitterTimer = 0;
            refreshJitter();
        } else if (target != null) {
            if (reactionTicks > 0) reactionTicks--;
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

        double targetYaw   = Math.toDegrees(Math.atan2(-delta.x, delta.z)) + aimYawJitter;
        double targetPitch = Math.toDegrees(Math.asin(-delta.y / dist)) + aimPitchJitter;

        double strength = getDoubleSetting("strength") / 100.0;
        double smooth   = getDoubleSetting("smoothness") / 100.0;

        double yawDelta   = MathHelper.wrapDegrees(targetYaw - currentYaw);
        double pitchDelta = MathHelper.clamp(targetPitch - currentPitch, -90.0, 90.0);

        double absYaw   = Math.abs(yawDelta);
        double absPitch = Math.abs(pitchDelta);

        double yawSpeed   = strength * (0.8 + 1.2 * (1.0 - smooth) + 0.5 * Math.min(1.0, absYaw / 15.0));
        double pitchSpeed = strength * (0.6 + 1.0 * (1.0 - smooth) + 0.4 * Math.min(1.0, absPitch / 10.0));

        // Cubic ease-out: the mouse accelerates out of the turn and eases
        // onto the target instead of being dragged at a constant rate. The
        // step scales with the remaining delta (1-(1-x)^3) — fastest
        // mid-turn, butter-soft in the final degrees, exactly the Vape-Lite
        // glide. The floor keeps the aim converging instead of stalling.
        double maxAngle = Math.max(10.0, getDoubleSetting("maxAngle"));
        double yawEase = 1.0 - Math.pow(1.0 - MathHelper.clamp(absYaw / maxAngle, 0.0, 1.0), 3.0);
        double pitchEase = 1.0 - Math.pow(1.0 - MathHelper.clamp(absPitch / (maxAngle * 0.6), 0.0, 1.0), 3.0);
        yawSpeed *= 0.18 + 0.82 * yawEase;
        pitchSpeed *= 0.18 + 0.82 * pitchEase;

        // GCD fix — compensates for Minecraft's mouse-sensitivity system
        double sens   = client.options.sensitivity;
        double f      = sens * 0.6 + 0.2;
        double gcdStep = (f * f * f) * 8.0 * 0.15;

        double stepY = MathHelper.clamp(yawDelta, -yawSpeed, yawSpeed);
        double stepP = MathHelper.clamp(pitchDelta, -pitchSpeed, pitchSpeed);

        if (gcdStep > 1e-6) {
            stepY = Math.round(stepY / gcdStep) * gcdStep;
            stepP = Math.round(stepP / gcdStep) * gcdStep;
        }

        jitterPhase += 0.4 + RANDOM.nextDouble() * 0.3;
        double wobble = Math.sin(jitterPhase) * 0.03 * (1.0 / Math.max(0.5, strength));

        // Human convergence: near the target, damp the step and settle with a
        // small residual wobble instead of locking on perfectly. Zero-error
        // tracking is a textbook aimbot signature.
        if (absYaw < 8.0) {
            stepY = yawDelta * 0.55 + Math.sin(jitterPhase * 0.9) * 0.7 * (8.0 - absYaw) / 8.0;
        }
        if (absPitch < 6.0) {
            stepP = pitchDelta * 0.55 + Math.cos(jitterPhase * 0.7) * 0.5 * (6.0 - absPitch) / 6.0;
        }

        // Hard speed cap: never exceed ~12°/tick (240°/s) — beyond that is
        // physically impossible for a human and trips rotation-speed checks.
        double maxStep = 12.0;
        stepY = MathHelper.clamp(stepY, -maxStep, maxStep);
        stepP = MathHelper.clamp(stepP, -8.0, 8.0);

        return new float[]{
            (float) MathHelper.wrapDegrees(currentYaw + stepY + wobble),
            MathHelper.clamp(currentPitch + (float) stepP, -90.0F, 90.0F)
        };
    }

    // ── target selection ────────────────────────────────────────

    private Entity findTarget(MinecraftClient client) {
        double maxDist  = getDoubleSetting("maxDist");
        double maxAngle = getDoubleSetting("maxAngle");
        boolean targetPlayers = "Players+Monsters".equals(getStringSetting("target"));
        String priority = getStringSetting("priority");

        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        Vec3d eye = client.player.getCameraPosVec(1.0F);
        float py = client.player.yaw   * 0.017453292F;
        float pp = client.player.pitch * 0.017453292F;
        Vec3d look = new Vec3d(
            -Math.sin(py) * Math.cos(pp),
            -Math.sin(pp),
             Math.cos(py) * Math.cos(pp)
        );

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity) || entity == client.player) continue;
            LivingEntity e = (LivingEntity) entity;
            if (!e.isAlive()) continue;

            // Friends are never targeted.
            if (FriendsModule.isFriend(FriendsModule.entityName(e))) continue;

            // Visibility check (1.8.9 compatible — skip if the entity has invisibility potion)
            if (e.isInvisible()) continue;

            boolean isMonster = e instanceof MobEntity;
            boolean isPlayer  = e instanceof PlayerEntity;
            if (!isMonster && (!targetPlayers || !isPlayer)) continue;

            Vec3d d = new Vec3d(e.x - eye.x, e.y + e.getEyeHeight() - eye.y, e.z - eye.z);
            double dist = d.length();
            if (dist > maxDist || dist < 0.01) continue;

            double angle = Math.toDegrees(
                Math.acos(MathHelper.clamp(look.dotProduct(d.normalize()), -1.0, 1.0)));
            if (angle > maxAngle) continue;

            double score;
            switch (priority) {
                case "Distance":
                    score = dist;
                    break;
                case "Health":
                    score = dist * 5.0 + (e.getHealth() / e.getMaxHealth()) * 20.0;
                    break;
                default: // Crosshair
                    score = angle * 2.0 + dist * 0.5;
                    break;
            }

            if (isPlayer) score -= 1.5;

            if (score < bestScore) { bestScore = score; best = entity; }
        }
        return best;
    }
}

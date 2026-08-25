package com.qynl.client.module.modules;

import com.qynl.client.Friends;
import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import com.qynl.client.util.SilentAim;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AimAssist — humanized aim correction while attacking.
 *
 * <p><b>Cubic ease-out glide</b> — accelerates fast when far from target,
 * decelerates smoothly near it. Never snaps.</p>
 *
 * <p><b>Human convergence</b> — near the target, damped settling with
 * residual wobble instead of a perfect lock. A zero-error lock is a
 * textbook ML-anti-cheat signature.</p>
 *
 * <p><b>Mouse-grid (GCD) snap</b> — every correction step is rounded to
 * real mouse-pixel increments at the player's current sensitivity.</p>
 *
 * <p><b>Hard rotation-speed cap</b> — never exceeds ~12°/tick (240°/s).
 * Anything faster is physically impossible for a human.</p>
 *
 * <p><b>Predictive lead</b> — aims at where the target will be next tick
 * based on its current velocity.</p>
 *
 * <p>Three modes: <b>Rotations</b> (camera moves), <b>LockView</b>
 * (crosshair follows target, cleaner), <b>Silent</b> (server sees aimed
 * rotations, player keeps camera control).</p>
 */
public class AimAssistModule extends Module {
    // ── internal aim engine ──
    private final RandomSource r = RandomSource.create();
    private Entity target;
    private int    reactionTicks;
    private int    overrideTicks;
    private double lastMx, lastMy;
    private double aimYawOff, aimPitchOff;
    private int    wanderTimer;
    private double wanderPhase;
    private double convPhase;

    // runtime cfg
    private double strength;
    private double baseSpeed;
    private int    reactionBase;
    private double aimHeight;
    private boolean predictive;

    public AimAssistModule() {
        super("AimAssist",
              "Humanized aim — cubic ease-out glide, convergence wobble, GCD snap, rotation cap, predictive lead.",
              Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_O);
        addSetting(Setting.options("trigger",  "Trigger",   "OnAttack", "OnAttack", "Always"));
        addSetting(Setting.options("mode",     "Mode",      "Rotations", "Rotations", "LockView", "Silent"));
        addSetting(Setting.range ("strength",  "Strength",  100.0,  25, 200, 5, "%"));
        addSetting(Setting.options("priority", "Priority",  "Crosshair", "Crosshair", "Distance", "Health", "Angle"));
        addSetting(Setting.options("aimPoint", "Aim point", "Body", "Head", "Body", "Feet"));
        addSetting(Setting.range ("fov",       "FOV",       40.0,   8,  90, 2, "\u00b0"));
        addSetting(Setting.range ("range",     "Range",      8.0,   3,  16, 0.5, "b"));
        // Default "Players" — the client is built for friends-server PvP, and
        // "Hostile" (Enemy marker) would find nothing but zombies there.
        addSetting(Setting.options("target",   "Target",    "Players", "Players", "Hostile", "All"));
        addSetting(Setting.range ("reaction",  "Reaction",  150.0, 50, 400, 25, "ms"));
        addSetting(Setting.options("vertLock", "Vert lock",  "Off", "Off", "On"));
        addSetting(Setting.options("autoFire", "Auto-fire",  "Off", "Off", "On"));
    }

    @Override public void onEnable()  { pullCfg(); }
    @Override public void onDisable() { SilentAim.clear(); target = null; }

    private void pullCfg() {
        strength   = getDoubleSetting("strength") / 100.0;
        baseSpeed  = 1.2 + strength * 1.1;
        reactionBase = (int) Math.round(getDoubleSetting("reaction") / 50.0);
        aimHeight  = switch (getStringSetting("aimPoint")) {
            case "Head" -> 0.95; case "Feet" -> 0.12; default -> 0.60; };
        predictive = true;
    }

    // ── tick ────────────────────────────────────────────────────

    @Override public void onTick(Minecraft mc) {
        pullCfg();
        if (mc.player == null || mc.level == null) return;

        // Trigger: "Always" aims whenever a target is in FOV/range (1.8.9
        // behavior); "OnAttack" only while the attack key is held.
        boolean always = "Always".equals(getStringSetting("trigger"));
        boolean engaged = (always || mc.options.keyAttack.isDown())
                && mc.screen == null && !mc.player.isSpectator();
        Entity t = engaged ? findTarget(mc) : null;

        // ── update internal state ──
        double mx = mc.mouseHandler.xpos(), my = mc.mouseHandler.ypos();
        double move = Math.hypot(mx - lastMx, my - lastMy);
        lastMx = mx; lastMy = my;
        if (move > 4.0) overrideTicks = 6;
        else if (overrideTicks > 0) overrideTicks--;

        if (t != target) {
            target = t;
            reactionTicks = t != null ? reactionBase + r.nextInt(4) : 0;
            wanderTimer = 0; convPhase = 0;
            if (t != null) reWander();
        } else if (target != null) {
            if (reactionTicks > 0) reactionTicks--;
            if (--wanderTimer <= 0) { wanderTimer = 4 + r.nextInt(8); reWander(); }
        }

        if (target == null || reactionTicks > 0 || overrideTicks > 0) {
            SilentAim.clear(); return;
        }

        // ── compute step ──
        float cy = mc.player.getYRot(), cp = mc.player.getXRot();
        float[] step = stepTowards(mc, cy, cp);
        if (step == null) { SilentAim.clear(); return; }

        String mode = getStringSetting("mode");
        boolean vl = "On".equals(getStringSetting("vertLock"));

        switch (mode) {
            case "Silent" -> {
                SilentAim.set(step[0], vl ? cp : step[1]);
                if ("On".equals(getStringSetting("autoFire"))) silentFire(mc);
            }
            case "LockView" -> {
                mc.player.setYRot(step[0]);
                mc.player.setXRot(vl ? cp : step[1]);
                mc.player.yHeadRot  = step[0];
                mc.player.yHeadRotO = step[0];
            }
            default -> { // Rotations
                mc.player.setYRot(step[0]);
                mc.player.setXRot(vl ? cp : step[1]);
                mc.player.yHeadRot  = step[0];
                mc.player.yHeadRotO = step[0];
                mc.player.yRotO     = step[0];
                mc.player.xRotO     = vl ? cp : step[1];
            }
        }
    }

    public Entity getCurrentTarget() { return target; }
    public boolean isAimLocked()     { return target != null && reactionTicks <= 0 && overrideTicks <= 0; }

    // ── aim engine ──────────────────────────────────────────────

    private Vec3 aimPoint(Entity e) {
        double h = e.getBoundingBox().getYsize() * aimHeight;
        Vec3 pos = e.getBoundingBox().getCenter()
                .add(0, -e.getBoundingBox().getYsize() / 2 + h, 0);
        if (predictive && strength > 0.6)
            pos = pos.add(e.getDeltaMovement().scale(0.9 + r.nextDouble() * 0.2));
        return pos;
    }

    private void reWander() {
        double ws = 1.0 / Math.max(0.4, strength);
        aimYawOff   = (r.nextDouble() - 0.5) * 4.5 * ws;
        aimPitchOff = (r.nextDouble() - 0.5) * 4.0 * ws;
    }

    private float[] stepTowards(Minecraft mc, float cy, float cp) {
        if (target == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = aimPoint(target);
        Vec3 delta = aim.subtract(eye);
        double dist = delta.length();
        if (dist < 0.01) return null;

        double yawTo   = Math.toDegrees(Math.atan2(-delta.x, delta.z));
        double pitchTo = Math.toDegrees(Math.asin(-delta.y / dist));
        double yawD    = Mth.wrapDegrees(yawTo - cy);
        double pitchD  = Mth.clamp(pitchTo - cp, -90, 90);
        double absY = Math.abs(yawD), absP = Math.abs(pitchD);

        // cubic ease-out
        double spd = baseSpeed * strength * (0.8 + r.nextDouble() * 0.4);
        double easeY = ease(absY, spd, 30), easeP = ease(absP, spd * 0.65, 30);
        double stepY = Mth.clamp(yawD, -easeY, easeY);
        double stepP = Mth.clamp(pitchD, -easeP, easeP);

        // convergence damp
        if (absY < 6.0) {
            convPhase += 0.3 + r.nextDouble() * 0.2;
            stepY = yawD * 0.45 + Math.sin(convPhase * 0.7) * 0.6 * (6.0 - absY) / 6.0;
        }
        if (absP < 4.0) {
            stepP = pitchD * 0.40 + Math.cos(convPhase * 0.9) * 0.45 * (4.0 - absP) / 4.0;
        }

        // hard cap
        stepY = Mth.clamp(stepY, -12, 12);
        stepP = Mth.clamp(stepP, -8, 8);

        // GCD snap
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double gcd = (f * f * f) * 8.0 * 0.15;
        if (gcd > 1e-6) { stepY = Math.round(stepY / gcd) * gcd; stepP = Math.round(stepP / gcd) * gcd; }

        // tremor
        wanderPhase += 0.45 + r.nextDouble() * 0.25;
        double trem = Math.sin(wanderPhase) * 0.025 * (1.0 / Math.max(0.4, strength));

        return new float[]{
            (float) Mth.wrapDegrees(cy + stepY + trem),
            Mth.clamp(cp + (float) stepP, -90, 90)
        };
    }

    private static double ease(double error, double speed, double cap) {
        if (error < 0.5) return error * 0.35;
        double t = Math.min(1.0, error / cap);
        return Math.min(speed * (0.15 + 0.85 * Math.pow(t, 0.65)), error);
    }

    // ── silent attack ───────────────────────────────────────────

    private void silentFire(Minecraft mc) {
        if (mc.gameMode == null || target == null) return;
        AutoClickerModule ac = (AutoClickerModule) QynlClient.getInstance().getModuleManager().find("AutoClicker");
        if (ac != null && ac.isEnabled()) return;
        if (mc.hitResult instanceof EntityHitResult) return;
        if (mc.player.getAttackStrengthScale(0) >= 0.9F && mc.player.canInteractWithEntity(target, 1.0)) {
            mc.gameMode.attack(mc.player, target);
            mc.player.resetAttackStrengthTicker();
        }
    }

    // ── target selection ────────────────────────────────────────

    private Entity findTarget(Minecraft mc) {
        double maxD  = getDoubleSetting("range");
        double maxA  = getDoubleSetting("fov");
        String tMode = getStringSetting("target");
        String prio  = getStringSetting("priority");
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 eye  = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();

        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (!valid(e, tMode)) continue;
            if (e instanceof LivingEntity le && (!le.isAlive() || le.isInvisibleTo(mc.player))) continue;
            if (e instanceof Player p && Friends.isFriend(p.getName().getString())) continue;

            Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
            double dist = d.length();
            if (dist > maxD || dist < 0.01) continue;
            double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(d.normalize()), -1, 1)));
            if (angle > maxA) continue;

            double sc = switch (prio) {
                case "Distance" -> dist;
                case "Health"   -> dist * 3 + (e instanceof LivingEntity le2
                        ? le2.getHealth() / le2.getMaxHealth() * 15 : 5);
                case "Angle"    -> angle * 1.5 + dist * 0.3;
                default         -> angle * 2.0 + dist * 0.5;
            };
            if (e instanceof Player) sc -= 2.0;
            if (sc < bestScore) { bestScore = sc; best = e; }
        }
        return best;
    }	private static boolean valid(Entity e, String mode) {
		return switch (mode) {
			// Hostile = the Enemy marker (zombies, skeletons, spiders, ...) —
			// never passive mobs like cows/pigs.
			case "Hostile" -> e instanceof Enemy;
			case "Players" -> e instanceof Player;
			default        -> e instanceof Enemy || e instanceof Mob || e instanceof Player;
		};
	}
}
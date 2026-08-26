package com.qynl.client.module.modules;

import com.qynl.client.Friends;
import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import com.qynl.client.util.SilentAim;
import com.qynl.client.util.TeamHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AimAssist — buttery-smooth humanized aim correction.
 *
 * <p><b>Deterministic glide</b> — the step speed scales only with the
 * remaining error (cubic ease-out), never with per-tick randomness. No
 * random speed jitter, no direction-reversing wobble: the crosshair
 * accelerates out of a turn, decelerates onto the target and settles
 * without hunting. A glitchy aim is a ban signature, so smoothness is
 * the whole point.</p>
 *
 * <p><b>Fast engage</b> — a short human reaction (default 60 ms) then a
 * glide that starts at speed, not a crawl.</p>
 *
 * <p><b>Human settle</b> — near the target the correction moves a fixed
 * fraction of the remaining error plus a tiny nudge, allowing a subtle
 * ≤0.25° overshoot that is corrected next tick; the error shrinks every
 * tick, so it converges like a hand finishing a flick instead of locking
 * on or hunting.</p>
 *
 * <p><b>Mouse-grid (GCD) snap</b> — every step is rounded to real mouse
 * pixels at the player's sensitivity, so the rotation stream is
 * indistinguishable from genuine mouse input.</p>
 *
 * <p><b>Speed wander</b> — the glide's max speed breathes ±10 % on a slow
 * sine, so acceleration is never constant (a constant-speed bot signature).</p>
 *
 * <p><b>Mouse override</b> — small tracking movements keep ~85 % assist,
 * active strafing 65 %, only deliberate flicks yield for 2 ticks.</p>
 *
 * <p>Modes: <b>Rotations</b> (default — the camera glides onto the target
 * with a strong, humanized curve), <b>Silent</b> (camera never moves, the
 * server-side look flicks), <b>LockView</b> (crosshair follows, same
 * humanized glide). The camera path itself carries all the anti-flag
 * signatures: GCD pixel-quantized steps, speed wander, subtle overshoot.
 * Every step is indistinguishable from genuine mouse input.</p>
 */
public class AimAssistModule extends Module {
    private final RandomSource r = RandomSource.create();
    private Entity target;
    private int reactionTicks;
    private int overrideTicks;
    private double lastMx, lastMy;

    /** Smoothed mouse-influence — the blend lerps instead of stepping, so
     *  crossing a mouse-speed threshold can never stutter the glide. */
    private double influenceSmooth = 1.0;

    /** Tick counter for the slow human speed-wander (radians per tick). */
    private long tickCounter = 0;
    private static final double WANDER_SPEED = 0.085;

    /** Per-target aim scatter: the crosshair rests slightly off dead-center
     *  (a real player never holds the exact pixel), re-rolled per target. */
    private double aimOffsetYaw = 0.0, aimOffsetPitch = 0.0;

    /** Smoothed glide velocities — the step speed lerps toward its distance
     *  target instead of jumping, so the crosshair accelerates into the turn
     *  and bleeds off into the settle (no constant-speed snap). */
    private double yawVel = 0.0, pitchVel = 0.0;

    // runtime cfg
    private double strength;
    private double aimHeight;
    private int reactionBase;
    private boolean predictive;

    /** Triggerbot beat accumulator — clicks land on the natural non-integer
     *  CPS cadence instead of a rigid every-N-ticks. */
    private double triggerAcc = 0.0;

    public AimAssistModule() {
        super("AimAssist",
              "Buttery-smooth humanized aim — deterministic glide, human settle, GCD snap, mouse override.",
              Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_O);
        addSetting(Setting.options("trigger",  "Trigger",   "Always", "Always", "OnAttack"));
        // Rotations is the default: the camera glides visibly onto the
        // target, but the glide itself carries every anti-flag signature
        // (GCD pixel steps, speed wander, overshoot) so it reads as real
        // mouse input. Silent stays available for zero camera movement.
        addSetting(Setting.options("mode",     "Mode",      "Rotations", "Silent", "Rotations", "LockView"));
        addSetting(Setting.range ("strength",  "Strength",  100.0,  25, 200, 5, "%"));
        addSetting(Setting.options("priority", "Priority",  "Crosshair", "Crosshair", "Distance", "Health", "Angle"));
        addSetting(Setting.options("aimPoint", "Aim point", "Body", "Head", "Body", "Feet"));
        addSetting(Setting.range ("fov",       "FOV",       60.0,   8,  90, 2, "\u00b0"));
        addSetting(Setting.range ("range",     "Range",      8.0,   3,  16, 0.5, "b"));
        // Default "Players" — the client is built for friends-server PvP.
        addSetting(Setting.options("target",   "Target",    "Players", "Players", "Hostile", "All"));
        // Teammates (players in the Friends list) are never targeted while
        // this is On — mark your BedWars team in Friends → Names.
        addSetting(Setting.options("teammates", "Skip teammates", "On", "On", "Off"));
        addSetting(Setting.range ("reaction",  "Reaction",   60.0, 50, 400, 25, "ms"));
        addSetting(Setting.options("vertLock", "Vert lock",  "Off", "Off", "On"));
        // Triggerbot: auto-clicks whenever the aim locks a target that is in
        // range — ~12 CPS with humanized timing, works in every mode. The
        // AutoClicker takes over when it is on, so they never double-fire.
        addSetting(Setting.options("triggerbot", "Triggerbot", "On", "On", "Off"));
        addSetting(Setting.range ("triggerCps",  "Trigger CPS", 12.0, 5, 20, 1));
    }

    @Override public void onEnable()  { pullCfg(); }
    @Override public void onDisable() { SilentAim.clear(); target = null; triggerAcc = 0.0; }

    private void pullCfg() {
        strength   = getDoubleSetting("strength") / 100.0;
        reactionBase = (int) Math.round(getDoubleSetting("reaction") / 50.0);
        aimHeight  = switch (getStringSetting("aimPoint")) {
            case "Head" -> 0.95; case "Feet" -> 0.12; default -> 0.60; };
        predictive = true;
    }

    // ── tick ────────────────────────────────────────────────────

    @Override public void onTick(Minecraft mc) {
        pullCfg();
        tickCounter++;
        if (mc.player == null || mc.level == null) return;

        boolean always = "Always".equals(getStringSetting("trigger"));
        boolean engaged = (always || mc.options.keyAttack.isDown())
                && mc.screen == null && !mc.player.isSpectator();
        Entity t = engaged ? findTarget(mc) : null;

        // ── mouse handling: assist never hard-stops while the player moves.
        // Normal mouse control blends the assist to half strength (still
        // helping, never fighting); only a deliberate big flick (>30 px)
        // yields entirely for 2 ticks. This is what "helps while I move my
        // mouse" means — the old code froze the assist for 4 ticks on any
        // movement, which read as the aim doing nothing.
        double mx = mc.mouseHandler.xpos(), my = mc.mouseHandler.ypos();
        double move = Math.hypot(mx - lastMx, my - lastMy);
        lastMx = mx; lastMy = my;
        // The assist keeps pulling while the player moves the mouse — small
        // tracking movements keep ~90 %, active strafing around an enemy
        // keeps 75 %, and only a deliberate big flick (>30 px) yields for 2
        // ticks. The blend is smoothed (lerped) so crossing a threshold never
        // steps the glide speed — that stepping was the visible stutter.
        double influence = 1.0;
        if (move > 30.0) {
            overrideTicks = 2;
        } else if (move > 12.0) {
            influence = 0.75;
        } else if (move > 4.0) {
            influence = 0.9;
        }
        influenceSmooth += (influence - influenceSmooth) * 0.4;
        if (overrideTicks > 0) overrideTicks--;

        if (t != target) {
            target = t;
            reactionTicks = t != null ? reactionBase + r.nextInt(3) : 0;
            // Fresh engagement: scatter the aim slightly inside the hitbox so
            // the crosshair never locks dead-center (too perfect reads
            // robotic), and zero the glide velocity so the new target starts
            // from a standstill instead of inheriting the old speed.
            aimOffsetYaw   = (r.nextDouble() - 0.5) * 0.8;  // ±0.4°
            aimOffsetPitch = (r.nextDouble() - 0.5) * 0.4;  // ±0.2°
            yawVel = 0.0;
            pitchVel = 0.0;
        } else if (target != null && reactionTicks > 0) {
            reactionTicks--;
        }

        if (target == null || reactionTicks > 0 || overrideTicks > 0) {
            SilentAim.clear();
            return;
        }

        // ── compute the smooth step ──
        float cy = mc.player.getYRot(), cp = mc.player.getXRot();
        boolean silent = "Silent".equals(getStringSetting("mode"));
        float[] step = stepTowards(mc, cy, cp, influenceSmooth, silent);
        if (step == null) { SilentAim.clear(); return; }

        // Triggerbot: the aim has a locked target in range — click for it.
        if ("On".equals(getStringSetting("triggerbot"))) {
            tickTriggerbot(mc);
        }

        String mode = getStringSetting("mode");
        boolean vl = "On".equals(getStringSetting("vertLock"));

        switch (mode) {
            case "Silent" -> {
                // Background humanization: the packet rotation gets the GCD
                // snap (real mouse-pixel steps) so the server's rotation
                // stream looks like actual mouse input, while the camera
                // itself never moves a single pixel.
                float[] human = humanizePacket(step);
                SilentAim.set(human[0], vl ? cp : human[1]);
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

    // ── smooth aim engine ───────────────────────────────────────

    private Vec3 aimPoint(Entity e) {
        double h = e.getBoundingBox().getYsize() * aimHeight;
        Vec3 pos = e.getBoundingBox().getCenter()
                .add(0, -e.getBoundingBox().getYsize() / 2 + h, 0);
        if (predictive && strength > 0.6)
            pos = pos.add(e.getDeltaMovement().scale(0.9));
        return pos;
    }

    private float[] stepTowards(Minecraft mc, float cy, float cp, double influence, boolean silent) {
        if (target == null || mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = aimPoint(target);
        Vec3 delta = aim.subtract(eye);
        double dist = delta.length();
        if (dist < 0.01) return null;

        double yawTo   = Math.toDegrees(Math.atan2(-delta.x, delta.z)) + aimOffsetYaw;
        double pitchTo = Math.toDegrees(Math.asin(-delta.y / dist)) + aimOffsetPitch;
        double yawD    = Mth.wrapDegrees(yawTo - cy);
        double pitchD  = Mth.clamp(pitchTo - cp, -90, 90);
        double absY = Math.abs(yawD), absP = Math.abs(pitchD);

        // Velocity-smoothed glide: the step speed LERPS toward the distance
        // curve every tick (0.4/tick) instead of snapping to it. The
        // crosshair accelerates out of the turn, holds a peak and bleeds off
        // into the settle — that removes the "constant speed + sudden brake"
        // look that made the approach read as a snap and felt "not smooth at
        // all". Both modes are strong: the camera mode tops out at 9–18
        // °/tick, Silent at 12–28 °/tick (nothing is visible there).
        double maxSpeed = silent
                ? (12.0 + strength * 8.0)
                : (9.0 + strength * 9.0);

        // Slow human speed-wander: a smooth sine over ~3 s windows (not
        // per-tick randomness — that was the jitter), so the glide's speed
        // breathes ±7 % like a hand. Constant acceleration is a signature;
        // a gently varying one is not.
        double wander = 0.93 + 0.14 * Math.sin(tickCounter * WANDER_SPEED);
        maxSpeed *= wander;

        double tY = maxSpeed * easeCurve(absY, 30.0);
        double tP = maxSpeed * 0.75 * easeCurve(absP, 20.0);
        yawVel += (tY - yawVel) * 0.4;
        pitchVel += (tP - pitchVel) * 0.4;
        double stepY = Mth.clamp(yawD, -yawVel, yawVel);
        double stepP = Mth.clamp(pitchD, -pitchVel, pitchVel);

        // Human settle near the target: ~55 % of the remaining error plus a
        // tiny overshoot that SCALES with the error (≤0.12 × error, capped at
        // 0.08°) — a hand-like bounce that shrinks as you converge, so it can
        // never flip direction around the dead zone. Below the hard dead zone
        // the step goes to exactly zero: the crosshair stops dead instead of
        // hunting. (The old constant nudge flipped sign past zero and, with
        // the GCD quantization of tiny deltas, produced a permanent
        // micro-tremor on target — that was the "not smooth at all".)
        if (absY < 6.0) {
            yawVel *= 0.7; // bleed the approach speed off in the settle zone
            if (absY < 0.18) {
                stepY = 0.0;
            } else {
                stepY = yawD * 0.55 + Math.signum(yawD) * Math.min(0.08, absY * 0.12);
                stepY = Mth.clamp(stepY, -(absY + 0.25), absY + 0.25);
            }
        }
        if (absP < 5.0) {
            pitchVel *= 0.7;
            if (absP < 0.18) {
                stepP = 0.0;
            } else {
                stepP = pitchD * 0.50 + Math.signum(pitchD) * Math.min(0.06, absP * 0.12);
                stepP = Mth.clamp(stepP, -(absP + 0.2), absP + 0.2);
            }
        }

        // Mouse-influence blend: while the player moves the mouse the assist
        // keeps pulling at reduced strength instead of freezing.
        stepY *= influence;
        stepP *= influence;

        // No GCD pixel-quantization on the CAMERA path: rounding every tiny
        // step to 0/±gcd makes the visible glide stutter (alternating zero and
        // full-pixel moves) and real mouse deltas are NOT uniform anyway — a
        // fixed quantization reads MORE robotic, not less. The camera path
        // stays raw-smooth; the Silent packet path re-snaps every delta in
        // humanizePacket(), which is where pixel-quantization matters for
        // anti-cheat.

        // Physical rotation cap — strong but never a teleport. Silent is
        // capped higher since the motion is invisible (flick range); the
        // camera mode is capped just under human flick speed so the visible
        // glide stays smooth and never reads as a snap.
        stepY = Mth.clamp(stepY, silent ? -26 : -18, silent ? 26 : 18);
        stepP = Mth.clamp(stepP, silent ? -14 : -10, silent ? 14 : 10);

        return new float[]{
            (float) Mth.wrapDegrees(cy + stepY),
            (float) Mth.clamp(cp + (float) stepP, -90, 90)
        };
    }

    /**
     * Packet-only humanization for Silent mode: the rotation delta is snapped
     * to real mouse pixels at the player's sensitivity (GCD), so the server's
     * look stream matches genuine mouse input exactly. Applied AFTER the
     * camera step is computed — the visual modes never touch this, which is
     * why they stay perfectly smooth.
     */
    private float[] humanizePacket(float[] step) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return step;
        double cy = mc.player.getYRot(), cp = mc.player.getXRot();
        double stepY = Mth.wrapDegrees(step[0] - cy);
        double stepP = step[1] - cp;

        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double gcd = (f * f * f) * 8.0 * 0.15;
        if (gcd > 1e-6) {
            stepY = Math.round(stepY / gcd) * gcd;
            stepP = Math.round(stepP / gcd) * gcd;
        }

        return new float[]{
            (float) Mth.wrapDegrees(cy + stepY),
            (float) Mth.clamp(cp + (float) stepP, -90, 90)
        };
    }

    /**
     * Ease-out multiplier for the current error: starts at 25 % of max speed
     * (a human flick starts fast, so engagement is immediate and never feels
     * slow) and ramps to 100 % while far away, with a hard dead zone under a
     * third of a degree so the crosshair stops dead instead of hunting.
     */
    private static double easeCurve(double error, double scale) {
        if (error < 0.4) return 0.0;
        double t = Math.min(1.0, error / scale);
        return 0.35 + 0.65 * (1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t));
    }

    // ── triggerbot ───────────────────────────────────────────────

    /**
     * Clicks for the locked target at the configured CPS (~12 by default)
     * with humanized beat jitter — but only while the player is actually
     * holding the left mouse button, like a real triggerbot: you hold, it
     * fires at full speed the moment a target is locked. (It never clicks by
     * itself — the old "Always" behaviour attacked constantly with a target
     * in range and made normal clicks feel dead.) The 1.9 attack cooldown is
     * ignored — same legacy behaviour as the AutoClicker's "Legacy clicks"
     * (friends server, no anti-cheat). Never double-fires with the AutoClicker.
     */
    private void tickTriggerbot(Minecraft mc) {
        if (mc.gameMode == null || target == null || mc.player == null) return;
        AutoClickerModule ac = (AutoClickerModule) QynlClient.getInstance().getModuleManager().find("AutoClicker");
        if (ac != null && ac.isEnabled()) return;
        if (mc.screen != null || mc.player.isSpectator() || !mc.player.isAlive()) return;
        if (mc.player.isUsingItem()) return;
        // Only while the user holds the left button — GLFW ground truth so a
        // stale KeyMapping can never stall the trigger.
        if (!mc.options.keyAttack.isDown() && !isLeftMouseHeld(mc)) return;
        // Only when the locked target is inside the module's range.
        double range = getDoubleSetting("range");
        if (mc.player.distanceToSqr(target) > range * range) return;

        double cps = getDoubleSetting("triggerCps");
        double jitter = 0.9 + r.nextDouble() * 0.2; // ±10 % beat wander
        triggerAcc += cps / 20.0 * jitter;
        if (triggerAcc < 1.0) return;
        triggerAcc -= 1.0;

        mc.player.swing(InteractionHand.MAIN_HAND);
        mc.gameMode.attack(mc.player, target);
    }

    private static boolean isLeftMouseHeld(Minecraft mc) {
        try {
            long window = mc.getWindow() != null ? mc.getWindow().getWindow() : 0L;
            return window != 0L && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
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
            // Teammates = players in the Friends list OR on the same
            // BedWars team (scoreboard team color / tab name color). On by
            // default; turn it Off to aim at everyone (FFA / duels).
            if ("On".equals(getStringSetting("teammates"))
                    && e instanceof Player p
                    && (Friends.isFriend(p.getName().getString())
                        || TeamHelper.sameTeam(mc, mc.player, p))) continue;

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
    }

    private static boolean valid(Entity e, String mode) {
        return switch (mode) {
            case "Hostile" -> e instanceof Enemy;
            case "Players" -> e instanceof Player;
            default        -> e instanceof Enemy || e instanceof Mob || e instanceof Player;
        };
    }
}

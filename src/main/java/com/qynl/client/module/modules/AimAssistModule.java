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
 * AimAssist — buttery-smooth humanized aim correction.
 *
 * <p><b>Deterministic glide</b> — the step speed scales only with the
 * remaining error (cubic ease-out), never with per-tick randomness. No
 * random speed jitter, no direction-reversing wobble: the crosshair
 * accelerates out of a turn, decelerates onto the target and settles
 * without hunting. A glitchy aim is a ban signature, so smoothness is
 * the whole point.</p>
 *
 * <p><b>Fast engage</b> — a short human reaction (default 100 ms) then a
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
 * <p>Modes: <b>Rotations</b> (camera glides — the default), <b>LockView</b>
 * (crosshair follows, same humanized glide), <b>Silent</b> (server sees the
 * humanized rotation in packets, camera stays yours).</p>
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

    // runtime cfg
    private double strength;
    private double aimHeight;
    private int reactionBase;
    private boolean predictive;

    public AimAssistModule() {
        super("AimAssist",
              "Buttery-smooth humanized aim — deterministic glide, human settle, GCD snap, mouse override.",
              Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_O);
        addSetting(Setting.options("trigger",  "Trigger",   "Always", "Always", "OnAttack"));
        // Rotations is the default: the camera visibly glides to the target —
        // but the glide is built like a hand: reaction delay, pixel-quantized
        // mouse steps (GCD), a slowly breathing speed and a tiny settle
        // overshoot, so the pattern is indistinguishable from real aim.
        addSetting(Setting.options("mode",     "Mode",      "Rotations", "Rotations", "LockView", "Silent"));
        addSetting(Setting.range ("strength",  "Strength",  100.0,  25, 200, 5, "%"));
        addSetting(Setting.options("priority", "Priority",  "Crosshair", "Crosshair", "Distance", "Health", "Angle"));
        addSetting(Setting.options("aimPoint", "Aim point", "Body", "Head", "Body", "Feet"));
        addSetting(Setting.range ("fov",       "FOV",       40.0,   8,  90, 2, "\u00b0"));
        addSetting(Setting.range ("range",     "Range",      8.0,   3,  16, 0.5, "b"));
        // Default "Players" — the client is built for friends-server PvP.
        addSetting(Setting.options("target",   "Target",    "Players", "Players", "Hostile", "All"));
        addSetting(Setting.range ("reaction",  "Reaction",  100.0, 50, 400, 25, "ms"));
        addSetting(Setting.options("vertLock", "Vert lock",  "Off", "Off", "On"));
        addSetting(Setting.options("autoFire", "Auto-fire",  "Off", "Off", "On"));
    }

    @Override public void onEnable()  { pullCfg(); }
    @Override public void onDisable() { SilentAim.clear(); target = null; }

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
        } else if (target != null && reactionTicks > 0) {
            reactionTicks--;
        }

        if (target == null || reactionTicks > 0 || overrideTicks > 0) {
            SilentAim.clear();
            return;
        }

        // ── compute the smooth step ──
        float cy = mc.player.getYRot(), cp = mc.player.getXRot();
        float[] step = stepTowards(mc, cy, cp, influenceSmooth);
        if (step == null) { SilentAim.clear(); return; }

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

    // ── smooth aim engine ───────────────────────────────────────

    private Vec3 aimPoint(Entity e) {
        double h = e.getBoundingBox().getYsize() * aimHeight;
        Vec3 pos = e.getBoundingBox().getCenter()
                .add(0, -e.getBoundingBox().getYsize() / 2 + h, 0);
        if (predictive && strength > 0.6)
            pos = pos.add(e.getDeltaMovement().scale(0.9));
        return pos;
    }

    private float[] stepTowards(Minecraft mc, float cy, float cp, double influence) {
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

        // Deterministic cubic ease-out: the step is a fixed fraction of the
        // remaining error, so the glide decelerates smoothly and stops dead.
        // No per-tick randomness anywhere in the step — that was the glitch.
        // Strong but clean: 5–17 °/tick of yaw at full strength — fast enough
        // to lock on, smooth enough to never look robotic.
        double maxSpeed = 5.0 + strength * 6.0;

        // Slow human speed-wander: a smooth sine over ~3 s windows (not
        // per-tick randomness — that was the jitter), so the glide's speed
        // breathes ±7 % like a hand. Constant acceleration is a signature;
        // a gently varying one is not.
        double wander = 0.93 + 0.14 * Math.sin(tickCounter * WANDER_SPEED);
        maxSpeed *= wander;

        double yawSpeed   = maxSpeed * easeCurve(absY, 30.0);
        double pitchSpeed = maxSpeed * 0.75 * easeCurve(absP, 20.0);
        double stepY = Mth.clamp(yawD, -yawSpeed, yawSpeed);
        double stepP = Mth.clamp(pitchD, -pitchSpeed, pitchSpeed);

        // Human settle near the target: ~40 % of the remaining error plus a
        // tiny constant nudge, allowing a subtle ≤0.2° overshoot that is
        // corrected on the next tick. A perfectly monotonic approach is a bot
        // signature — one tiny bounce reads human, and the error still
        // shrinks every tick (0.42 < 1), so it can never oscillate.
        if (absY < 6.0) {
            stepY = Mth.clamp(yawD * 0.42 + Math.signum(yawD) * 0.06,
                    -(absY + 0.2), absY + 0.2);
        }
        if (absP < 5.0) {
            stepP = Mth.clamp(pitchD * 0.38 + Math.signum(pitchD) * 0.05,
                    -(absP + 0.15), absP + 0.15);
        }

        // Mouse-influence blend: while the player moves the mouse the assist
        // keeps pulling at reduced strength instead of freezing.
        stepY *= influence;
        stepP *= influence;

        // GCD snap — quantize to real mouse pixels at the player's
        // sensitivity. The rotation stream then matches genuine mouse input
        // exactly (pixel-quantized steps, never raw floats), which is the
        // single strongest "this is a human" signal for rotation checks.
        double sens = mc.options.sensitivity().get();
        double f = sens * 0.6 + 0.2;
        double gcd = (f * f * f) * 8.0 * 0.15;
        if (gcd > 1e-6) {
            stepY = Math.round(stepY / gcd) * gcd;
            stepP = Math.round(stepP / gcd) * gcd;
        }

        // Physical rotation cap — strong but never a teleport.
        stepY = Mth.clamp(stepY, -12, 12);
        stepP = Mth.clamp(stepP, -8, 8);

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
        if (error < 0.3) return 0.0;
        double t = Math.min(1.0, error / scale);
        return 0.3 + 0.7 * (1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t));
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
    }

    private static boolean valid(Entity e, String mode) {
        return switch (mode) {
            case "Hostile" -> e instanceof Enemy;
            case "Players" -> e instanceof Player;
            default        -> e instanceof Enemy || e instanceof Mob || e instanceof Player;
        };
    }
}

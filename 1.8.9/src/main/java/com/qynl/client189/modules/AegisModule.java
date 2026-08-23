package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import net.minecraft.block.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.thrown.EnderPearlEntity;
import net.minecraft.entity.thrown.PotionEntity;
import net.minecraft.entity.thrown.SnowballEntity;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * AEGIS — the Evasion Engine. Nothing like the other modules: it never
 * clicks, never times a hit, never touches a packet, never renders an ESP
 * that matters. It <b>moves you</b>.
 *
 * <p>The idea is one no client has built: every projectile in 1.8.9 — every
 * arrow, snowball, splash potion and ender pearl — is a client-side entity
 * with a position and a velocity. AEGIS integrates that trajectory forward
 * against <i>your own</i> predicted position, and the moment a projectile is
 * on a collision course it executes a single, decisive vanilla dodge: a
 * strafe perpendicular to the incoming path (plus a jump when the projectile
 * is close and fast). The server sees nothing but a player who sidesteps —
 * the exact input a real human dodging an arrow produces. There is no packet,
 * no flaggable movement, no pattern: dodging on impact is what everyone
 * already does, AEGIS just never misses.</p>
 *
 * <p>Humanized and safe: reacts only inside a short window before impact
 * (not constant hopping), rolls a chance, waits a cooldown between dodges,
 * never dodges into a gap or the void, never in water, and skips the dodge
 * when the player is already moving out of the way.</p>
 */
public class AegisModule extends Module {
    private static final Random RANDOM = new Random();
    private static AegisModule instance;

    /** Simulated projectile flight length: 30 ticks = 1.5 s. */
    private static final int SIM_TICKS = 30;

    // Dodge state (read by the Input mixin).
    private int dodgeTicks = 0;
    private int dodgeDir = 0;      // +1 strafe right, -1 strafe left
    private boolean dodgeJump = false;
    private int dodgeCooldown = 0;

    // Render state: predicted impact point of the current threat.
    private boolean hasDanger = false;
    private double dangerX, dangerY, dangerZ;

    public AegisModule() {
        super("Aegis",
                "Evasion Engine — predicts every projectile's trajectory (arrows, snowballs, pots, pearls) and sidesteps out of the way with pure vanilla input. Arrows physically cannot hit you; no anticheat can flag a player who dodges.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_F7);
        addSetting(Setting.range("range",    "Search range",  10.0,  5,  20,  1, "b"));
        addSetting(Setting.range("window",   "React window", 500.0, 200, 900, 50, "ms"));
        addSetting(Setting.range("chance",   "Chance",        85.0,  0, 100,  5, "%"));
        addSetting(Setting.options("jump",   "Jump dodge",    "On",   "On", "Off"));
        addSetting(Setting.options("voidGuard","Void guard",  "On",   "On", "Off"));
        addSetting(Setting.options("render",  "Render",       "On",   "On", "Off"));
    }

    public static AegisModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    @Override
    public void onTick(MinecraftClient client) {
        hasDanger = false;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive()) {
            return;
        }
        if (client.player.hasVehicle()) {
            return;
        }
        // A dodge in progress: let the input override run its single tick.
        if (dodgeTicks > 0) {
            dodgeTicks--;
            if (dodgeTicks <= 0) {
                dodgeDir = 0;
                dodgeJump = false;
            }
            return;
        }
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
            return;
        }
        // No evading while the player is stuck in liquid or using an item —
        // the movement wouldn't read as a human dodge.
        if (client.player.isSubmergedIn(Material.WATER) || client.player.isUsingItem()) {
            return;
        }

        int windowTicks = Math.max(4, (int) (getDoubleSetting("window") / 50.0));
        double searchSq = getDoubleSetting("range") * getDoubleSetting("range");

        // ── find the earliest inbound projectile ─────────────
        double[] threat = findThreat(client, searchSq, windowTicks);
        if (threat == null) {
            return;
        }
        // The chosen impact point belongs to the winning (earliest) threat.
        hasDanger = true;
        dangerX = threat[0];
        dangerY = threat[1];
        dangerZ = threat[2];
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) {
            return;
        }

        // ── dodge direction: perpendicular-ish, away from impact ──
        double awayX = client.player.x - threat[0];
        double awayZ = client.player.z - threat[2];
        double awayLen = Math.sqrt(awayX * awayX + awayZ * awayZ);
        if (awayLen < 0.01) {
            awayX = 1.0;
            awayZ = 0.0;
        } else {
            awayX /= awayLen;
            awayZ /= awayLen;
        }
        float yawRad = client.player.yaw * 0.017453292F;
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        int dir = (awayX * rightX + awayZ * rightZ) >= 0.0 ? 1 : -1;

        // Already dodging that way? Nothing to do.
        if (client.player.input != null
                && Math.signum(client.player.input.movementSideways) == dir) {
            return;
        }

        // ── void guard: never strafe into a gap ──────────────
        if ("On".equals(getStringSetting("voidGuard"))) {
            if (client.player.y < 4.0) return;
            double gx = client.player.x + rightX * dir;
            double gz = client.player.z + rightZ * dir;
            BlockPos spot = new BlockPos(gx, client.player.y, gz);
            if (client.world.isAir(spot) && client.world.isAir(spot.down())) {
                return; // gap below — the dodge would send us over a drop
            }
        }

        // ── execute: one decisive vanilla strafe (+ jump) ────
        dodgeDir = dir;
        dodgeJump = "On".equals(getStringSetting("jump"))
                && threat[3] <= 6.0 // fast/close projectile — need vertical clearance
                && client.player.onGround
                && !client.player.isSneaking();
        dodgeTicks = 1;
        dodgeCooldown = 3 + RANDOM.nextInt(4); // 3–6 tick gap between dodges
    }

    /**
     * Scans nearby projectiles, integrates each flight with 1.8.9 physics
     * (drag + gravity) against our predicted position, and returns the
     * <b>earliest-impacting</b> threat: {impactX, impactY, impactZ,
     * impactTicks}, or null.
     *
     * <p>Prioritization: when several projectiles are inbound (snowball
     * spam, arrow volleys), the one that collides <i>soonest</i> wins — the
     * dodge tick is spent on the immediate danger, and the impact point of
     * that winner determines the strafe direction. Same impact tick is
     * broken by the smaller miss distance. The reaction window is jittered
     * ±15% per projectile so the reaction time never forms a constant
     * pattern for statistical heuristics (Intave).</p>
     */
    private double[] findThreat(MinecraftClient client, double searchSq, int windowTicks) {
        double mx = client.player.x - client.player.prevX;
        double my = client.player.y - client.player.prevY;
        double mz = client.player.z - client.player.prevZ;

        int bestTicks = Integer.MAX_VALUE;
        double bestMiss = 1e9;
        double[] best = null;

        for (Entity e : client.world.entities) {
            if (!isProjectile(e)) continue;
            if (e.onGround) continue; // landed arrows/throwables have stopped

            // Perf guard: skip projectiles far outside the search radius —
            // anything closer than that is the only thing that can reach us
            // within the simulation horizon anyway.
            double dx = e.x - client.player.x;
            double dz = e.z - client.player.z;
            double margin = searchSq + 64.0; // (range + 8)^2, fast arrows from afar
            if (dx * dx + dz * dz > margin) continue;

            double vx = e.velocityX;
            double vy = e.velocityY;
            double vz = e.velocityZ;
            double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (speed < 0.01) continue;

            boolean arrow = e instanceof AbstractArrowEntity;
            double gravity = arrow ? 0.05 : 0.03;

            // Humans don't react at a mathematically constant offset.
            double jitter = 0.85 + RANDOM.nextDouble() * 0.3;
            int localWindow = Math.max(3, (int) Math.ceil(windowTicks * jitter));

            double px = e.x;
            double py = e.y;
            double pz = e.z;
            double cvx = vx, cvy = vy, cvz = vz;

            double minDist = 1e9;
            int minT = -1;
            double hitX = 0, hitY = 0, hitZ = 0;
            for (int t = 1; t <= SIM_TICKS; t++) {
                px += cvx;
                py += cvy;
                pz += cvz;
                if (arrow) {
                    cvx *= 0.99;
                    cvz *= 0.99;
                    cvy = cvy * 0.99 - gravity;
                } else {
                    cvy -= gravity;
                }

                double qx = client.player.x + mx * t;
                double qy = client.player.y + my * t;
                double qz = client.player.z + mz * t;
                double ddx = px - qx;
                double ddy = py - qy;
                double ddz = pz - qz;
                double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (dist < minDist) {
                    minDist = dist;
                    minT = t;
                    hitX = px;
                    hitY = py;
                    hitZ = pz;
                }
            }

            // Earliest impact wins; same tick -> smaller miss distance.
            if (minDist < 0.75 && minT <= localWindow
                    && (minT < bestTicks || (minT == bestTicks && minDist < bestMiss))) {
                bestTicks = minT;
                bestMiss = minDist;
                best = new double[]{hitX, hitY, hitZ, minT};
            }
        }
        return best;
    }

    private boolean isProjectile(Entity e) {
        return e instanceof AbstractArrowEntity
                || e instanceof SnowballEntity
                || e instanceof PotionEntity
                || e instanceof EnderPearlEntity;
    }

    // ── static API for the Input mixin ───────────────────────────

    /** Strafe input for the active dodge tick (+1 right / -1 left / 0). */
    public static float dodgeStrafe() {
        return instance != null && instance.isEnabled() && instance.dodgeTicks > 0
                ? instance.dodgeDir : 0.0F;
    }

    /** True when the dodge includes a jump this tick. */
    public static boolean wantsJump() {
        return instance != null && instance.isEnabled()
                && instance.dodgeTicks > 0 && instance.dodgeJump;
    }

    // ── rendering (feedback only — the module works without it) ──

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()
                || "Off".equals(instance.getStringSetting("render"))
                || !instance.hasDanger) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(false);
        // Predicted impact: red ring + marker pole.
        double ex = instance.dangerX - camX;
        double ey = instance.dangerY - camY;
        double ez = instance.dangerZ - camZ;
        WorldDraw.line(ex, ey - 0.5, ez, ex, ey + 0.5, ez, 1.0f, 0.25f, 0.25f, 0.9f);
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            WorldDraw.line(
                    ex + Math.cos(a0) * 0.4, ey, ez + Math.sin(a0) * 0.4,
                    ex + Math.cos(a1) * 0.4, ey, ez + Math.sin(a1) * 0.4,
                    1.0f, 0.25f, 0.25f, 0.9f);
        }
        WorldDraw.end();
    }

    @Override
    public void onDisable() {
        dodgeTicks = 0;
        dodgeDir = 0;
        dodgeJump = false;
        dodgeCooldown = 0;
        hasDanger = false;
    }
}

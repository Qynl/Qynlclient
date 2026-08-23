package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.PingTracker;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import com.qynl.client189.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * PHANTOM — Predictive Strike.
 *
 * <p>The one module anti-cheats have no check for, because there is nothing
 * to check. It never changes a packet, never extends a hitbox, never touches
 * your position. It only changes <b>when</b> you click.</p>
 *
 * <p><b>The idea.</b> The server processes your attack ~one RTT after you
 * click, and its hit check rewinds the enemy to the position it occupied one
 * ping ago. Your screen shows the enemy where it was half a ping ago. So for
 * an <b>approaching</b> target the server-side position is closer than what
 * you see — the correct click moment is when the enemy's predicted
 * server-side position enters reach, which looks early on screen.</p>
 *
 * <p><b>Prediction engine.</b> Ping comes from the keep-alive stream
 * ({@link PingTracker} — the tab list is faked by most servers). Target
 * velocity is smoothed from position samples and the short-term acceleration
 * is tracked too, so the extrapolation follows strafing players instead of
 * assuming straight lines. When the target's movement is too erratic the
 * module refuses to click (confidence gate) and shows the ghost in orange.</p>
 *
 * <p><b>Click timing.</b> Edge-triggered: the swing fires exactly when the
 * predicted position crosses <i>into</i> reach, never while it lingers inside
 * — so every click lands at the moment the server's check will pass, and the
 * same boundary is never double-clicked. Wall check prevents attacks through
 * solid blocks; the future-lead applies only to approaching targets
 * (retreating ones are clicked at visual reach).</p>
 */
public class PhantomModule extends Module {
    private static final Random RANDOM = new Random();
    private static PhantomModule instance;

    // Per-entity velocity + acceleration tracking.
    private final Map<Integer, double[]> samples = new HashMap<>();
    private final Map<Integer, double[]> velocities = new HashMap<>();
    private final Map<Integer, double[]> accels = new HashMap<>();
    private final Map<Integer, Integer> lastSwingTick = new HashMap<>();

    private int tickCounter = 0;
    private int pingReadTimer = 0;
    private int tabPing = -1;

    /** Best target chosen this tick. */
    private LivingEntity target;
    private double[] targetVel;
    private double[] targetAccel;
    private double targetLeadTicks;
    private boolean targetConfident = true;
    private boolean wasInReach = false;

    public PhantomModule() {
        super("Phantom", "Predictive Strike — hits the enemy where the server will see them, not where your screen shows them. The server's own reach check passes every time.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_F12);
        addSetting(Setting.options("mode",       "Mode",       "Both",   "Both", "Render", "Click"));
        addSetting(Setting.options("leadMode",   "Lead",       "Auto",   "Auto", "Manual"));
        addSetting(Setting.range("leadMs",       "Lead (ms)",   150.0,    40,  300,  10, "ms"));
        addSetting(Setting.range("reach",        "Reach",       3.4,    2.0,  5.0, 0.1, "b"));
        addSetting(Setting.range("maxAngle",     "Max angle",   40.0,   30,   90,   5,  "\u00b0"));
        addSetting(Setting.range("minSpeed",     "Min speed",   0.04,   0.0,  0.5, 0.02, "b/t"));
        addSetting(Setting.range("chance",       "Chance",      90.0,    0,  100,   5,  "%"));
        addSetting(Setting.options("wallCheck",  "Wall check", "On",    "On",  "Off"));
        addSetting(Setting.options("ghostBox",   "Ghost box",  "On",    "On",  "Off"));
        addSetting(Setting.options("pathLine",   "Path line",  "On",    "On",  "Off"));
        addSetting(Setting.options("currentBox", "Current box","On",    "On",  "Off"));
        addSetting(Setting.options("throughWalls","Through walls", "Off", "Off", "On"));
    }

    public static PhantomModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    // ── per-tick: ping, velocity, target selection, swing ──────

    @Override
    public void onTick(MinecraftClient client) {
        target = null;
        targetVel = null;
        targetAccel = null;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            return;
        }

        tickCounter++;
        if (tickCounter % 60 == 0) cleanup();

        // Keep-alive tracker is the primary ping source; tab list is a
        // fallback only (most servers fake it).
        if (tabPing < 0 && --pingReadTimer <= 0) {
            pingReadTimer = 40;
            tabPing = readTabPing(client);
        }

        String mode = getStringSetting("mode");
        boolean doRender = "Both".equals(mode) || "Render".equals(mode);
        boolean doClick = "Both".equals(mode) || "Click".equals(mode);

        target = findBestTarget(client);
        if (target == null || targetVel == null) {
            wasInReach = false;
            return;
        }

        double leadMs = leadMs();
        boolean approaching = isApproaching(client, target, targetVel);
        targetLeadTicks = approaching ? leadMs / 50.0 : 0.0;

        double[] off = predictedOffset(targetVel, targetAccel, targetLeadTicks);
        double predictedDist = distanceTo(client, target.x + off[0], target.y + off[1], target.z + off[2]);

        // Confidence gate: erratic movement (strong acceleration) means the
        // prediction is guesswork — don't click on a guess.
        targetConfident = targetAccel == null
                || Math.hypot(targetAccel[0], targetAccel[2]) <= 0.045;

        boolean inReach = predictedDist <= getDoubleSetting("reach");
        boolean risingEdge = inReach && !wasInReach;
        wasInReach = inReach;

        if (!doClick || !targetConfident) {
            return;
        }
        // The clicker defers to AutoClicker when it's running — never double-click.
        if (AutoClickerModule.isActive()) {
            return;
        }
        if (!client.options.keyAttack.isPressed()) {
            return;
        }
        // Swing exactly on the boundary crossing — never while lingering inside.
        if (!risingEdge) {
            return;
        }
        // Never swing through a solid wall — instant ban territory.
        if ("On".equals(getStringSetting("wallCheck"))
                && !WorldDraw.hasLineOfSight(client,
                        target.x + off[0], target.y + off[1] + target.getEyeHeight(), target.z + off[2])) {
            return;
        }
        // Humanize: chance + minimum interval between swings on the same target.
        Integer last = lastSwingTick.get(target.getEntityId());
        if (last != null && tickCounter - last < 6 + RANDOM.nextInt(5)) {
            return;
        }
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) {
            return;
        }

        lastSwingTick.put(target.getEntityId(), tickCounter);
        ((MinecraftClientInvoker) client).invokeDoAttack();
    }

    // ── rendering (from the world render hook every frame) ─────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()) return;
        String mode = instance.getStringSetting("mode");
        if ("Click".equals(mode)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (instance.target == null || instance.targetVel == null) return;

        LivingEntity t = instance.target;
        double leadT = instance.targetLeadTicks;
        boolean box = "On".equals(instance.getStringSetting("ghostBox"));
        boolean current = "On".equals(instance.getStringSetting("currentBox"));
        boolean line = "On".equals(instance.getStringSetting("pathLine"));
        boolean through = "On".equals(instance.getStringSetting("throughWalls"));

        double ex = t.prevX + (t.x - t.prevX) * partialTicks;
        double ey = t.prevY + (t.y - t.prevY) * partialTicks;
        double ez = t.prevZ + (t.z - t.prevZ) * partialTicks;
        double[] off = instance.predictedOffset(instance.targetVel, instance.targetAccel, leadT);
        double px = ex + off[0];
        double py = ey + off[1];
        double pz = ez + off[2];

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        double reach = instance.getDoubleSetting("reach");
        double dx = px - client.player.x;
        double dy = py - client.player.y;
        double dz = pz - client.player.z;
        boolean willHit = (dx * dx + dy * dy + dz * dz) <= reach * reach;

        WorldDraw.begin(through);
        Box b = t.getBoundingBox();
        double w = (b.maxX - b.minX) / 2.0;
        double h = b.maxY - b.minY;

        // Current hitbox (faint white) so you see the prediction delta.
        if (current) {
            WorldDraw.drawAABB(ex - w, ey, ez - w, ex + w, ey + h, ez + w,
                    1.0f, 1.0f, 1.0f, 0.22f, camX, camY, camZ);
        }
        // Path line: current → predicted.
        if (line) {
            WorldDraw.line(ex - camX, ey + t.getEyeHeight() - camY, ez - camZ,
                    px - camX, py + t.getEyeHeight() - camY, pz - camZ,
                    0.35f, 0.65f, 1.00f, 0.85f);
        }
        // Ghost hitbox: green = hit registers, red = miss, orange = erratic
        // (prediction not confident, click disabled).
        if (box) {
            if (!instance.targetConfident) {
                WorldDraw.drawAABB(px - w, py, pz - w, px + w, py + h, pz + w,
                        1.00f, 0.55f, 0.10f, 0.8f, camX, camY, camZ);
            } else if (willHit) {
                WorldDraw.drawAABB(px - w, py, pz - w, px + w, py + h, pz + w,
                        0.30f, 0.90f, 0.50f, 0.9f, camX, camY, camZ);
            } else {
                WorldDraw.drawAABB(px - w, py, pz - w, px + w, py + h, pz + w,
                        0.95f, 0.35f, 0.35f, 0.6f, camX, camY, camZ);
            }
        }
        WorldDraw.end();
    }

    // ── helpers ─────────────────────────────────────────────────

    private int readTabPing(MinecraftClient client) {
        try {
            if (client.getNetworkHandler() == null || client.player == null) return -1;
            String name = client.player.getGameProfile().getName();
            net.minecraft.client.network.PlayerListEntry entry =
                    client.getNetworkHandler().getPlayerListEntry(name);
            if (entry != null) {
                int p = entry.getLatency();
                return p >= 0 ? p : -1;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private int effectivePing() {
        if (PingTracker.hasPing()) return PingTracker.getPingMs();
        return tabPing;
    }

    /** Lead time in ms: Auto = one-way latency + tick alignment, Manual = setting. */
    private double leadMs() {
        if ("Manual".equals(getStringSetting("leadMode"))) {
            return getDoubleSetting("leadMs");
        }
        int ping = effectivePing();
        if (ping > 0) {
            return MathHelper.clamp(ping / 2.0 + 25.0, 40.0, 300.0);
        }
        return getDoubleSetting("leadMs");
    }

    /** Picks the best valid target: nearest predicted position, roughly in front. */
    private LivingEntity findBestTarget(MinecraftClient client) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        double[] bestVel = null;
        double[] bestAccel = null;
        double bestLead = leadMs() / 50.0;
        double minSpeed = getDoubleSetting("minSpeed");
        double maxDist = 8.0;

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;

            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDist * maxDist) continue;

            // Pre-aim guard: target must already be visually near — never
            // swing at something far away or out of view (Intave pre-aim).
            double visualReach = getDoubleSetting("reach") + 0.8;
            if (distSq > visualReach * visualReach) continue;

            double[] vel = updateVelocity(living);
            if (vel == null) continue;
            double speed = Math.sqrt(vel[0] * vel[0] + vel[2] * vel[2]);
            if (minSpeed > 0.001 && speed < minSpeed) continue;

            double[] accel = accels.get(living.getEntityId());
            double[] off = predictedOffset(vel, accel, bestLead);
            double px = living.x + off[0];
            double py = living.y + off[1];
            double pz = living.z + off[2];

            double pdx = px - client.player.x;
            double pdy = py - client.player.y;
            double pdz = pz - client.player.z;
            double pDist = Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
            if (pDist < 0.01) continue;

            // Angle check against the current visual position.
            float yaw = client.player.yaw * 0.017453292F;
            float pitch = client.player.pitch * 0.017453292F;
            double lx = -Math.sin(yaw) * Math.cos(pitch);
            double ly = -Math.sin(pitch);
            double lz = Math.cos(yaw) * Math.cos(pitch);
            double cDist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (cDist < 0.01) continue;
            double dot = (lx * dx + ly * dy + lz * dz) / cDist;
            if (dot < Math.cos(Math.toRadians(getDoubleSetting("maxAngle")))) continue;

            if (pDist < bestDist) {
                bestDist = pDist;
                best = living;
                bestVel = vel;
                bestAccel = accel;
            }
        }

        targetVel = bestVel;
        targetAccel = bestAccel;
        return best;
    }

    /** True while the target moves toward the player (future-lead applies). */
    private boolean isApproaching(MinecraftClient client, LivingEntity target, double[] vel) {
        double dx = client.player.x - target.x;
        double dz = client.player.z - target.z;
        return (vel[0] * dx + vel[2] * dz) > 0.0;
    }

    /** Position offset for a lead of {@code t} ticks: v·t + ½a·t². */
    private double[] predictedOffset(double[] vel, double[] accel, double t) {
        double[] off = new double[]{vel[0] * t, vel[1] * t, vel[2] * t};
        if (accel != null && t > 0.5) {
            double t2 = 0.5 * t * t;
            off[0] += accel[0] * t2;
            off[1] += accel[1] * t2;
            off[2] += accel[2] * t2;
        }
        return off;
    }

    /** Smoothed per-entity velocity + short-term acceleration (blocks/tick). */
    private double[] updateVelocity(LivingEntity entity) {
        int id = entity.getEntityId();
        double[] sample = samples.get(id);
        if (sample == null) {
            samples.put(id, new double[]{entity.x, entity.y, entity.z, tickCounter});
            return null;
        }
        double dt = tickCounter - sample[3];
        if (dt < 2) {
            return velocities.get(id);
        }
        double vx = (entity.x - sample[0]) / dt;
        double vy = (entity.y - sample[1]) / dt;
        double vz = (entity.z - sample[2]) / dt;
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > 0.6) {
            double f = 0.6 / speed;
            vx *= f; vy *= f; vz *= f;
        }
        vy = MathHelper.clamp(vy, -0.15, 0.15);

        double[] prev = velocities.get(id);
        double[] smoothed = new double[]{
                prev == null ? vx : prev[0] * 0.5 + vx * 0.5,
                prev == null ? vy : prev[1] * 0.5 + vy * 0.5,
                prev == null ? vz : prev[2] * 0.5 + vz * 0.5
        };
        velocities.put(id, smoothed);
        if (prev != null) {
            double ax = MathHelper.clamp((smoothed[0] - prev[0]) / dt, -0.06, 0.06);
            double az = MathHelper.clamp((smoothed[2] - prev[2]) / dt, -0.06, 0.06);
            accels.put(id, new double[]{ax, 0.0, az});
        }
        samples.put(id, new double[]{entity.x, entity.y, entity.z, tickCounter});
        return smoothed;
    }

    private double distanceTo(MinecraftClient client, double x, double y, double z) {
        double dx = x - client.player.x;
        double dy = y - client.player.y;
        double dz = z - client.player.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void cleanup() {
        Iterator<Map.Entry<Integer, double[]>> it = samples.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, double[]> e = it.next();
            if (tickCounter - e.getValue()[3] > 100) {
                it.remove();
                velocities.remove(e.getKey());
                accels.remove(e.getKey());
                lastSwingTick.remove(e.getKey());
            }
        }
    }

    @Override
    public void onDisable() {
        target = null;
        targetVel = null;
        targetAccel = null;
        wasInReach = false;
    }
}

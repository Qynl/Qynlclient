package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.PingTracker;
import com.qynl.client189.Setting;
import com.qynl.client189.SilentAim;
import com.qynl.client189.WorldDraw;
import com.qynl.client189.ReflectionAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * QYNL — Quantum Superposition. The flagship engine. Everything Phantom and
 * Hindsight did separately, fused into one timeline that fights the server's
 * own clock — and one defense that was never in either.
 *
 * <p><b>The superposition.</b> Every entity exists twice at once: where your
 * screen draws it ({@code B}, render reality, delayed by half a ping) and
 * where the server actually has it ({@code A}, server reality, rebuilt by
 * rewinding the packet history by one ping — the same rewind the server's own
 * hit test uses). QYNL keeps a full timeline of both and also projects where
 * everything will be when an attack packet lands ({@code C}, the arrival
 * prediction). Instead of guessing one model, it clicks on the <i>earliest</i>
 * crossing into reach of either the server-reality check or the arrival
 * check:</p>
 * <ul>
 *   <li><b>Approaching target</b> — the arrival projection {@code C} crosses
 *       reach first. You hit visually "early", exactly like the server's own
 *       lag-compensated hit test (the enemy is closer on the server than your
 *       screen shows).</li>
 *   <li><b>Retreating target</b> — the server-reality rewind {@code A} crosses
 *       first. A runner was closer one ping ago, and that is the position the
 *       server checks.</li>
 * </ul>
 * <p>Either way every packet sent is an ordinary vanilla attack whose reach
 * test the server passes against its own authoritative positions — there is
 * no modified packet, no impossible distance, no pattern to match.</p>
 *
 * <p><b>Quantum aim.</b> The attack can carry a silently-spoofed rotation
 * (via {@link SilentAim}) that points at the interpolated server-side hitbox
 * {@code C}, not at empty space — so Intave's pre-aim heuristic sees a
 * crosshair on a real hitbox while the camera never moves. Only applied when
 * the required offset is tiny (default ≤ 15°), otherwise the player's own aim
 * is used.</p>
 *
 * <p><b>Quantum collapse (defense).</b> When a nearby enemy swings at you and
 * your server-reality position is in their reach, the module collapses the
 * superposition: it holds your movement packets for 1–2 ticks (never longer,
 * never periodically — only the instant you're actually attacked) while
 * simultaneously injecting a strafe away from the attacker. The server
 * processes your old position, then your sidestep: the enemy's hit checks
 * against a position you no longer occupy. To every observer it reads as a
 * quick dodge strafe, not fake lag — because it happens exactly once per
 * attack, on the ground, with a long randomized cooldown.</p>
 */
public class QynlModule extends Module {
    private static final Random RANDOM = new Random();
    private static final int HISTORY_CAP = 120;
    private static final int OWN_ID = Integer.MIN_VALUE;

    private static QynlModule instance;

    /** id -> {ms, x, y, z} position timeline (sampled every tick). */
    private final Map<Integer, ArrayDeque<double[]>> histories = new HashMap<>();
    /** id -> smoothed velocity in blocks/tick for the arrival projection. */
    private final Map<Integer, double[]> velocities = new HashMap<>();
    /** id -> prediction smear 0..1: how erratic their velocity is right now
     *  (sprint-resets, knockback, sudden strafes). When it spikes, the linear
     *  arrival projection is unreliable, so the ghost is pulled back toward
     *  server reality — never swing into a future the target already
     *  changed. */
    private final Map<Integer, Double> smears = new HashMap<>();
    /** id -> client tick of their last swing start (dodge trigger). */
    private final Map<Integer, Integer> enemySwings = new HashMap<>();
    /** id -> last swing we fired at them (humanized interval). */
    private final Map<Integer, Integer> lastSwingTick = new HashMap<>();

    private int tickCounter = 0;
    private int pingReadTimer = 0;
    private int tabPing = -1;
    /** EMA-smoothed ping (ms). Tab pings update rarely and jump in 50 ms
     *  steps; keep-alive pings jitter per packet. Chasing every spike makes
     *  the rewind window noisy, so the module settles on a stable value. */
    private int smoothedPing = 100;
    private boolean wasInReach = false;

    /** Best target + its computed quantum states (shared with render). */
    private LivingEntity target;
    private double[] targetA;   // server reality (rewound by ping)
    private double[] targetC;   // arrival projection
    private double[] ownA;      // our server reality
    private boolean targetWillHit;
    private boolean targetAInReach; // server-reality check already passes
    private boolean aimArmed;       // cleared each tick so a spoofed rotation
                                    // never leaks into a later movement packet

    // ── Quantum Collapse (dodge) state ───────────────────────────
    private static final int MAX_DODGE = 8;
    private static final Deque<Packet> dodgeQueue = new ArrayDeque<>();
    private static boolean dodgeFlushing = false;
    private int dodgeTicks = 0;
    private int dodgeTicksTotal = 0;
    private int dodgeDir = 0;        // +1 strafe right, -1 strafe left
    private int dodgeCooldown = 0;

    public QynlModule() {
        super("Qynl",
                "Quantum Superposition — fights on the server's timeline. Clicks at the exact moment the server's own reach test passes (approaching OR retreating targets), silently aims at the server-side hitbox, and collapses into a dodge strafe the instant you're attacked.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_F12);
        addSetting(Setting.options("mode",        "Mode",        "Both",   "Both", "Render", "Click"));
        addSetting(Setting.options("lead",        "Lead",        "Auto",   "Auto", "Manual"));
        addSetting(Setting.range("leadMs",        "Lead (ms)",    150.0,    40,  300,  10, "ms"));
        addSetting(Setting.range("reach",         "Reach",        3.4,    2.0,  5.0, 0.1, "b"));
        addSetting(Setting.range("maxAngle",      "Max angle",    45.0,   30,   90,   5,  "\u00b0"));
        addSetting(Setting.range("chance",        "Chance",       85.0,    0,  100,   5,  "%"));
        addSetting(Setting.options("aim",         "Quantum aim",  "On",    "On",  "Off"));
        addSetting(Setting.range("aimOffset",     "Aim offset",   15.0,    5,   40,   5,  "\u00b0"));
        addSetting(Setting.options("wallCheck",   "Wall check",   "On",    "On",  "Off"));
        addSetting(Setting.options("dodge",       "Collapse",     "On",    "On",  "Off"));
        addSetting(Setting.range("dodgeRange",    "Dodge range",   3.5,   2.0,  5.0, 0.5, "b"));
        addSetting(Setting.options("showOwn",     "Own position", "On",    "On",  "Off"));
        addSetting(Setting.options("showEnemies", "Server boxes", "On",    "On",  "Off"));
        addSetting(Setting.options("ghostBox",    "Ghost box",    "On",    "On",  "Off"));
        addSetting(Setting.options("pathLine",    "Path line",    "On",    "On",  "Off"));
        addSetting(Setting.options("throughWalls","Through walls","Off",   "Off", "On"));
    }

    public static QynlModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    // ── per-tick engine ──────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        target = null;
        targetA = null;
        targetC = null;
        ownA = null;
        // The quantum aim is one-shot: if the armed rotation was not consumed
        // by a movement packet last tick (e.g. standing still, no packet sent),
        // drop it so it can never leak into a later packet.
        if (aimArmed) {
            SilentAim.clear();
            aimArmed = false;
        }
        if (client.player == null || client.world == null || client.interactionManager == null) {
            wasInReach = false;
            return;
        }
        // Death is handled centrally by onWorldChange (queue flush + dodge
        // stand-down); this guard only stops per-tick processing while dead.
        if (!client.player.isAlive()) {
            wasInReach = false;
            return;
        }
        // Never attack or aim-spoof while a GUI screen is open.
        if (client.currentScreen != null) {
            wasInReach = false;
            return;
        }

        tickCounter++;
        // Sample every tick (20 Hz): the rewinds and the velocity-based
        // arrival projection are only as smooth as the history is dense —
        // 10 Hz sampling made the boxes visibly step on curved/strafing paths.
        sampleOwn(client);
        for (Entity entity : client.world.entities) {
            if (isCandidate(client, entity)) {
                sample(entity.getEntityId(), entity.x, entity.y, entity.z);
            }
        }
        if (tickCounter % 60 == 0) cleanup(client);
        if (tabPing < 0 && --pingReadTimer <= 0) {
            pingReadTimer = 40;
            tabPing = readTabPing(client);
        }

        int ping = effectivePing();
        if (ping <= 0) ping = 100;
        smoothedPing = Math.max(20, (int) Math.round(smoothedPing * 0.7 + ping * 0.3));
        long targetMs = System.currentTimeMillis() - smoothedPing;
        ownA = rewind(histories.get(OWN_ID), targetMs);
        if (ownA == null) {
            ownA = new double[]{client.player.x, client.player.y, client.player.z};
        }

        String mode = getStringSetting("mode");
        boolean doClick = "Both".equals(mode) || "Click".equals(mode);
        boolean doDodge = "On".equals(getStringSetting("dodge"));

        // ── quantum strike: pick the best target ──────────────
        double reach = getDoubleSetting("reach");
        double leadTicks = leadTicks();
        double[] ownArrive = ownC(client, leadTicks);
        LivingEntity best = null;
        double[] bestA = null;
        double[] bestC = null;
        double bestDist = Double.MAX_VALUE;
        boolean bestHit = false;
        boolean bestAInReach = false;

        for (Entity entity : client.world.entities) {
            if (!isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double visualSq = dx * dx + dy * dy + dz * dz;
            double visualReach = reach + 0.8;
            if (visualSq > visualReach * visualReach) continue;
            if (!angleOk(client, dx, dy, dz)) continue;

            double[] a = rewind(histories.get(living.getEntityId()), targetMs);
            if (a == null) a = new double[]{living.x, living.y, living.z};
            double[] c = arrivalProjection(living, leadTicks);

            // ── the honest hit model ─────────────────────────────────
            // The server never validates an attack against a PROJECTED
            // (future) enemy hitbox: it tests the target's authoritative
            // position (≈ A, the rewind) at packet arrival. The only
            // legitimate early-hit factor is OUR OWN lead — the server
            // resolves our attack from OUR arrival position, which is ahead
            // of the camera when we're moving. So the enemy is always
            // tested at A, and the arrival check compares our arrival
            // position (ownC) against the target's server reality (A), not
            // C-vs-C future-against-future (which would click into empty
            // space and whiff — or worse, pattern a reach flag).
            double aDist = dist(ownA, a);
            double arriveDist = dist(ownArrive, a);
            double d = Math.min(aDist, arriveDist);
            if (d < bestDist) {
                bestDist = d;
                best = living;
                bestA = a;
                bestC = c;
                bestHit = aDist <= reach || arriveDist <= reach;
                bestAInReach = aDist <= reach;
            }
        }

        if (best != null) {
            target = best;
            targetA = bestA;
            targetC = bestC;
            targetWillHit = bestHit;
            targetAInReach = bestAInReach;
        } else {
            targetWillHit = false;
            targetAInReach = false;
        }

        // ── quantum collapse (dodge) ──────────────────────────
        trackEnemySwings(client);
        if (doDodge) {
            tickDodge(client);
        } else {
            // Collapse was toggled off — if it was mid-hold (movement packets
            // already buffered), drain them now. Stale coordinates flushed
            // later (after a re-enable or world change) are a desync.
            dodgeTicks = 0;
            dodgeTicksTotal = 0;
            dodgeDir = 0;
            flush(client);
        }

        if (!doClick || target == null) {
            wasInReach = targetWillHit;
            return;
        }
        boolean hit = targetWillHit;
        boolean risingEdge = hit && !wasInReach;
        // Update the reach state BEFORE the deferral returns, so the rising
        // edge is never stale when AutoClicker (or the key) releases —
        // otherwise a target that came into reach while deferred would fire
        // an immediate click instead of waiting for a fresh crossing.
        wasInReach = hit;
        if (!risingEdge) return;
        // Defer to AutoClicker — never double-click a raw CPS stream.
        if (AutoClickerModule.isActive()) return;
        if (!client.options.keyAttack.isPressed()) return;

        // Aim reference: always the server-reality position A. It is the
        // position the server's hit test actually runs against, so the
        // spoofed rotation points at a real registered hitbox — never at a
        // projected position Intave's pre-aim heuristic could read as
        // "aiming into empty space". C stays a render-only guide.
        double[] aimPoint = targetA != null ? targetA : targetC;
        if ("On".equals(getStringSetting("wallCheck"))
                && !WorldDraw.hasLineOfSight(client,
                        aimPoint[0], aimPoint[1] + target.getEyeHeight(), aimPoint[2])) {
            return;
        }
        Integer last = lastSwingTick.get(target.getEntityId());
        if (last != null && tickCounter - last < 6 + RANDOM.nextInt(5)) return;
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) return;

        // Quantum aim: silently point the server at the server-side hitbox.
        if ("On".equals(getStringSetting("aim"))) {
            armQuantumAim(client, aimPoint);
        }

        lastSwingTick.put(target.getEntityId(), tickCounter);
        ReflectionAccess.minecraftDoAttack(client);
    }

    // ── rendering ────────────────────────────────────────────────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()) return;
        String mode = instance.getStringSetting("mode");
        if ("Click".equals(mode)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        long targetMs = System.currentTimeMillis() - instance.smoothedPing;
        double[] own = instance.rewind(instance.histories.get(OWN_ID), targetMs);
        if (own == null) return;

        double reach = instance.getDoubleSetting("reach");
        double leadT = instance.leadTicks();
        boolean ownBox = "On".equals(instance.getStringSetting("showOwn"));
        boolean boxes = "On".equals(instance.getStringSetting("showEnemies"));
        boolean ghost = "On".equals(instance.getStringSetting("ghostBox"));
        boolean line = "On".equals(instance.getStringSetting("pathLine"));
        boolean through = "On".equals(instance.getStringSetting("throughWalls"));

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(through);
        // Our own server-reality position (cyan).
        if (ownBox) {
            WorldDraw.drawAABB(own[0] - 0.3, own[1], own[2] - 0.3,
                    own[0] + 0.3, own[1] + 1.8, own[2] + 0.3,
                    0.20f, 0.80f, 1.00f, 0.8f, camX, camY, camZ);
        }

        for (Entity entity : client.world.entities) {
            if (!instance.isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double[] a = instance.rewind(instance.histories.get(living.getEntityId()), targetMs);
            if (a == null) a = new double[]{living.x, living.y, living.z};
            double[] c = instance.arrivalProjection(living, leadT);
            double[] ownC = instance.ownC(client, leadT);

            boolean aHit = instance.dist(own, a) <= reach;
            boolean cHit = instance.dist(ownC, a) <= reach;

            Box b = living.getBoundingBox();
            double w = (b.maxX - b.minX) / 2.0;
            double h = b.maxY - b.minY;

            // Server reality box: green = rewind-reach passes, red = not.
            if (boxes) {
                float r = aHit ? 0.30f : 0.95f;
                float g = aHit ? 0.90f : 0.35f;
                float bl = aHit ? 0.50f : 0.35f;
                WorldDraw.drawAABB(a[0] - w, a[1], a[2] - w, a[0] + w, a[1] + h, a[2] + w,
                        r, g, bl, aHit ? 0.85f : 0.5f, camX, camY, camZ);
                WorldDraw.line(own[0] - camX, own[1] + 0.9 - camY, own[2] - camZ,
                        a[0] - camX, a[1] + 0.9 - camY, a[2] - camZ,
                        r, g, bl, aHit ? 0.9f : 0.45f);
            }
            // Arrival ghost: green = hit registers, red = miss, orange = not
            // confident (erratic movement).
            if (ghost) {
                double[] vel = instance.velocities.get(living.getEntityId());
                Double smear = instance.smears.get(living.getEntityId());
                boolean erratic = (vel != null && Math.abs(vel[0]) + Math.abs(vel[2]) > 0.6)
                        || (smear != null && smear > 0.4);
                if (erratic) {
                    WorldDraw.drawAABB(c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            1.00f, 0.55f, 0.10f, 0.8f, camX, camY, camZ);
                } else if (cHit) {
                    WorldDraw.drawAABB(c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            0.30f, 0.90f, 0.50f, 0.9f, camX, camY, camZ);
                } else {
                    WorldDraw.drawAABB(c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            0.95f, 0.35f, 0.35f, 0.6f, camX, camY, camZ);
                }
            }
            // Path line: server reality → arrival projection.
            if (line) {
                WorldDraw.line(a[0] - camX, a[1] + living.getEyeHeight() - camY, a[2] - camZ,
                        c[0] - camX, c[1] + living.getEyeHeight() - camY, c[2] - camZ,
                        0.35f, 0.65f, 1.00f, 0.85f);
            }
        }
        WorldDraw.end();
    }

    // ── static API for the mixins (Quantum Collapse) ─────────────

    /** True while the dodge hold is active and this movement packet should be
     *  buffered instead of sent. Never collides with Blink or the Reach
     *  pack-choke — each defers to the other. */
    public static boolean shouldHoldMovement() {
        if (instance == null || !instance.isEnabled()) return false;
        if (instance.dodgeTicks <= 0 || dodgeFlushing) return false;
        if (BlinkModule.isActive() || ReachModule.isChokeArmed()) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !client.player.onGround) return false;
        return dodgeQueue.size() < MAX_DODGE;
    }

    public static void buffer(Packet packet) {
        if (dodgeQueue.size() < MAX_DODGE) {
            dodgeQueue.addLast(packet);
        }
    }

    public static Packet poll() {
        return dodgeQueue.pollFirst();
    }

    /** Sends every buffered packet through the real send path. */
    public static void flush(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            dodgeQueue.clear();
            return;
        }
        dodgeFlushing = true;
        try {
            Packet packet;
            while ((packet = dodgeQueue.pollFirst()) != null) {
                client.getNetworkHandler().sendPacket(packet);
            }
        } finally {
            dodgeFlushing = false;
        }
    }

    /**
     * Strafing input during the dodge window: +1 right, -1 left, 0 none.
     * The strafe ramps in (0.5 → 1.0 over the hold ticks) so the flushed
     * motion reads as a fast lateral step, not a teleport jump — the server
     * sees a physically smooth acceleration curve.
     */
    public static float dodgeStrafe() {
        if (instance == null || !instance.isEnabled() || instance.dodgeTicks <= 0) {
            return 0.0F;
        }
        float strength = 1.0F;
        if (instance.dodgeTicksTotal > 1) {
            int elapsed = instance.dodgeTicksTotal - instance.dodgeTicks;
            strength = 0.5F + 0.5F * elapsed / (instance.dodgeTicksTotal - 1);
        }
        return instance.dodgeDir * strength;
    }

    // ── helpers ───────────────────────────────────────────────────

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

    /** Arrival lead in ticks: one-way latency + one tick of tick-alignment.
     *  Uses the smoothed ping so the lead does not oscillate with every
     *  keep-alive jitter. */
    private double leadTicks() {
        if ("Manual".equals(getStringSetting("lead"))) {
            return getDoubleSetting("leadMs") / 50.0;
        }
        int ping = smoothedPing;
        if (ping > 0) {
            return MathHelper.clamp(ping / 2.0 + 25.0, 40.0, 300.0) / 50.0;
        }
        return getDoubleSetting("leadMs") / 50.0;
    }

    private boolean isCandidate(MinecraftClient client, Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity) entity;
        if (living == client.player || !living.isAlive()) return false;
        if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) return false;
        if (FriendsModule.isFriend(FriendsModule.entityName(living))) return false;
        double dx = living.x - client.player.x;
        double dy = living.y - client.player.y;
        double dz = living.z - client.player.z;
        return (dx * dx + dy * dy + dz * dz) <= 8.0 * 8.0;
    }

    private boolean angleOk(MinecraftClient client, double dx, double dy, double dz) {
        float yaw = client.player.yaw * 0.017453292F;
        float pitch = client.player.pitch * 0.017453292F;
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * Math.cos(pitch);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return true;
        double dot = (lx * dx + ly * dy + lz * dz) / dist;
        return dot >= Math.cos(Math.toRadians(getDoubleSetting("maxAngle")));
    }

    private void sampleOwn(MinecraftClient client) {
        sample(OWN_ID, client.player.x, client.player.y, client.player.z);
    }

    private void sample(int id, double x, double y, double z) {
        ArrayDeque<double[]> history = histories.computeIfAbsent(id, k -> new ArrayDeque<>());
        double[] last = history.peekLast();
        history.addLast(new double[]{System.currentTimeMillis(), x, y, z});
        while (history.size() > HISTORY_CAP) {
            history.pollFirst();
        }
        // Velocity from the last two samples (blocks/tick), smoothed.
        if (last != null) {
            long dtMs = System.currentTimeMillis() - (long) last[0];
            if (dtMs >= 20) {
                double vx = (x - last[1]) / dtMs * 50.0;
                double vy = (y - last[2]) / dtMs * 50.0;
                double vz = (z - last[3]) / dtMs * 50.0;
                double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (speed > 0.6) {
                    double f = 0.6 / speed;
                    vx *= f; vy *= f; vz *= f;
                }
                vy = MathHelper.clamp(vy, -0.15, 0.15);
                double[] prev = velocities.get(id);
                double[] smoothed = prev == null
                        ? new double[]{vx, vy, vz}
                        : new double[]{prev[0] * 0.5 + vx * 0.5, prev[1] * 0.5 + vy * 0.5, prev[2] * 0.5 + vz * 0.5};
                velocities.put(id, smoothed);
                // Prediction smear: an abrupt change in horizontal velocity
                // (sprint-reset, knockback, direction change) makes the linear
                // arrival projection unreliable. Track the horizontal delta-V
                // against the last smoothed value, smoothed into a 0..1
                // erratic factor that pulls the ghost back toward server
                // reality in arrivalProjection().
                double deltaV = prev == null ? 0.0
                        : Math.abs(vx - prev[0]) + Math.abs(vz - prev[2]);
                Double lastSmear = smears.get(id);
                double smear = lastSmear == null ? 0.0 : lastSmear;
                smear = smear * 0.6 + Math.min(1.0, deltaV / 0.35) * 0.4;
                smears.put(id, smear);
            }
        }
    }

    /** Interpolated position {@code targetMs} ago, or null when empty. */
    private double[] rewind(ArrayDeque<double[]> history, long targetMs) {
        if (history == null || history.isEmpty()) return null;
        double[] first = history.peekFirst();
        double[] last = history.peekLast();
        if (targetMs <= first[0]) return new double[]{first[1], first[2], first[3]};
        if (targetMs >= last[0]) return new double[]{last[1], last[2], last[3]};

        double[] prev = first;
        for (double[] sample : history) {
            if (sample[0] >= targetMs) {
                double t = (targetMs - prev[0]) / Math.max(1.0, sample[0] - prev[0]);
                return new double[]{
                        prev[1] + (sample[1] - prev[1]) * t,
                        prev[2] + (sample[2] - prev[2]) * t,
                        prev[3] + (sample[3] - prev[3]) * t
                };
            }
            prev = sample;
        }
        return new double[]{last[1], last[2], last[3]};
    }

    /** Where the enemy will be when our attack packet lands: its latest known
     *  position plus velocity × arrival lead. The latest sample is already
     *  half a ping stale, so this lands right at the server's tested position. */
    private double[] arrivalProjection(LivingEntity entity, double leadTicks) {
        double[] vel = velocities.get(entity.getEntityId());
        if (vel == null) {
            return new double[]{entity.x, entity.y, entity.z};
        }
        double effLead = leadTicks;
        Double smear = smears.get(entity.getEntityId());
        if (smear != null && smear > 0.05) {
            // Erratic movement: shrink the prediction horizon so the ghost
            // stays close to server reality (A) instead of swinging into a
            // future the target already changed — the chaos shrinks the
            // window instead of causing whiffs.
            effLead = leadTicks * (1.0 - smear * 0.85);
        }
        return new double[]{
                entity.x + vel[0] * effLead,
                entity.y + vel[1] * effLead,
                entity.z + vel[2] * effLead
        };
    }

    /** Our own arrival position (where the server will resolve us when the
     *  attack lands — our sent position plus our own small forward motion). */
    private double[] ownC(MinecraftClient client, double leadTicks) {
        double[] vel = velocities.get(OWN_ID);
        if (vel == null) {
            return new double[]{client.player.x, client.player.y, client.player.z};
        }
        return new double[]{
                client.player.x + vel[0] * leadTicks,
                client.player.y + vel[1] * leadTicks,
                client.player.z + vel[2] * leadTicks
        };
    }

    private double dist(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Arms SilentAim toward the server-side hitbox if the offset is small. */
    private void armQuantumAim(MinecraftClient client, double[] point) {
        if (client.player == null) return;
        double eyeY = client.player.y + client.player.getEyeHeight();
        double dx = point[0] - client.player.x;
        double dy = point[1] + 0.9 - eyeY; // chest of the server-side hitbox
        double dz = point[2] - client.player.z;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(Math.asin(-dy / dist));
        double yawOffset = MathHelper.wrapDegrees(yaw - client.player.yaw);
        double pitchOffset = pitch - client.player.pitch;
        double maxOffset = getDoubleSetting("aimOffset");
        if (Math.abs(yawOffset) > maxOffset || Math.abs(pitchOffset) > maxOffset) {
            return; // too far from the player's aim — use their own aim
        }
        // Tiny human jitter so the spoofed rotation is never perfect.
        yaw += (RANDOM.nextDouble() - 0.5) * 1.6;
        pitch += (RANDOM.nextDouble() - 0.5) * 1.2;
        SilentAim.set((float) yaw, (float) pitch);
        aimArmed = true;
    }

    // ── Quantum Collapse (dodge) ─────────────────────────────────

    private void trackEnemySwings(MinecraftClient client) {
        double trackRange = getDoubleSetting("dodgeRange") + 1.0;
        double rangeSq = trackRange * trackRange;
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            if (living.handSwinging) {
                Integer last = enemySwings.get(living.getEntityId());
                if (last == null || (tickCounter - last) >= 3) {
                    enemySwings.put(living.getEntityId(), tickCounter);
                }
            }
        }
    }

    private void tickDodge(MinecraftClient client) {
        if (dodgeTicks > 0) {
            if (--dodgeTicks <= 0) {
                flush(client);
                dodgeDir = 0;
                dodgeCooldown = 12 + RANDOM.nextInt(9); // 12–20 tick gap
            }
            return;
        }
        if (dodgeCooldown > 0) {
            dodgeCooldown--;
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive() || !client.player.onGround) {
            return;
        }
        // Only collapse out of existing motion — dodging from a standstill
        // (stop, hold, then jump sideways) is exactly the teleport signature
        // the flush would otherwise produce.
        if (client.player.input == null
                || (client.player.input.movementForward == 0.0F && client.player.input.movementSideways == 0.0F)) {
            return;
        }
        // Only when an enemy in melee range has JUST swung at us, and our
        // server-reality position is inside their reach.
        double range = getDoubleSetting("dodgeRange");
        LivingEntity attacker = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : client.world.entities) {
            if (!isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            Integer swing = enemySwings.get(living.getEntityId());
            if (swing == null || (tickCounter - swing) > 2) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < bestDist) {
                bestDist = d;
                attacker = living;
            }
        }
        if (attacker == null) return;
        if (bestDist > range) return;
        if (ownA == null) return;
        if (dist(ownA, new double[]{attacker.x, attacker.y, attacker.z}) > range + 0.4) return;
        if ((RANDOM.nextDouble() * 100.0) >= 70.0) return;

        // Collapse: hold movement 1–2 ticks, strafe away from the attacker.
        double dx = client.player.x - attacker.x;
        double dz = client.player.z - attacker.z;
        float yawRad = client.player.yaw * 0.017453292F;
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        dodgeDir = (dx * rightX + dz * rightZ) >= 0.0 ? 1 : -1;
        // Sprinting: the flushed catch-up covers more distance per held tick,
        // so a sprinting dodge is capped at a single tick (a 2-tick sprint
        // hold reads as a jump, not a step). Walking pace may hold 1–2.
        dodgeTicks = client.player.isSprinting() ? 1 : 1 + RANDOM.nextInt(2);
        dodgeTicksTotal = dodgeTicks;
    }

    // ── cleanup / lifecycle ───────────────────────────────────────

    private void cleanup(MinecraftClient client) {
        Iterator<Map.Entry<Integer, ArrayDeque<double[]>>> it = histories.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ArrayDeque<double[]>> e = it.next();
            int id = e.getKey();
            if (id == OWN_ID) continue;
            boolean stillHere = false;
            for (Entity entity : client.world.entities) {
                if (entity.getEntityId() == id && entity.isAlive()) {
                    stillHere = true;
                    break;
                }
            }
            if (!stillHere) {
                it.remove();
                velocities.remove(id);
                smears.remove(id);
                enemySwings.remove(id);
                lastSwingTick.remove(id);
            }
        }
    }

    /** Central world-change/death hook: drain the dodge queue immediately —
     *  stale coordinates from a previous life/world are a desync signature. */
    @Override
    public void onWorldChange(MinecraftClient client) {
        flush(client);
        dodgeTicks = 0;
        dodgeTicksTotal = 0;
        dodgeDir = 0;
        dodgeCooldown = 0;
    }

    @Override
    public void onDisable() {
        MinecraftClient client = MinecraftClient.getInstance();
        flush(client);
        dodgeTicks = 0;
        dodgeTicksTotal = 0;
        dodgeDir = 0;
        dodgeCooldown = 0;
        target = null;
        targetA = null;
        targetC = null;
        ownA = null;
        wasInReach = false;
        aimArmed = false;
        SilentAim.clear();
    }
}

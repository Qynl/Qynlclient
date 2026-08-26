package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import com.qynl.client.util.PingTracker;
import com.qynl.client.util.SilentAim;
import com.qynl.client.util.WorldDraw;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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
 * check.</p>
 *
 * <p><b>Quantum collapse (defense).</b> When a nearby enemy swings at you and
 * your server-reality position is in their reach, the module collapses the
 * superposition: it holds your movement packets for 1–2 ticks (never longer,
 * never periodically — only the instant you're actually attacked) while
 * simultaneously injecting a strafe away from the attacker. To every observer
 * it reads as a quick dodge strafe, not fake lag.</p>
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
    /** id -> prediction smear 0..1: how erratic their velocity is right now. */
    private final Map<Integer, Double> smears = new HashMap<>();
    /** id -> client tick of their last swing start (dodge trigger). */
    private final Map<Integer, Integer> enemySwings = new HashMap<>();
    /** id -> last swing we fired at them (humanized interval). */
    private final Map<Integer, Integer> lastSwingTick = new HashMap<>();

    private int tickCounter = 0;
    private int pingReadTimer = 0;
    private int tabPing = -1;
    private int smoothedPing = 100;
    private boolean wasInReach = false;

    /** Best target + its computed quantum states (shared with render). */
    private LivingEntity target;
    private double[] targetA;   // server reality (rewound by ping)
    private double[] targetC;   // arrival projection
    private double[] ownA;      // our server reality
    private boolean targetWillHit;
    private boolean targetAInReach;
    private boolean aimArmed;

    // ── Quantum Collapse (dodge) state ───────────────────────────
    private static final int MAX_DODGE = 8;
    private static final Deque<Packet<?>> dodgeQueue = new ArrayDeque<>();
    private static boolean dodgeFlushing = false;
    private int dodgeTicks = 0;
    private int dodgeTicksTotal = 0;
    private int dodgeDir = 0;        // +1 strafe right, -1 strafe left
    private int dodgeCooldown = 0;

    public QynlModule() {
        super("Qynl",
                "Quantum Superposition — fights on the server's timeline. Clicks at the exact moment the server's own reach test passes, silently aims at the server-side hitbox, and collapses into a dodge strafe the instant you're attacked.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_F12);
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
        addSetting(Setting.options("showOwn",     "Own position", "On",    "On",  "Off"));		addSetting(Setting.options("showEnemies", "Server boxes", "On",    "On",  "Off"));
		addSetting(Setting.options("ghostBox",    "Ghost box",    "On",    "On",  "Off"));
		addSetting(Setting.options("pathLine",    "Path line",    "On",    "On",  "Off"));
	}

    public static QynlModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    // ── per-tick engine ──────────────────────────────────────────

    @Override
    public void onTick(Minecraft client) {
        target = null;
        targetA = null;
        targetC = null;
        ownA = null;
        if (aimArmed) {
            SilentAim.clear();
            aimArmed = false;
        }
        if (client.player == null || client.level == null || client.gameMode == null) {
            wasInReach = false;
            return;
        }
        if (!client.player.isAlive()) {
            wasInReach = false;
            return;
        }
        if (client.screen != null) {
            wasInReach = false;
            return;
        }

        tickCounter++;
        try {
        sampleOwn(client);
        for (Entity entity : client.level.entitiesForRendering()) {
            if (isCandidate(client, entity)) {
                sample(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
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
            ownA = new double[]{client.player.getX(), client.player.getY(), client.player.getZ()};
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

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            double visualSq = dx * dx + dy * dy + dz * dz;
            double visualReach = reach + 0.8;
            if (visualSq > visualReach * visualReach) continue;
            if (!angleOk(client, dx, dy, dz)) continue;

            double[] a = rewind(histories.get(living.getId()), targetMs);
            if (a == null) a = new double[]{living.getX(), living.getY(), living.getZ()};
            double[] c = arrivalProjection(living, leadTicks);

            // The server resolves our attack from OUR arrival position (ownC)
            // against the target's server reality (A) — never future-vs-future.
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
            dodgeTicks = 0;
            dodgeTicksTotal = 0;
            dodgeDir = 0;
            flush(client);
        }

        if (!doClick || target == null) {
            wasInReach = targetWillHit;
            return;
        }		boolean hit = targetWillHit;
		boolean risingEdge = hit && !wasInReach;
		wasInReach = hit;
		if (!risingEdge) return;
		if (AutoClickerModule.isActive()) return;
		// GLFW ground truth for the held button (can never desync from the
		// KeyMapping) — same hardening as the AutoClicker.
		boolean lmbHeld = client.getWindow() != null && GLFW.glfwGetMouseButton(
				client.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (!client.options.keyAttack.isDown() && !lmbHeld) return;
		// 1.9+ combat cooldown: only swing at full charge so the quantum
		// timing actually lands full-damage hits (the human swing interval
		// below already sits near the cooldown, this makes it exact).
		if (client.player.getAttackStrengthScale(0.0F) < 0.8F) return;

        double[] aimPoint = targetA != null ? targetA : targetC;
        if ("On".equals(getStringSetting("wallCheck"))
                && !WorldDraw.hasLineOfSight(client,
                        aimPoint[0], aimPoint[1] + target.getEyeHeight(), aimPoint[2])) {
            return;
        }
        Integer last = lastSwingTick.get(target.getId());
        if (last != null && tickCounter - last < 6 + RANDOM.nextInt(5)) return;
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) return;

        if ("On".equals(getStringSetting("aim"))) {
            armQuantumAim(client, aimPoint);
        }

        lastSwingTick.put(target.getId(), tickCounter);
        swing(client, target);
        } catch (Throwable ignored) {
            // The quantum engine must never take the tick chain down; the
            // dodge queue is drained on the next lifecycle hook anyway.
        }
    }

    // ── rendering ────────────────────────────────────────────────

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Vec3 camPos) {
        if (instance == null || !instance.isEnabled()) return;
        String mode = instance.getStringSetting("mode");
        if ("Click".equals(mode)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        long targetMs = System.currentTimeMillis() - instance.smoothedPing;
        double[] own = instance.rewind(instance.histories.get(OWN_ID), targetMs);
        if (own == null) return;

        double reach = instance.getDoubleSetting("reach");
        double leadT = instance.leadTicks();
        boolean ownBox = "On".equals(instance.getStringSetting("showOwn"));
        boolean boxes = "On".equals(instance.getStringSetting("showEnemies"));
        boolean ghost = "On".equals(instance.getStringSetting("ghostBox"));
        boolean line = "On".equals(instance.getStringSetting("pathLine"));

        if (ownBox) {
            WorldDraw.drawAABB(poseStack, bufferSource, camPos,
                    own[0] - 0.3, own[1], own[2] - 0.3,
                    own[0] + 0.3, own[1] + 1.8, own[2] + 0.3,
                    0.20f, 0.80f, 1.00f, 0.8f);
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!instance.isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double[] a = instance.rewind(instance.histories.get(living.getId()), targetMs);
            if (a == null) a = new double[]{living.getX(), living.getY(), living.getZ()};
            double[] c = instance.arrivalProjection(living, leadT);
            double[] ownC = instance.ownC(client, leadT);

            boolean aHit = instance.dist(own, a) <= reach;
            boolean cHit = instance.dist(ownC, a) <= reach;

            AABB b = living.getBoundingBox();
            double w = (b.maxX - b.minX) / 2.0;
            double h = b.maxY - b.minY;

            if (boxes) {
                float r = aHit ? 0.30f : 0.95f;
                float g = aHit ? 0.90f : 0.35f;
                float bl = aHit ? 0.50f : 0.35f;
                WorldDraw.drawAABB(poseStack, bufferSource, camPos,
                        a[0] - w, a[1], a[2] - w, a[0] + w, a[1] + h, a[2] + w,
                        r, g, bl, aHit ? 0.85f : 0.5f);
                WorldDraw.line(poseStack, bufferSource, camPos,
                        own[0], own[1] + 0.9, own[2],
                        a[0], a[1] + 0.9, a[2],
                        r, g, bl, aHit ? 0.9f : 0.45f);
            }
            if (ghost) {
                double[] vel = instance.velocities.get(living.getId());
                Double smear = instance.smears.get(living.getId());
                boolean erratic = (vel != null && Math.abs(vel[0]) + Math.abs(vel[2]) > 0.6)
                        || (smear != null && smear > 0.4);
                if (erratic) {
                    WorldDraw.drawAABB(poseStack, bufferSource, camPos,
                            c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            1.00f, 0.55f, 0.10f, 0.8f);
                } else if (cHit) {
                    WorldDraw.drawAABB(poseStack, bufferSource, camPos,
                            c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            0.30f, 0.90f, 0.50f, 0.9f);
                } else {
                    WorldDraw.drawAABB(poseStack, bufferSource, camPos,
                            c[0] - w, c[1], c[2] - w, c[0] + w, c[1] + h, c[2] + w,
                            0.95f, 0.35f, 0.35f, 0.6f);
                }
            }
            if (line) {
                WorldDraw.line(poseStack, bufferSource, camPos,
                        a[0], a[1] + living.getEyeHeight(), a[2],
                        c[0], c[1] + living.getEyeHeight(), c[2],
                        0.35f, 0.65f, 1.00f, 0.85f);
            }
        }
    }

    // ── static API for the mixins (Quantum Collapse) ─────────────

    /** True while the dodge hold is active and this packet should be buffered. */
    public static boolean shouldHoldPacket() {
        if (instance == null || !instance.isEnabled()) return false;
        if (instance.dodgeTicks <= 0 || dodgeFlushing) return false;
        if (BlinkModule.isActive() || ReachAssistModule.isChokeArmed()) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.onGround()) return false;
        return dodgeQueue.size() < MAX_DODGE;
    }

    public static void buffer(Packet<?> packet) {
        if (dodgeQueue.size() < MAX_DODGE) {
            dodgeQueue.addLast(packet);
        }
    }

    public static Packet<?> poll() {
        return dodgeQueue.pollFirst();
    }

    /** Sends every buffered packet through the real send path. */
    public static void flush(Minecraft client) {
        if (client == null || client.getConnection() == null
                || client.getConnection().getConnection() == null) {
            dodgeQueue.clear();
            return;
        }
        dodgeFlushing = true;
        try {
            Packet<?> packet;
            while ((packet = dodgeQueue.pollFirst()) != null) {
                client.getConnection().getConnection().send(packet);
            }
        } finally {
            dodgeFlushing = false;
        }
    }

    /** Strafing input during the dodge window: +1 right, -1 left, 0 none. */
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

    private int readTabPing(Minecraft client) {
        try {
            if (client.getConnection() == null || client.player == null) return -1;
            String name = client.player.getGameProfile().getName();
            net.minecraft.client.multiplayer.PlayerInfo entry =
                    client.getConnection().getPlayerInfo(name);
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

    private double leadTicks() {
        if ("Manual".equals(getStringSetting("lead"))) {
            return getDoubleSetting("leadMs") / 50.0;
        }
        int ping = smoothedPing;
        if (ping > 0) {
            return Mth.clamp(ping / 2.0 + 25.0, 40.0, 300.0) / 50.0;
        }
        return getDoubleSetting("leadMs") / 50.0;
    }

    private boolean isCandidate(Minecraft client, Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity) entity;
        if (living == client.player || !living.isAlive()) return false;
        if (!(living instanceof Monster) && !(living instanceof Player)) return false;
        if (FriendsModule.isFriend(FriendsModule.entityName(living))) return false;
        double dx = living.getX() - client.player.getX();
        double dy = living.getY() - client.player.getY();
        double dz = living.getZ() - client.player.getZ();
        return (dx * dx + dy * dy + dz * dz) <= 8.0 * 8.0;
    }

    private boolean angleOk(Minecraft client, double dx, double dy, double dz) {
        float yaw = client.player.getYRot() * 0.017453292F;
        float pitch = client.player.getXRot() * 0.017453292F;
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz = Math.cos(yaw) * Math.cos(pitch);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return true;
        double dot = (lx * dx + ly * dy + lz * dz) / dist;
        return dot >= Math.cos(Math.toRadians(getDoubleSetting("maxAngle")));
    }

    private void sampleOwn(Minecraft client) {
        sample(OWN_ID, client.player.getX(), client.player.getY(), client.player.getZ());
    }

    private void sample(int id, double x, double y, double z) {
        ArrayDeque<double[]> history = histories.computeIfAbsent(id, k -> new ArrayDeque<>());
        double[] last = history.peekLast();
        history.addLast(new double[]{System.currentTimeMillis(), x, y, z});
        while (history.size() > HISTORY_CAP) {
            history.pollFirst();
        }
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
                vy = Mth.clamp(vy, -0.15, 0.15);
                double[] prev = velocities.get(id);
                double[] smoothed = prev == null
                        ? new double[]{vx, vy, vz}
                        : new double[]{prev[0] * 0.5 + vx * 0.5, prev[1] * 0.5 + vy * 0.5, prev[2] * 0.5 + vz * 0.5};
                velocities.put(id, smoothed);
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

    private double[] arrivalProjection(LivingEntity entity, double leadTicks) {
        double[] vel = velocities.get(entity.getId());
        if (vel == null) {
            return new double[]{entity.getX(), entity.getY(), entity.getZ()};
        }
        double effLead = leadTicks;
        Double smear = smears.get(entity.getId());
        if (smear != null && smear > 0.05) {
            effLead = leadTicks * (1.0 - smear * 0.85);
        }
        return new double[]{
                entity.getX() + vel[0] * effLead,
                entity.getY() + vel[1] * effLead,
                entity.getZ() + vel[2] * effLead
        };
    }

    /** Our own arrival position (where the server will resolve us when the
     *  attack lands — our sent position plus our own small forward motion). */
    private double[] ownC(Minecraft client, double leadTicks) {
        double[] vel = velocities.get(OWN_ID);
        if (vel == null) {
            return new double[]{client.player.getX(), client.player.getY(), client.player.getZ()};
        }
        return new double[]{
                client.player.getX() + vel[0] * leadTicks,
                client.player.getY() + vel[1] * leadTicks,
                client.player.getZ() + vel[2] * leadTicks
        };
    }

    private double dist(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Arms SilentAim toward the server-side hitbox if the offset is small. */
    private void armQuantumAim(Minecraft client, double[] point) {
        if (client.player == null) return;
        double eyeY = client.player.getY() + client.player.getEyeHeight();
        double dx = point[0] - client.player.getX();
        double dy = point[1] + 0.9 - eyeY; // chest of the server-side hitbox
        double dz = point[2] - client.player.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 0.01) return;

        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = Math.toDegrees(Math.asin(-dy / dist));
        double yawOffset = Mth.wrapDegrees(yaw - client.player.getYRot());
        double pitchOffset = pitch - client.player.getXRot();
        double maxOffset = getDoubleSetting("aimOffset");
        if (Math.abs(yawOffset) > maxOffset || Math.abs(pitchOffset) > maxOffset) {
            return;
        }
        yaw += (RANDOM.nextDouble() - 0.5) * 1.6;
        pitch += (RANDOM.nextDouble() - 0.5) * 1.2;
        SilentAim.set((float) yaw, (float) pitch);
        aimArmed = true;
    }

    /** Performs the swing against the chosen target (the server's hit test
     *  runs against its own authoritative position — this is a vanilla attack). */
    private void swing(Minecraft client, LivingEntity target) {
        if (client.gameMode == null || client.player == null) return;
        client.player.swing(InteractionHand.MAIN_HAND);
        client.gameMode.attack(client.player, target);
    }

    // ── Quantum Collapse (dodge) ─────────────────────────────────

    private void trackEnemySwings(Minecraft client) {
        double trackRange = getDoubleSetting("dodgeRange") + 1.0;
        double rangeSq = trackRange * trackRange;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;
            if (living.swinging) {
                Integer last = enemySwings.get(living.getId());
                if (last == null || (tickCounter - last) >= 3) {
                    enemySwings.put(living.getId(), tickCounter);
                }
            }
        }
    }

    private void tickDodge(Minecraft client) {
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
        if (client.screen != null || !client.player.isAlive() || !client.player.onGround()) {
            return;
        }
        if (client.player.input == null
                || (client.player.input.forwardImpulse == 0.0F && client.player.input.leftImpulse == 0.0F)) {
            return;
        }
        double range = getDoubleSetting("dodgeRange");
        LivingEntity attacker = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            Integer swing = enemySwings.get(living.getId());
            if (swing == null || (tickCounter - swing) > 2) continue;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < bestDist) {
                bestDist = d;
                attacker = living;
            }
        }
        if (attacker == null) return;
        if (bestDist > range) return;
        if (ownA == null) return;
        if (dist(ownA, new double[]{attacker.getX(), attacker.getY(), attacker.getZ()}) > range + 0.4) return;
        if ((RANDOM.nextDouble() * 100.0) >= 70.0) return;

        // Collapse: hold movement 1–2 ticks, strafe away from the attacker.
        double dx = client.player.getX() - attacker.getX();
        double dz = client.player.getZ() - attacker.getZ();
        float yawRad = client.player.getYRot() * 0.017453292F;
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        dodgeDir = (dx * rightX + dz * rightZ) >= 0.0 ? 1 : -1;
        dodgeTicks = client.player.isSprinting() ? 1 : 1 + RANDOM.nextInt(2);
        dodgeTicksTotal = dodgeTicks;
    }

    // ── cleanup / lifecycle ───────────────────────────────────────

    private void cleanup(Minecraft client) {
        Iterator<Map.Entry<Integer, ArrayDeque<double[]>>> it = histories.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ArrayDeque<double[]>> e = it.next();
            int id = e.getKey();
            if (id == OWN_ID) continue;
            boolean stillHere = false;
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity.getId() == id && entity.isAlive()) {
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

    /** Central world-change/death hook: drain the dodge queue immediately. */
    @Override
    public void onWorldChange(Minecraft client) {
        flush(client);
        dodgeTicks = 0;
        dodgeTicksTotal = 0;
        dodgeDir = 0;
        dodgeCooldown = 0;
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
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

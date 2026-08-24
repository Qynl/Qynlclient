package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.PingTracker;
import com.qynl.client189.Setting;
import com.qynl.client189.WorldDraw;
import com.qynl.client189.ReflectionAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * HINDSIGHT — Server-Time Replay.
 *
 * <p>PHANTOM predicts the future. HINDSIGHT replays the past — and that is
 * exactly what the server checks. When your attack packet arrives, the
 * server's hit test rewinds the target (and your own position) by roughly
 * one ping and checks reach against <i>those</i> coordinates. Nobody builds
 * for that view; HINDSIGHT is the first module that lives in it.</p>
 *
 * <p><b>What you see:</b></p>
 * <ul>
 *   <li>a <b>cyan box at your own server-side position</b> — where the server
 *       thinks you are right now (on high ping it can be a full block from
 *       where your camera is),</li>
 *   <li>every enemy's <b>server-side hitbox</b> (green = the rewind-reach
 *       test passes, red = it doesn't) and a line from your server position
 *       to theirs — the actual server-side battlefield,</li>
 *   <li>the click fires exactly when the rewind-reach test crosses into
 *       reach — the precise moment the server's own check will pass. Unlike
 *       forward prediction it also handles <b>retreating</b> targets, because
 *       a retreating enemy was closer one ping ago.</li>
 * </ul>
 *
 * <p><b>Why it cannot be flagged:</b> every packet is an ordinary attack
 * whose reach check the server passes against its own authoritative
 * positions. HINDSIGHT only computes <i>when</i> to click from the server's
 * own model — there is no modified packet, no impossible distance, no
 * pattern. Wall check prevents attacks through solid blocks; the target must
 * be visually near and in front (pre-aim guard).</p>
 */
public class HindsightModule extends Module {
    private static final Random RANDOM = new Random();
    private static final int HISTORY_CAP = 120;   // samples (~12 s at 10/s)
    private static final int OWN_ID = Integer.MIN_VALUE;

    private static HindsightModule instance;

    /** id -> position history: {ms, x, y, z}. */
    private final Map<Integer, ArrayDeque<double[]>> histories = new HashMap<>();
    private final Map<Integer, Integer> lastSwingTick = new HashMap<>();

    private int tickCounter = 0;
    private boolean wasInReach = false;

    /** Best target chosen this tick (for click + HUD state). */
    private LivingEntity target;
    private double[] targetRewind;
    private boolean targetWillHit;

    public HindsightModule() {
        super("Hindsight", "Server-Time Replay — shows where the server thinks you and your enemies are, and clicks exactly when the server's own rewind-reach check will pass. Works on retreating targets too.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_F11);
        addSetting(Setting.options("mode",        "Mode",        "Both",   "Both", "Render", "Click"));
        addSetting(Setting.range("reach",         "Reach",       3.4,    2.0,  5.0, 0.1, "b"));
        addSetting(Setting.range("maxAngle",      "Max angle",   40.0,   30,   90,   5,  "\u00b0"));
        addSetting(Setting.range("chance",        "Chance",      85.0,    0,  100,   5,  "%"));
        addSetting(Setting.options("wallCheck",   "Wall check", "On",    "On",  "Off"));
        addSetting(Setting.options("showOwn",     "Own position","On",    "On",  "Off"));
        addSetting(Setting.options("showEnemies", "Enemy boxes", "On",    "On",  "Off"));
        addSetting(Setting.options("showLines",   "Server lines","On",    "On",  "Off"));
        addSetting(Setting.options("throughWalls","Through walls", "Off", "Off", "On"));
    }

    public static HindsightModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    // ── per-tick ────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        target = null;
        targetRewind = null;
        if (client.player == null || client.world == null || client.interactionManager == null) {
            wasInReach = false;
            return;
        }
        // Never click behind a GUI screen.
        if (client.currentScreen != null) {
            wasInReach = false;
            return;
        }

        tickCounter++;
        if (tickCounter % 2 == 0) {
            sample(OWN_ID, client.player.x, client.player.y, client.player.z);
            for (Entity entity : client.world.entities) {
                if (!isCandidate(client, entity)) continue;
                sample(entity.getEntityId(), entity.x, entity.y, entity.z);
            }
        }
        if (tickCounter % 60 == 0) cleanup(client);

        int ping = PingTracker.hasPing() ? PingTracker.getPingMs() : 100;
        long targetMs = System.currentTimeMillis() - ping;
        double[] own = rewind(histories.get(OWN_ID), targetMs);
        if (own == null) {
            wasInReach = false;
            return;
        }

        String mode = getStringSetting("mode");
        boolean doClick = "Both".equals(mode) || "Click".equals(mode);

        // Best target by rewind-reach distance, with pre-aim guards.
        double reach = getDoubleSetting("reach");
        double bestDist = Double.MAX_VALUE;
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

            double[] rw = rewind(histories.get(living.getEntityId()), targetMs);
            if (rw == null) rw = new double[]{living.x, living.y, living.z};
            double rdx = rw[0] - own[0];
            double rdy = rw[1] - own[1];
            double rdz = rw[2] - own[2];
            double rewindDist = Math.sqrt(rdx * rdx + rdy * rdy + rdz * rdz);
            if (rewindDist < bestDist) {
                bestDist = rewindDist;
                target = living;
                targetRewind = rw;
                targetWillHit = rewindDist <= reach;
            }
        }

        if (target == null) {
            wasInReach = false;
            return;
        }

        // Edge-triggered: click when the server-side check crosses into reach.
        boolean risingEdge = targetWillHit && !wasInReach;
        wasInReach = targetWillHit;

        if (!doClick || !risingEdge) return;
        // Mutual exclusion with the other predictive clickers.
        if (QynlModule.isActive() || AutoClickerModule.isActive()) return;
        if (!client.options.keyAttack.isPressed()) return;
        if ("On".equals(getStringSetting("wallCheck"))
                && !WorldDraw.hasLineOfSight(client,
                        target.x, target.y + target.getEyeHeight(), target.z)) {
            return;
        }
        Integer last = lastSwingTick.get(target.getEntityId());
        if (last != null && tickCounter - last < 6 + RANDOM.nextInt(5)) return;
        if ((RANDOM.nextDouble() * 100.0) >= getDoubleSetting("chance")) return;

        lastSwingTick.put(target.getEntityId(), tickCounter);
        ReflectionAccess.minecraftDoAttack(client);
    }

    // ── rendering ───────────────────────────────────────────────

    public static void render(float partialTicks) {
        if (instance == null || !instance.isEnabled()) return;
        String mode = instance.getStringSetting("mode");
        if ("Click".equals(mode)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int ping = PingTracker.hasPing() ? PingTracker.getPingMs() : 100;
        long targetMs = System.currentTimeMillis() - ping;
        double[] own = instance.rewind(instance.histories.get(OWN_ID), targetMs);
        if (own == null) return;

        double reach = instance.getDoubleSetting("reach");
        boolean ownBox = "On".equals(instance.getStringSetting("showOwn"));
        boolean boxes = "On".equals(instance.getStringSetting("showEnemies"));
        boolean lines = "On".equals(instance.getStringSetting("showLines"));
        boolean through = "On".equals(instance.getStringSetting("throughWalls"));

        double camX = client.player.prevX + (client.player.x - client.player.prevX) * partialTicks;
        double camY = client.player.prevY + (client.player.y - client.player.prevY) * partialTicks;
        double camZ = client.player.prevZ + (client.player.z - client.player.prevZ) * partialTicks;

        WorldDraw.begin(through);
        // Your own server-side position (cyan).
        if (ownBox) {
            WorldDraw.drawAABB(own[0] - 0.3, own[1], own[2] - 0.3,
                    own[0] + 0.3, own[1] + 1.8, own[2] + 0.3,
                    0.20f, 0.80f, 1.00f, 0.8f, camX, camY, camZ);
        }

        for (Entity entity : client.world.entities) {
            if (!instance.isCandidate(client, entity)) continue;
            LivingEntity living = (LivingEntity) entity;
            double[] rw = instance.rewind(instance.histories.get(living.getEntityId()), targetMs);
            if (rw == null) rw = new double[]{living.x, living.y, living.z};

            double dx = rw[0] - own[0];
            double dy = rw[1] - own[1];
            double dz = rw[2] - own[2];
            boolean willHit = Math.sqrt(dx * dx + dy * dy + dz * dz) <= reach;
            float r = willHit ? 0.30f : 0.95f;
            float g = willHit ? 0.90f : 0.35f;
            float b = willHit ? 0.50f : 0.35f;

            if (lines) {
                WorldDraw.line(own[0] - camX, own[1] + 0.9 - camY, own[2] - camZ,
                        rw[0] - camX, rw[1] + 0.9 - camY, rw[2] - camZ,
                        r, g, b, willHit ? 0.9f : 0.45f);
            }
            if (boxes) {
                Box box = living.getBoundingBox();
                double w = (box.maxX - box.minX) / 2.0;
                double h = box.maxY - box.minY;
                WorldDraw.drawAABB(rw[0] - w, rw[1], rw[2] - w, rw[0] + w, rw[1] + h, rw[2] + w,
                        r, g, b, willHit ? 0.85f : 0.5f, camX, camY, camZ);
            }
        }
        WorldDraw.end();
    }

    // ── helpers ─────────────────────────────────────────────────

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

    private void sample(int id, double x, double y, double z) {
        ArrayDeque<double[]> history = histories.computeIfAbsent(id, k -> new ArrayDeque<>());
        history.addLast(new double[]{System.currentTimeMillis(), x, y, z});
        while (history.size() > HISTORY_CAP) {
            history.pollFirst();
        }
    }

    /** Interpolated position of an entity {@code targetMs} ago, or null. */
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
                lastSwingTick.remove(id);
            }
        }
    }

    @Override
    public void onDisable() {
        target = null;
        targetRewind = null;
        wasInReach = false;
        histories.clear();
    }
}

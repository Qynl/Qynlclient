package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.PingTracker;
import com.qynl.client189.Setting;
import com.qynl.client189.QynlClient189;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * ReachAssist — extends reach with natural, humanized fluctuation, tuned to
 * stay inside the "ghost" range modern anti-cheat (Grim / Vulcan) tolerates.
 *
 * <p>The old defaults went up to +1.15 blocks, which is mathematically
 * impossible for any server with a reach check — that alone is what gets you
 * flagged. The per-mode bounds now keep the total reach between 3.01 and 3.35
 * blocks, which reads as connection jitter instead of reach abuse.</p>
 *
 * <p>On top of the small bonus, the optional <b>Silent Pack-Choke</b> applies
 * the backtrack principle: while you are in combat and closing on an enemy
 * 3.0–3.6 blocks away, outgoing movement packets are held for 1–2 server
 * ticks and then flushed together. The server resolves your position further
 * ahead than the opponent sees, so hits register from what looks like 3.5
 * blocks while the anti-cheat sees you mathematically inside 3.0. It is the
 * same fake-lag trick as {@link BlinkModule} and carries the same server
 * tolerance caveats.</p>
 */
public class ReachModule extends Module {
    private static final Random RANDOM = new Random();
    private static double currentBonus = 0.12;
    private static double minBonus = 0.05, maxBonus = 0.18;
    private double targetBonus = 0.12;
    private int walkTimer = 0;
    private int transitionTimer = 0;

    // ── Silent Pack-Choke state ─────────────────────────────────
    /** Hard cap on buffered movement packets before they are dropped. */
    private static final int MAX_CHOKE = 8;
    private static final Deque<Packet> chokeQueue = new ArrayDeque<>();
    private static boolean armed = false;
    private static int holdTicksLeft = 0;
    private static int cooldownTicks = 0;
    private static boolean flushing = false;

    public ReachModule() {
        super("Reach", "Extends reach with smooth, human-like fluctuation.", Category.COMBAT);
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("mode",        "Mode",        "Normal", "Subtle", "Normal", "Aggressive"));
        addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
        addSetting(Setting.options("choke",       "Pack choke",  "On",     "Off", "On"));
    }

    @Override
    public void onEnable() { applyBounds(); }

    @Override
    public void onTick(MinecraftClient client) {
        applyBounds();

        int interval;
        double flucRange;
        switch (getStringSetting("fluctuation")) {
            case "Low":  interval = 7; flucRange = 0.06; break;
            case "High": interval = 3; flucRange = 0.20; break;
            default:     interval = 5; flucRange = 0.12; break;
        }

        if (--walkTimer <= 0) {
            walkTimer = interval + RANDOM.nextInt(interval);
            targetBonus = minBonus + RANDOM.nextDouble() * (maxBonus - minBonus);
            transitionTimer = interval; // smooth transition over interval ticks
        }

        // Smoothly interpolate toward target for natural feel
        if (transitionTimer > 0) {
            transitionTimer--;
            double t = 1.0 - (double) transitionTimer / (walkTimer + 1);
            // Smoothstep-ish
            t = t * t * (3.0 - 2.0 * t);
            double prevBonus = currentBonus;
            currentBonus = prevBonus + (targetBonus - prevBonus) * t * 0.5;
        }

        currentBonus = MathHelper.clamp(currentBonus, minBonus, maxBonus);

        tickChoke(client);
    }

    /**
     * Per-mode reach bonus bounds. These keep the total reach inside the
     * ghost range (3.01–3.35 blocks) so no anti-cheat reach check can ever
     * call it impossible.
     */
    private void applyBounds() {
        switch (getStringSetting("mode")) {
            case "Subtle":     minBonus = 0.01; maxBonus = 0.08; break; // 3.01–3.08 (Grim safe)
            case "Aggressive": minBonus = 0.15; maxBonus = 0.35; break; // 3.15–3.35 (Vulcan/Matrix limit)
            default:           minBonus = 0.05; maxBonus = 0.18; break; // 3.05–3.18 (Hypixel safe)
        }
        currentBonus = MathHelper.clamp(currentBonus, minBonus, maxBonus);
        targetBonus = MathHelper.clamp(targetBonus, minBonus, maxBonus);
    }

    // ── Silent Pack-Choke ───────────────────────────────────────

    /** True while the choke is actively holding packets (used by other
     *  modules to avoid colliding with the choke window). */
    public static boolean isChokeArmed() {
        return armed && !flushing;
    }

    /** True while the choke is armed and the packet should be held back. */
    public static boolean shouldHoldPacket() {
        return armed && !flushing && !BlinkModule.isActive() && chokeQueue.size() < MAX_CHOKE;
    }

    /** Buffers one held movement packet (called from the send mixin). */
    public static void buffer(Packet packet) {
        if (chokeQueue.size() < MAX_CHOKE) {
            chokeQueue.addLast(packet);
        }
    }

    /** Sends every buffered packet through the normal send path. */
    public static void flush(MinecraftClient client) {
        if (client == null || client.getNetworkHandler() == null) {
            chokeQueue.clear();
            return;
        }
        flushing = true;
        try {
            Packet packet;
            while ((packet = chokeQueue.pollFirst()) != null) {
                client.getNetworkHandler().sendPacket(packet);
            }
        } finally {
            flushing = false;
        }
    }

    /**
     * Arms / maintains / flushes the choke window once per client tick.
     * While armed, outgoing movement packets are held 1–2 ticks and then
     * flushed together; a cooldown keeps the holds from being constant.
     */
    private void tickChoke(MinecraftClient client) {
        if (!"On".equals(getStringSetting("choke"))) {
            // Choke was toggled off — if it was mid-arm, drain any buffered
            // movement packets so stale coordinates never flush later.
            flush(client);
            armed = false;
            holdTicksLeft = 0;
            return;
        }
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            // Death/leave is handled centrally by onWorldChange; this guard
            // only keeps the choke from re-arming while dead.
            flush(client);
            armed = false;
            holdTicksLeft = 0;
            return;
        }
        // On high ping the held packets turn into a bigger server-side
        // catch-up jump — exactly the rubberband signature Grim's movement
        // check flags. The choke only buys reach when the connection is
        // tight enough to stay subtle.
        if (PingTracker.hasPing() && PingTracker.getPingMs() > 150) {
            armed = false;
            holdTicksLeft = 0;
            return;
        }

        if (armed) {
            if (--holdTicksLeft <= 0) {
                flush(client);
                armed = false;
                cooldownTicks = 8 + RANDOM.nextInt(7); // 8–14 tick gap between chokes
            }
        } else if (cooldownTicks > 0) {
            cooldownTicks--;
        } else if (inChokeWindow(client)) {
            armed = true;
            holdTicksLeft = 1 + RANDOM.nextInt(2); // 1–2 server ticks (50–100 ms)
        }
    }

    /**
     * True while the player is in combat and closing on an enemy that sits
     * 3.0–3.6 blocks away — the window where the choke buys reach.
     */
    private boolean inChokeWindow(MinecraftClient client) {
        if (!client.options.keyAttack.isPressed()) return false; // in combat
        // Never choke mid-air — a frozen position while falling is the
        // strongest position-desync signature there is (and the choke buys
        // nothing airborne anyway).
        if (!client.player.onGround) return false;

        LivingEntity target = findChokeTarget(client);
        if (target == null) return false;

        double dx = target.x - client.player.x;
        double dy = target.y - client.player.y;
        double dz = target.z - client.player.z;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < 3.0 * 3.0 || distSq > 3.6 * 3.6) return false;

        // Only while running toward the target (spec: "wenn du auf den
        // Gegner zuläufst") — never while backing away.
        if (client.player.input == null || client.player.input.movementForward <= 0.0F) return false;
        // Never choke while sprinting: the held packets cover more distance
        // at sprint speed, so the flush becomes a visibly larger catch-up
        // jump — the exact rubberband signature Grim's movement check flags.
        // Walking pace keeps the desync small enough to read as jitter.
        if (client.player.isSprinting()) return false;
        double vx = client.player.x - client.player.prevX;
        double vz = client.player.z - client.player.prevZ;
        return vx * dx + vz * dz > 0;
    }

    /** Nearest attackable enemy within 4 blocks. */
    private LivingEntity findChokeTarget(MinecraftClient client) {
        double range = 4.0;
        double rangeSq = range * range;
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity || living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;

            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq && distSq <= rangeSq) {
                bestDistSq = distSq;
                best = living;
            }
        }
        return best;
    }

    /** Central world-change/death hook: drain held packets immediately. */
    @Override
    public void onWorldChange(MinecraftClient client) {
        flush(client);
        armed = false;
        holdTicksLeft = 0;
    }

    @Override
    public void onDisable() {
        // Never leave the player frozen: flush whatever is buffered.
        MinecraftClient client = MinecraftClient.getInstance();
        flush(client);
        armed = false;
        holdTicksLeft = 0;
    }

    public static double currentBonus() { return currentBonus; }
    public static boolean isActive() {
        QynlClient189 qynl = QynlClient189.getInstance();
        return qynl != null && qynl.getModuleManager().isEnabled("Reach");
    }
}

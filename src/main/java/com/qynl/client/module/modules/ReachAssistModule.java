package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.ModuleManager;
import com.qynl.client.module.Setting;
import com.qynl.client.util.PingTracker;
import com.qynl.client.util.TeamHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * ReachAssist — extends reach with natural, humanized fluctuation, tuned to
 * stay inside the "ghost" range modern anti-cheat (Grim / Vulcan) tolerates.
 *
 * <p>The per-mode bounds keep the total reach between 3.01 and 3.35 blocks,
 * which reads as connection jitter instead of reach abuse.</p>
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
public class ReachAssistModule extends Module {
    private static final Random RANDOM = new Random();
    private static double currentBonus = 0.12;
    private static double minBonus = 0.05, maxBonus = 0.18;
    private double targetBonus = 0.12;
    private int walkTimer = 0;
    private int transitionTimer = 0;

    // ── Silent Pack-Choke state ─────────────────────────────────
    private static final int MAX_CHOKE = 8;
    private static final Deque<Packet<?>> chokeQueue = new ArrayDeque<>();
    private static boolean armed = false;
    private static int holdTicksLeft = 0;
    private static int cooldownTicks = 0;
    private static boolean flushing = false;

    public ReachAssistModule() {
        super("ReachAssist",
                "Extends reach with smooth, human-like fluctuation. Optional Silent Pack-Choke for ghost servers.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_I);
        addSetting(Setting.options("mode",        "Mode",        "Normal", "Subtle", "Normal", "Aggressive"));
        addSetting(Setting.options("fluctuation", "Fluctuation", "Medium", "Low", "Medium", "High"));
        // Pack choke is OFF by default: on LAN/friends servers the 1-2 tick
        // packet hold just reads as rubberbanding and made Reach feel broken.
        // It only pays off on laggy ghost servers with an anti-cheat — turn it
        // on there explicitly.
        addSetting(Setting.options("choke",       "Pack choke",  "Off",    "Off", "On"));
    }

    @Override
    public void onEnable() { applyBounds(); }

    @Override
    public void onTick(Minecraft client) {
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
            transitionTimer = interval;
        }

        // Smoothly interpolate toward target for natural feel
        if (transitionTimer > 0) {
            transitionTimer--;
            double t = 1.0 - (double) transitionTimer / (walkTimer + 1);
            t = t * t * (3.0 - 2.0 * t); // smoothstep
            double prevBonus = currentBonus;
            currentBonus = prevBonus + (targetBonus - prevBonus) * t * 0.5;
        }

        currentBonus = Mth.clamp(currentBonus, minBonus, maxBonus);

        tickChoke(client);
    }

    /**
     * Per-mode reach bonus bounds. These keep the total reach inside the
     * ghost range (3.01–3.35 blocks) so no anti-cheat reach check can ever
     * call it impossible.
     */
    private void applyBounds() {
        switch (getStringSetting("mode")) {
            case "Subtle":     minBonus = 0.05; maxBonus = 0.15; break; // 3.05–3.15 (anti-cheat safe)
            // Friends-server use: no anti-cheat, so the reach is meant to be
            // clearly felt — the old +0.08–0.22 blocks (~20 cm) was
            // imperceptible and read as "reach doesn't work". The vanilla
            // server's own interact tolerance allows up to ~6 blocks.
            case "Aggressive": minBonus = 0.80; maxBonus = 1.30; break; // 3.80–4.30 (1.8-client feel)
            default:           minBonus = 0.35; maxBonus = 0.65; break; // 3.35–3.65 (clearly felt)
        }
        currentBonus = Mth.clamp(currentBonus, minBonus, maxBonus);
        targetBonus = Mth.clamp(targetBonus, minBonus, maxBonus);
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
    public static void buffer(Packet<?> packet) {
        if (chokeQueue.size() < MAX_CHOKE) {
            chokeQueue.addLast(packet);
        }
    }

    /** Sends every buffered packet through the normal send path. */
    public static void flush(Minecraft client) {
        if (client == null || client.getConnection() == null
                || client.getConnection().getConnection() == null) {
            chokeQueue.clear();
            return;
        }
        flushing = true;
        try {
            Packet<?> packet;
            while ((packet = chokeQueue.pollFirst()) != null) {
                client.getConnection().getConnection().send(packet);
            }
        } finally {
            flushing = false;
        }
    }

    /**
     * Arms / maintains / flushes the choke window once per client tick.
     */
    private void tickChoke(Minecraft client) {
        if (!"On".equals(getStringSetting("choke"))) {
            flush(client);
            armed = false;
            holdTicksLeft = 0;
            return;
        }
        if (client.player == null || client.level == null || !client.player.isAlive()) {
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
            // Sprinting covers more distance per held tick — clamp to 1 tick
            // then so the flush never becomes a visible catch-up jump.
            holdTicksLeft = client.player.isSprinting()
                    ? 1 : 1 + RANDOM.nextInt(2);
        }
    }

    /**
     * True while the player is in combat and closing on an enemy that sits
     * 3.0–3.6 blocks away — the window where the choke buys reach.
     */
    private boolean inChokeWindow(Minecraft client) {
        if (!client.options.keyAttack.isDown()) return false; // in combat
        if (!client.player.onGround()) return false;

        LivingEntity target = findChokeTarget(client);
        if (target == null) return false;

        double dx = target.getX() - client.player.getX();
        double dy = target.getY() - client.player.getY();
        double dz = target.getZ() - client.player.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        // Wider window (2.5–4.2 blocks) so the choke actually engages in real
        // fights — the old 3.0–3.6 band almost never happened while chasing.
        if (distSq < 2.5 * 2.5 || distSq > 4.2 * 4.2) return false;

        // Only while running toward the target — never while backing away.
        if (client.player.input == null || client.player.input.forwardImpulse <= 0.0F) return false;
        double vx = client.player.getX() - client.player.xOld;
        double vz = client.player.getZ() - client.player.zOld;
        return vx * dx + vz * dz > 0;
    }

    /** Nearest attackable enemy within 4 blocks. */
    private LivingEntity findChokeTarget(Minecraft client) {
        double rangeSq = 4.0 * 4.0;
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof Monster || living instanceof Player)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            if (living instanceof Player && TeamHelper.sameTeam(client, client.player, (Player) living)) continue;

            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
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
    public void onWorldChange(Minecraft client) {
        flush(client);
        armed = false;
        holdTicksLeft = 0;
    }

    @Override
    public void onDisable() {
        // Never leave the player frozen: flush whatever is buffered.
        Minecraft client = Minecraft.getInstance();
        flush(client);
        armed = false;
        holdTicksLeft = 0;
    }

    public static double currentBonus() { return currentBonus; }
    public static boolean isActive() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        QynlClient qynl = QynlClient.getInstance();
        if (qynl == null) return false;
        ModuleManager modules = qynl.getModuleManager();
        return modules != null && modules.isEnabled("ReachAssist");
    }
}

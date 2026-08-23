package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.KeyBindingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * BlockHit — Vape-Lite-grade sword blocking, tuned to look like a real player.
 *
 * <p>Two triggers (mode: Reactive / Rhythm / Both):</p>
 * <ul>
 *   <li><b>Reactive</b> — blocks when a nearby enemy swings at you. Swing
 *       detection is per-entity with a recency window, and the block only
 *       starts after a randomized human reaction delay.</li>
 *   <li><b>Rhythm</b> — re-blocks after <i>your own</i> swings while you're
 *       attacking an enemy in range, the way real blockhitters do. The block
 *       is released on the tick your swing starts, so attacks land unblocked
 *       (post-attack blocking), then it comes back up.</li>
 * </ul>
 *
 * <p>Anti-cheat hardening:</p>
 * <ul>
 *   <li>Never blocks at air — every block is tied to a swing or an enemy in
 *       melee range.</li>
 *   <li>Sprint is dropped one tick before a block so the server sees correct
 *       block-while-not-sprinting physics (Intave trick).</li>
 *   <li>All timings are randomized (reaction, hold, chance) with a minimum
 *       cooldown between block cycles — no fixed clocks to pattern-match.</li>
 *   <li>Sword/axe only; never interferes with manual item use (eating,
 *       drinking); releases instantly when the situation ends.</li>
 * </ul>
 */
public class BlockHitModule extends Module {
    private static final Random RANDOM = new Random();
    private static BlockHitModule instance;

    // Per-entity swing tracking: entityId -> tick of their last swing start.
    private final Map<Integer, Integer> enemySwings = new HashMap<>();
    private int tickCounter = 0;

    // State machine
    private static final int IDLE = 0, REACTING = 1, BLOCKING = 2, COOLDOWN = 3;
    private int state = IDLE;
    private int stateTicks = 0;
    private int blockTicksRemaining = 0;

    // Our own swing detection (handSwinging rising edge).
    private boolean wasSwinging = false;

    public BlockHitModule() {
        super("BlockHit", "Auto-blocks with your sword like a real player — reactive to enemy swings, rhythmic on your own.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_NONE);
        addSetting(Setting.options("mode",       "Mode",        "Both",   "Reactive", "Rhythm", "Both"));
        addSetting(Setting.range("reactionMs",   "Reaction",    110.0,    60,   250,  10, "ms"));
        addSetting(Setting.range("blockTime",    "Block time",    4.0,    1,     8,   1,  "t"));
        addSetting(Setting.range("range",        "Range",        3.5,    2.0,   5.0, 0.5, "b"));
        addSetting(Setting.range("swingWindow",  "Swing detect",  2.0,    1,     4,   1,  "t"));
        addSetting(Setting.range("chance",       "Chance",       80.0,    0,   100,   5,  "%"));
    }

    public static BlockHitModule getInstance() { return instance; }
    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    // ── per-tick ────────────────────────────────────────────────

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            reset(client);
            return;
        }
        if (!client.player.isAlive() || client.currentScreen != null) {
            reset(client);
            return;
        }
        if (!holdsWeapon(client)) {
            reset(client);
            return;
        }

        tickCounter++;
        if (tickCounter % 40 == 0) cleanupSwingHistory();

        // Never fight the player's own right-click (eating, drinking, manual block).
        if (client.player.isUsingItem() && state != BLOCKING) {
            return;
        }

        // ── Our own swing: the attack must land unblocked ──────
        boolean swingingNow = client.player.handSwinging;
        if (swingingNow && !wasSwinging && state == BLOCKING) {
            // Post-attack blocking: release exactly when our swing starts,
            // then let Rhythm re-block a tick later.
            releaseUse(client);
            state = COOLDOWN;
            stateTicks = 1;
            blockTicksRemaining = 0;
        }
        wasSwinging = swingingNow;

        trackEnemySwings(client);

        // ── state machine ──────────────────────────────────────
        switch (state) {
            case BLOCKING:
                // Keep holding; release early if the fight moved away.
                if (!enemyNearby(client, getDoubleSetting("range") + 1.0)) {
                    releaseUse(client);
                    state = COOLDOWN;
                    stateTicks = 2 + RANDOM.nextInt(2);
                    blockTicksRemaining = 0;
                    return;
                }
                pressUse(client);
                blockTicksRemaining--;
                if (blockTicksRemaining <= 0) {
                    releaseUse(client);
                    state = COOLDOWN;
                    stateTicks = 2 + RANDOM.nextInt(2);
                }
                return;

            case REACTING:
                if (--stateTicks <= 0) {
                    if (rollChance()) {
                        startBlock(client);
                    } else {
                        state = COOLDOWN;
                        stateTicks = 2 + RANDOM.nextInt(3);
                    }
                }
                return;

            case COOLDOWN:
                if (--stateTicks <= 0) state = IDLE;
                return;

            default: // IDLE
                String mode = getStringSetting("mode");
                boolean enemyNear = enemyNearby(client, getDoubleSetting("range"));
                boolean attacking = client.options.keyAttack.isPressed();

                // Rhythm: re-block after our own swings while fighting.
                if (enemyNear && attacking && ("Rhythm".equals(mode) || "Both".equals(mode))) {
                    // Only start a block right after a swing, not mid-swing.
                    if (client.player.handSwinging) {
                        if (rollChance()) {
                            startBlock(client);
                        } else {
                            state = COOLDOWN;
                            stateTicks = 1 + RANDOM.nextInt(2);
                        }
                    }
                }

                // Reactive: enemy just swung at us within the window.
                if (state == IDLE && enemyNear
                        && ("Reactive".equals(mode) || "Both".equals(mode))
                        && enemyJustSwung(client)) {
                    int reactionTicks = Math.max(1, (int) (getDoubleSetting("reactionMs") / 50.0)
                            + RANDOM.nextInt(3) - 1);
                    state = REACTING;
                    stateTicks = reactionTicks;
                }
                return;
        }
    }

    // ── block control ───────────────────────────────────────────

    /** Starts a block hold with randomized duration and the sprint reset. */
    private void startBlock(MinecraftClient client) {
        // Intave: drop sprint a tick before blocking so the server sees the
        // correct physics (you can't block while sprinting).
        if (client.player.isSprinting()) {
            client.player.setSprinting(false);
        }
        int hold = (int) getDoubleSetting("blockTime");
        hold += RANDOM.nextInt(3) - 1; // humanize ±1 tick
        if (hold < 1) hold = 1;
        blockTicksRemaining = hold;
        pressUse(client);
        state = BLOCKING;
        stateTicks = 0;
    }

    private void pressUse(MinecraftClient client) {
        if (!client.options.keyUse.isPressed()) {
            ((KeyBindingAccessor) client.options.keyUse).setPressed(true);
        }
    }

    private void releaseUse(MinecraftClient client) {
        if (client.options.keyUse.isPressed()) {
            ((KeyBindingAccessor) client.options.keyUse).setPressed(false);
        }
    }

    private boolean rollChance() {
        return (RANDOM.nextDouble() * 100.0) < getDoubleSetting("chance");
    }

    // ── detection ───────────────────────────────────────────────

    private boolean holdsWeapon(MinecraftClient client) {
        return client.player.getMainHandStack().getItem() instanceof SwordItem
                || client.player.getMainHandStack().getItem() instanceof AxeItem;
    }

    /** True if any non-friend enemy is within {@code range} blocks. */
    private boolean enemyNearby(MinecraftClient client, double range) {
        if (client.player == null || client.world == null) return false;
        double rangeSq = range * range;
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
        }
        return false;
    }

    /** True if an enemy in range swung within the configured recency window. */
    private boolean enemyJustSwung(MinecraftClient client) {
        int window = (int) getDoubleSetting("swingWindow");
        double rangeSq = getDoubleSetting("range") * getDoubleSetting("range");
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity) && !(living instanceof PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

            Integer last = enemySwings.get(living.getEntityId());
            if (last != null && (tickCounter - last) <= window) return true;
        }
        return false;
    }

    /** Records when nearby entities start a swing (rising edge of handSwinging). */
    private void trackEnemySwings(MinecraftClient client) {
        double trackRange = getDoubleSetting("range") + 1.5;
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

    private void cleanupSwingHistory() {
        Iterator<Map.Entry<Integer, Integer>> it = enemySwings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            if (tickCounter - e.getValue() > 60) it.remove();
        }
    }

    // ── cleanup ─────────────────────────────────────────────────

    private void reset(MinecraftClient client) {
        if (client != null && client.options != null && state == BLOCKING) {
            releaseUse(client);
        }
        state = IDLE;
        stateTicks = 0;
        blockTicksRemaining = 0;
        wasSwinging = false;
    }

    @Override
    public void onDisable() {
        reset(MinecraftClient.getInstance());
    }
}

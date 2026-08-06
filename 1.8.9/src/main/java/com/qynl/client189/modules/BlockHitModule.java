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
import net.minecraft.item.SwordItem;
import org.lwjgl.input.Keyboard;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class BlockHitModule extends Module {
    private static final Random RANDOM = new Random();

    // Per-entity swing tracking: entityId → tick when they last swung
    private final Map<Integer, Integer> swingTicks = new HashMap<>();
    private final Map<Integer, Integer> swingCounts = new HashMap<>();

    private int tickCounter = 0;
    private int blockTicksRemaining = 0;
    private int attackGateTicks = 0;
    private int rhythmPhase = 0; // 0=idle, 1=attacked, 2=blocking, 3=releasing
    private int rhythmTimer = 0;
    private int lastEnemyId = -1;

    public BlockHitModule() {
        super("BlockHit", "1.8.9 sword blocking — predicts swings, blocks on reaction, releases fast for counter-attacks.", Category.ASSIST);
        bindKey(Keyboard.KEY_F);
        addSetting(Setting.range("reactionMs",  "Reaction",   60.0, 30, 150, 10, "ms"));
        addSetting(Setting.range("blockTicks",  "Block time",  2.0,  1,   5,  1, "t"));
        addSetting(Setting.range("maxDist",     "Max range",   3.5, 2.0, 6.0, 0.5, "b"));
        addSetting(Setting.options("rhythm",     "Rhythm",     "On", "On", "Off"));
        addSetting(Setting.range("rhythmCps",   "Rhythm CPS",  5.0,  3,  8,  1));
    }

    // ── per-tick ────────────────────────────────────────────────
    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) { reset(); return; }
        if (!client.player.isAlive() || client.currentScreen != null) { reset(); return; }
        if (!(client.player.getMainHandStack().getItem() instanceof SwordItem)) { reset(); return; }

        tickCounter++;

        // Clean old swing entries every 40 ticks
        if (tickCounter % 40 == 0) cleanupSwingHistory();

        // Track swings of nearby entities
        trackEntitySwings(client);

        // If we have an active block hold, maintain it
        if (blockTicksRemaining > 0) {
            blockTicksRemaining--;
            pressUse(client);
            // If we just finished blocking, start release cooldown
            if (blockTicksRemaining == 0) {
                releaseUse(client);
                attackGateTicks = 1; // allow immediate counter-attack
            }
            return;
        }

        // Cooldown after releasing block — don't re-block immediately
        if (attackGateTicks > 0) {
            attackGateTicks--;
            releaseUse(client);
            return;
        }

        // Not attacking? Release and reset
        if (!client.options.keyAttack.isPressed()) {
            releaseUse(client);
            rhythmPhase = 0;
            rhythmTimer = 0;
            return;
        }

        // Already using item? Don't interfere
        if (client.player.isUsingItem()) return;

        // ── Smart block decision ─────────────────────────────
        LivingEntity threat = findBestThreat(client);
        if (threat != null) {
            lastEnemyId = threat.getEntityId();

            // Did this enemy just swing?
            Integer lastSwing = swingTicks.get(threat.getEntityId());
            boolean enemyJustSwung = lastSwing != null && (tickCounter - lastSwing) <= 2;
            boolean enemyInRange = isInMeleeRange(client, threat);

            if (enemyJustSwung && enemyInRange) {
                // Reactive block — enemy just swung, block NOW
                triggerBlock(client);
                return;
            }
        }

        // Rhythmic blockhitting when in melee combat
        if ("On".equals(getStringSetting("rhythm")) && lastEnemyId >= 0) {
            handleRhythm(client);
        } else {
            releaseUse(client);
        }
    }

    // ── rhythmic blockhitting ──────────────────────────────────
    private void handleRhythm(MinecraftClient client) {
        int cps = (int) getDoubleSetting("rhythmCps");
        int cycleLength = Math.max(2, 20 / cps); // ticks per full attack-block cycle
        int blockPortion = Math.max(1, cycleLength / 4); // block for ~25% of cycle

        rhythmTimer++;
        if (rhythmTimer >= cycleLength) rhythmTimer = 0;

        if (rhythmTimer < blockPortion) {
            // Block phase
            pressUse(client);
        } else {
            // Attack phase
            releaseUse(client);
        }
    }

    // ── trigger a reactive block ────────────────────────────────
    private void triggerBlock(MinecraftClient client) {
        int hold = (int) getDoubleSetting("blockTicks");
        // Add slight randomization for human feel
        hold += RANDOM.nextInt(3) - 1;
        if (hold < 1) hold = 1;
        blockTicksRemaining = hold;
        pressUse(client);
    }

    // ── threat detection ────────────────────────────────────────
    private LivingEntity findBestThreat(MinecraftClient client) {
        double maxDist = getDoubleSetting("maxDist");
        double maxDistSq = maxDist * maxDist;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof MobEntity || living instanceof PlayerEntity)) continue;

            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDistSq) continue;

            double dist = Math.sqrt(distSq);

            // Check if this entity is looking at / facing us
            float yaw = living.yaw * 0.017453292F;
            double lx = -Math.sin(yaw);
            double lz = Math.cos(yaw);
            double dotToUs = (lx * dx + lz * dz) / Math.max(0.01, dist);

            // Prefer enemies that are: close, facing us, recently swung
            double score = dist;

            Integer lastSwing = swingTicks.get(living.getEntityId());
            if (lastSwing != null) {
                int ticksSinceSwing = tickCounter - lastSwing;
                // Recently swung = more dangerous
                if (ticksSinceSwing <= 5) score -= 3.0;
                else if (ticksSinceSwing <= 10) score -= 1.5;
            }

            // Facing us = more dangerous
            if (dotToUs > 0.3) score -= 1.0;
            if (dotToUs > 0.7) score -= 1.5;

            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }
        return best;
    }

    // ── swing tracking ──────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private void trackEntitySwings(MinecraftClient client) {
        double maxDist = getDoubleSetting("maxDist") + 2.0;
        for (Entity entity : client.world.entities) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;

            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz > (maxDist * maxDist)) continue;

            if (living.handSwinging) {
                Integer last = swingTicks.get(living.getEntityId());
                // Only count as a new swing if enough ticks have passed
                if (last == null || (tickCounter - last) >= 3) {
                    swingTicks.put(living.getEntityId(), tickCounter);
                    Integer cnt = swingCounts.getOrDefault(living.getEntityId(), 0);
                    swingCounts.put(living.getEntityId(), cnt + 1);
                }
            }
        }
    }

    private void cleanupSwingHistory() {
        Iterator<Map.Entry<Integer, Integer>> it = swingTicks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> e = it.next();
            if (tickCounter - e.getValue() > 60) {
                it.remove();
                swingCounts.remove(e.getKey());
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────
    private boolean isInMeleeRange(MinecraftClient client, LivingEntity target) {
        double dx = target.x - client.player.x;
        double dy = target.y - client.player.y;
        double dz = target.z - client.player.z;
        return (dx * dx + dy * dy + dz * dz) <= 4.5 * 4.5;
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

    private void reset() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.options != null) releaseUse(client);
        blockTicksRemaining = 0;
        attackGateTicks = 0;
        rhythmPhase = 0;
        rhythmTimer = 0;
    }

    @Override
    public void onDisable() { reset(); }
}

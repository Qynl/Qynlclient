package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import org.lwjgl.glfw.GLFW;

/**
 * BlockHit — Vape-Lite-grade auto-blocking.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>Reactive</b> — blocks when an enemy swings at you (human reaction delay).</li>
 *   <li><b>Rhythm</b> — re-blocks after your own swings with attacks landing
 *       unblocked (post-attack blocking). Sprint-reset before every block.</li>
 * </ul>
 *
 * <p>Only works with a sword or axe in hand, never blocks at air,
 * never touches manual item use.</p>
 */
public class BlockHitModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();
    private static BlockHitModule instance;

    private boolean forcingBlock = false;
    private int blockTimer = 0;
    private int cooldownTicks = 0;
    private int reactionTicks = 0;
    private boolean swungThisTick = false;
    private int postAttackTicks = 0;

    public BlockHitModule() {
        super("BlockHit", "Auto-blocks when enemy attacks (Reactive) or after your swings (Rhythm).",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("mode", "Mode", "Reactive", "Reactive", "Rhythm"));
        addSetting(Setting.range("reactionMs", "Reaction delay", 150.0, 50, 300, 10, "ms"));
        addSetting(Setting.range("blockTicks", "Block duration", 3.0, 2, 8, 1, "t"));
        addSetting(Setting.range("chance", "Chance", 80.0, 50, 100, 5, "%"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }
        if (client.player.isDeadOrDying()) {
            reset();
            return;
        }

        // Must hold a sword or axe
        ItemStack held = client.player.getMainHandItem();
        boolean hasWeapon = held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem;
        if (!hasWeapon) {
            reset();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        if (reactionTicks > 0) {
            reactionTicks--;
        }

        // Detect our own swing
        boolean nowSwinging = client.options.keyAttack.isDown();
        if (!swungThisTick && nowSwinging && client.player.getAttackStrengthScale(0.0F) >= 0.9F) {
            swungThisTick = true;
            postAttackTicks = 2; // window after swing to re-block
        }
        if (!nowSwinging) {
            swungThisTick = false;
        }
        if (postAttackTicks > 0) postAttackTicks--;

        // While blocking, count down
        if (forcingBlock) {
            if (blockTimer > 0) {
                blockTimer--;
                if (!client.options.keyUse.isDown()) {
                    client.options.keyUse.setDown(true);
                }
                return;
            }
            releaseBlock(client);
            cooldownTicks = 3 + RANDOM.nextInt(5);
            return;
        }

        if (cooldownTicks > 0) return;

        // Don't interfere with player's own right-click
        if (client.options.keyUse.isDown() || client.player.isUsingItem()) return;

        double chance = getDoubleSetting("chance") / 100.0;
        String mode = getStringSetting("mode");

        if ("Reactive".equals(mode)) {
            // Block when enemy is about to hit us
            LivingEntity enemy = findAttackingEnemy(client);
            if (enemy == null) return;

            if (reactionTicks > 0) return;
            if (RANDOM.nextDouble() > chance) return;

            startBlock(client);
            reactionTicks = (int) Math.round(getDoubleSetting("reactionMs") / 50.0) + RANDOM.nextInt(3);
        } else {
            // Rhythm: re-block after our own swings
            if (postAttackTicks <= 0) return;
            if (RANDOM.nextDouble() > chance) return;

            // Only rhythm-block when an enemy is in range
            LivingEntity enemy = findNearbyEnemy(client);
            if (enemy == null) return;

            startBlock(client);
        }
    }

    private void startBlock(Minecraft client) {
        // Sprint-reset before block
        if (client.player.isSprinting()) {
            client.player.setSprinting(false);
        }
        client.options.keyUse.setDown(true);
        forcingBlock = true;
        blockTimer = (int) getDoubleSetting("blockTicks") + RANDOM.nextInt(3) - 1;
        if (blockTimer < 2) blockTimer = 2;
    }

    private void releaseBlock(Minecraft client) {
        if (client.options != null) client.options.keyUse.setDown(false);
        forcingBlock = false;
        blockTimer = 0;
    }

    private void reset() {
        Minecraft client = Minecraft.getInstance();
        releaseBlock(client);
        reactionTicks = 0;
        cooldownTicks = 0;
        postAttackTicks = 0;
        swungThisTick = false;
    }

    private LivingEntity findAttackingEnemy(Minecraft client) {
        var player = client.player;
        var box = player.getBoundingBox().inflate(3.5);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (var entity : client.level.getEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive()
                        && (e instanceof Monster || e instanceof Player) && e != player)) {
            if (!(entity instanceof LivingEntity living)) continue;
            double dist = living.distanceTo(player);
            if (dist > 3.5) continue;

            var toPlayer = player.position().subtract(living.position()).normalize();
            double dot = living.getLookAngle().dot(toPlayer);

            // Close + facing us + swinging = attacking
            if (dist <= 3.0 && dot > 0.3 && living.swinging && dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    private LivingEntity findNearbyEnemy(Minecraft client) {
        var player = client.player;
        var box = player.getBoundingBox().inflate(4.0);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (var entity : client.level.getEntities(player, box,
                e -> e instanceof LivingEntity && e.isAlive()
                        && (e instanceof Monster || e instanceof Player) && e != player)) {
            if (!(entity instanceof LivingEntity living)) continue;
            double dist = living.distanceTo(player);
            if (dist <= 4.0 && dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    public static boolean isActive() {
        return instance != null && instance.isEnabled();
    }

    @Override
    public void onDisable() {
        reset();
    }
}
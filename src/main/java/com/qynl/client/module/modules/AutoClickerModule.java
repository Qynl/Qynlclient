package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * AutoClicker — clicks for you with human-like timing variance.
 *
 * <p><b>Block Hit</b> (Vape-style): when enabled and holding a shield, the
 * clicker re-blocks right after every swing using post-attack blocking — the
 * block is released a tick before the swing so the attack registers
 * unblocked, then pressed again immediately after. Hold duration is
 * randomized for a human feel.</p>
 */
public class AutoClickerModule extends Module {
    private static AutoClickerModule instance;
    private final RandomSource random = RandomSource.create();
    private int clickTicks = 0;
    private int nextInterval = 4;
    private int burstClicks = 0;
    private int burstPause = 0;
    private double currentCps = 7.0;
    private int cpsWalkTimer = 0;

    /** Human start reaction: random beat before clicking on first key press. */
    private boolean wasHeld = false;
    private int startDelayTicks = 0;

    /** Block Hit state. */
    private int blockTicksRemaining = 0;

    public AutoClickerModule() {
        super("AutoClicker",
                "Clicks for you with human-like timing variance and optional Block Hit.",
                Category.COMBAT);
        instance = this;
        bindKey(GLFW.GLFW_KEY_G);
        addSetting(Setting.range("cps",        "Target CPS", 9.0, 5, 16, 1));
        addSetting(Setting.range("jitter",     "Jitter",      8.0, 0, 20, 1, "%"));
        addSetting(Setting.options("pattern",  "Pattern",    "Steady", "Steady", "Burst"));
        addSetting(Setting.options("blockhit", "Block Hit",  "Off",    "Off",    "On"));
        addSetting(Setting.range("blockChance","Block chance", 100.0, 0, 100, 5, "%"));
        addSetting(Setting.range("blockTicks", "Block time",    4.0,  1,   8,  1, "t"));
        addSetting(Setting.range("blockRange", "Block range",   3.5,  2.0, 5.0, 0.5, "b"));
    }

    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            resetState(client);
            return;
        }
        if (client.screen != null || !client.options.keyAttack.isDown()) {
            resetState(client);
            return;
        }
        // Never click while the player is eating, drinking or using an item —
        // attacking would cancel the use and the pattern is suspicious.
        if (client.player.isUsingItem()) {
            if (blockTicksRemaining > 0) {
                blockTicksRemaining = 0;
                releaseUse(client);
            }
            return;
        }

        // Humans don't start clicking the instant they press the button —
        // wait a short random beat on the first press (100–300 ms).
        if (!wasHeld) {
            wasHeld = true;
            startDelayTicks = 2 + random.nextInt(5);
            clickTicks = 0;
            return;
        }
        if (startDelayTicks > 0) {
            startDelayTicks--;
            return;
        }

        // The standalone BlockHit module takes over blocking when active.
        boolean blockhit = "On".equals(getStringSetting("blockhit")) && !BlockHitModule.isActive();
        boolean holdingShield = holdingShield(client.player.getMainHandItem())
                || holdingShield(client.player.getOffhandItem());

        // Maintain an active block hold from the previous swing.
        if (blockhit && holdingShield && blockTicksRemaining > 0) {
            blockTicksRemaining--;
            if (blockTicksRemaining > 0) {
                pressUse(client);
            } else {
                releaseUse(client);
            }
        } else if (blockTicksRemaining > 0) {
            blockTicksRemaining = 0;
            releaseUse(client);
        }

        // Slow random walk of the target CPS.
        cpsWalkTimer++;
        if (cpsWalkTimer >= 40) {
            cpsWalkTimer = 0;
            double targetCps = getDoubleSetting("cps");
            currentCps = Math.max(2, Math.min(16, currentCps + (random.nextDouble() - 0.5) * 1.0));
            currentCps = currentCps + (targetCps - currentCps) * 0.2;
        }

        // Burst mode: click 2-4 times fast, then pause briefly.
        if ("Burst".equals(getStringSetting("pattern")) && burstPause > 0) {
            burstPause--;
            clickTicks = 0;
            return;
        }

        clickTicks++;
        if (clickTicks < nextInterval) return;
        clickTicks = 0;

        double baseCps = getDoubleSetting("cps");
        double jitter = getDoubleSetting("jitter") / 100.0;
        currentCps += (random.nextDouble() - 0.5) * jitter * 4.0;
        currentCps = Math.max(baseCps * (1.0 - jitter), Math.min(baseCps * (1.0 + jitter), currentCps));
        double effectiveCps = Math.max(1, currentCps);
        nextInterval = Math.max(1, (int) Math.round(20.0 / effectiveCps) + random.nextInt(5) - 2);

        // Occasional missed click — human click streams have gaps.
        if (random.nextInt(100) < 3) {
            nextInterval *= 2;
            return;
        }

        // Respect the attack cooldown (1.9+ combat).
        if (client.player.getAttackStrengthScale(0.0F) < 0.6F) return;

        // Post-attack blocking: the swing always lands unblocked, then the
        // block comes up right after it. Never blocks at air.
        if (blockhit && holdingShield) {
            releaseUse(client);
        }

        click(client);

        // Block holds only happen on the ground, with an enemy in melee range.
        if (blockhit && holdingShield && client.player.onGround()
                && enemyInRange(client)
                && (random.nextDouble() * 100.0) < getDoubleSetting("blockChance")) {
            int hold = (int) getDoubleSetting("blockTicks");
            hold += random.nextInt(3) - 1; // humanize
            if (hold < 1) hold = 1;
            blockTicksRemaining = hold;
            // Briefly drop sprint so the server sees correct block physics.
            if (client.player.isSprinting()) {
                client.player.setSprinting(false);
            }
            pressUse(client);
        }

        // Burst tracking.
        if ("Burst".equals(getStringSetting("pattern"))) {
            burstClicks++;
            if (burstClicks >= 2 + random.nextInt(3)) {
                burstClicks = 0;
                burstPause = 3 + random.nextInt(4);
            }
        }
    }

    /** Performs one click: entity attack if aiming at one, else block-break. */
    private void click(Minecraft client) {
        if (client.hitResult == null) return;
        if (client.hitResult.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) client.hitResult).getEntity();
            client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            client.gameMode.attack(client.player, target);
        } else if (client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) client.hitResult;
            BlockPos pos = hit.getBlockPos();
            Direction side = hit.getDirection();
            if (client.gameMode.isDestroying()) {
                client.gameMode.continueDestroyBlock(pos, side);
            } else {
                client.gameMode.startDestroyBlock(pos, side);
            }
        }
    }

    /** True while a non-friend enemy stands within the block range. */
    private boolean enemyInRange(Minecraft client) {
        if (client.player == null || client.level == null) return false;
        double range = getDoubleSetting("blockRange");
        double rangeSq = range * range;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity)) continue;
            LivingEntity living = (LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof Monster) && !(living instanceof Player)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.getX() - client.player.getX();
            double dy = living.getY() - client.player.getY();
            double dz = living.getZ() - client.player.getZ();
            if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
        }
        return false;
    }

    private static boolean holdingShield(ItemStack stack) {
        return stack != null && stack.getItem() == Items.SHIELD;
    }

    private void pressUse(Minecraft client) {
        if (!client.options.keyUse.isDown()) {
            client.options.keyUse.setDown(true);
        }
    }

    private void releaseUse(Minecraft client) {
        if (client.options.keyUse.isDown()) {
            client.options.keyUse.setDown(false);
        }
    }

    private void resetState(Minecraft client) {
        clickTicks = 0;
        burstClicks = 0;
        burstPause = 0;
        wasHeld = false;
        startDelayTicks = 0;
        if (blockTicksRemaining > 0) {
            blockTicksRemaining = 0;
            if (client != null && client.options != null) {
                releaseUse(client);
            }
        }
    }

    @Override
    public void onDisable() {
        resetState(Minecraft.getInstance());
    }
}

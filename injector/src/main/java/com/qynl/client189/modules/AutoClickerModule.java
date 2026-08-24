package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.access.IKeyBindingAccess;
import com.qynl.client189.access.IMinecraftAccess;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * AutoClicker — clicks for you with human-like timing variance.
 *
 * <p><b>Block Hit</b> (Vape-style): when enabled and holding a sword/axe,
 * the clicker re-blocks right after every swing using post-attack blocking —
 * the block is released a tick before the swing so the attack registers
 * unblocked, then pressed again immediately after. Sprint is interrupted for
 * one tick when the block starts so the server sees the correct
 * block-while-not-sprinting physics. Hold duration is randomized for a
 * human feel.</p>
 */
public class AutoClickerModule extends Module {
    private static AutoClickerModule instance;
    private final Random random = new Random();
    private int clickTimer = 0;
    private int nextInterval = 4;
    private int burstClicks = 0;
    private int burstDelay = 0;
    private double currentCps = 10.0;

    /** Human start reaction: random beat before clicking on first key press. */
    private boolean wasHeld = false;
    private int startDelayTicks = 0;

    /** Block Hit state. */
    private int blockTicksRemaining = 0;

    /** Slow random drift of the target CPS over seconds — humans never hold
     *  a constant click rate, and a constant rate is what AC statistics flag. */
    private double cpsDrift = 0.0;
    private int driftTimer = 0;

    public AutoClickerModule() {
        super("AutoClicker", "Clicks for you with human-like timing variance and optional Block Hit.",
                Category.COMBAT);
        instance = this;
        bindKey(Keyboard.KEY_G);
        addSetting(Setting.range("cps",       "Target CPS", 10.0, 5, 16, 1));
        addSetting(Setting.range("jitter",     "Jitter",      8.0, 0, 20, 1, "%"));
        addSetting(Setting.options("pattern",  "Pattern",    "Steady", "Steady", "Burst"));
        addSetting(Setting.options("blockhit",  "Block Hit",  "Off",    "Off",    "On"));
        addSetting(Setting.range("blockChance", "Block chance", 100.0, 0, 100, 5, "%"));
        addSetting(Setting.range("blockTicks",  "Block time",    4.0,  1,   8,  1, "t"));
        addSetting(Setting.range("blockRange",  "Block range",   3.5,  2.0, 5.0, 0.5, "b"));
    }

    public static boolean isActive() { return instance != null && instance.isEnabled(); }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            resetState(client);
            return;
        }
        if (client.currentScreen != null || !client.options.keyAttack.isPressed()) {
            resetState(client);
            return;
        }
        // Never click while the player is eating, drinking or using an item —
        // attacking would cancel the use (annoying for the user) and the
        // attack-while-using pattern is exactly what Intave's combat-flow
        // heuristics fingerprint. The clicker stands down until the use ends.
        if (client.player.isUsingItem()) {
            if (blockTicksRemaining > 0) {
                blockTicksRemaining = 0;
                releaseUse(client);
            }
            return;
        }

        // Humans don't start clicking the instant they press the button —
        // wait a short random beat on the first press (100–300 ms) so the
        // click start looks like a real reaction instead of an instant bot.
        if (!wasHeld) {
            wasHeld = true;
            startDelayTicks = 2 + random.nextInt(5);
            clickTimer = 0;
            return;
        }
        if (startDelayTicks > 0) {
            startDelayTicks--;
            return;
        }

        // The standalone BlockHit module (Vape-style) takes over blocking
        // when it's active — never double-block.
        boolean blockhit = "On".equals(getStringSetting("blockhit")) && !BlockHitModule.isActive();
        boolean holdingWeapon = isSwordOrAxe(client.player.getMainHandStack());

        // Maintain an active block hold from the previous swing.
        if (blockhit && holdingWeapon && blockTicksRemaining > 0) {
            blockTicksRemaining--;
            if (blockTicksRemaining > 0) {
                pressUse(client);
            } else {
                releaseUse(client);
            }
        } else if (blockTicksRemaining > 0) {
            // Block-hit was interrupted (weapon switched away, blockhit
            // toggled off, hand emptied): never leave the use button stuck
            // down — release it so the player isn't left blocking.
            blockTicksRemaining = 0;
            releaseUse(client);
        }

        // Slow random walk of the target CPS (humans speed up and slow down).
        if (--driftTimer <= 0) {
            driftTimer = 80 + random.nextInt(80);
            cpsDrift = (random.nextDouble() - 0.5) * 3.0;
        }

        // Burst mode: click 2-4 times fast, then pause briefly
        if ("Burst".equals(getStringSetting("pattern")) && burstDelay > 0) {
            burstDelay--;
            return;
        }

        clickTimer++;
        if (clickTimer < nextInterval) return;
        clickTimer = 0;

        double baseCps = getDoubleSetting("cps") + cpsDrift;
        double jitter = getDoubleSetting("jitter") / 100.0;

        // Slowly drift CPS for human feel
        currentCps += (random.nextDouble() - 0.5) * jitter * 4.0;
        currentCps = Math.max(baseCps * (1.0 - jitter), Math.min(baseCps * (1.0 + jitter), currentCps));

        double effectiveCps = Math.max(1, currentCps);
        nextInterval = Math.max(1, (int) Math.round(20.0 / effectiveCps) + random.nextInt(5) - 2);
        if (nextInterval < 1) nextInterval = 1;

        // Humans briefly pause clicking while they adjust their aim.
        if (Math.abs(org.lwjgl.input.Mouse.getDX()) + Math.abs(org.lwjgl.input.Mouse.getDY()) > 18) {
            nextInterval += 1 + random.nextInt(2);
        }
        // Occasional missed click — human click streams have gaps.
        if (random.nextInt(100) < 3) {
            nextInterval *= 2;
        }

        // Post-attack blocking: the swing always lands unblocked, then the
        // block comes up right after it. Never blocks at air — only when an
        // enemy is actually in melee range (blocking into nothing is a
        // textbook bot signature).
        if (blockhit && holdingWeapon) {
            releaseUse(client);
        }
        ((IMinecraftAccess) client).qynlDoAttack();

        // Block holds only happen on the ground: blocking while falling
        // serves no purpose, and a player who blocks mid-air while their
        // opponent is grounded looks like a script, not a human.
        if (blockhit && holdingWeapon && client.player.onGround
                && enemyInRange(client)
                && (random.nextDouble() * 100.0) < getDoubleSetting("blockChance")) {
            int hold = (int) getDoubleSetting("blockTicks");
            hold += random.nextInt(3) - 1; // humanize
            if (hold < 1) hold = 1;
            blockTicksRemaining = hold;
            // Intave trick: briefly drop sprint so the server sees correct
            // block-while-not-sprinting physics.
            if (client.player.isSprinting()) {
                client.player.setSprinting(false);
            }
            pressUse(client);
        }

        // Burst tracking
        if ("Burst".equals(getStringSetting("pattern"))) {
            burstClicks++;
            if (burstClicks >= 2 + random.nextInt(3)) {
                burstClicks = 0;
                burstDelay = 3 + random.nextInt(4); // small pause between bursts
            }
        }
    }

    /** True while a non-friend enemy stands within the block range. */
    private boolean enemyInRange(MinecraftClient client) {
        if (client.player == null || client.world == null) return false;
        double range = getDoubleSetting("blockRange");
        double rangeSq = range * range;
        for (net.minecraft.entity.Entity entity : client.world.entities) {
            if (!(entity instanceof net.minecraft.entity.LivingEntity)) continue;
            net.minecraft.entity.LivingEntity living = (net.minecraft.entity.LivingEntity) entity;
            if (living == client.player || !living.isAlive()) continue;
            if (!(living instanceof net.minecraft.entity.mob.MobEntity)
                    && !(living instanceof net.minecraft.entity.player.PlayerEntity)) continue;
            if (FriendsModule.isFriend(FriendsModule.entityName(living))) continue;
            double dx = living.x - client.player.x;
            double dy = living.y - client.player.y;
            double dz = living.z - client.player.z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) return true;
        }
        return false;
    }

    /** True only when the main hand actually holds a sword/axe. In 1.8.9 an
     *  empty hand is a null stack — dereferencing it crashes the tick, so
     *  never assume it is non-null. */
    private static boolean isSwordOrAxe(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    private void pressUse(MinecraftClient client) {
        if (!client.options.keyUse.isPressed()) {
            ((IKeyBindingAccess) client.options.keyUse).qynlSetPressed(true);
        }
    }

    private void releaseUse(MinecraftClient client) {
        if (client.options.keyUse.isPressed()) {
            ((IKeyBindingAccess) client.options.keyUse).qynlSetPressed(false);
        }
    }

    private void resetState(MinecraftClient client) {
        clickTimer = 0;
        burstClicks = 0;
        burstDelay = 0;
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
        resetState(MinecraftClient.getInstance());
    }
}

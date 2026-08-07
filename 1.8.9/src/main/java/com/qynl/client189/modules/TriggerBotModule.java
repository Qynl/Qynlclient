package com.qynl.client189.modules;

import com.qynl.client189.Category;
import com.qynl.client189.Module;
import com.qynl.client189.Setting;
import com.qynl.client189.mixin.MinecraftClientInvoker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.input.Keyboard;

import java.util.Random;

/**
 * TriggerBot for 1.8.9 — attacks enemies in your range automatically,
 * but only fires when your crosshair is already on the enemy.
 *
 * <p>Unlike an aimbot it never moves your view. The game's own crosshair
 * hit result ({@code MinecraftClient.result}) decides what you are looking
 * at; when that is a valid enemy inside the configured range, the module
 * attacks with humanized, adjustable timing (CPS + jitter, optional burst
 * pattern). Players who cannot click fast or time their swings get the
 * damage without having to aim or spam-click.</p>
 */
public class TriggerBotModule extends Module {
    private static final Random RANDOM = new Random();

    private int clickTimer = 0;
    private int nextInterval = 4;
    private int burstClicks = 0;
    private int burstDelay = 0;
    private double currentCps = 10.0;

    public TriggerBotModule() {
        super("TriggerBot",
                "Attacks enemies in range automatically when your crosshair is on them.",
                Category.ASSIST);
        bindKey(Keyboard.KEY_T);
        addSetting(Setting.options("mode",     "Mode",     "Hold", "Hold", "Always"));
        addSetting(Setting.range("range",      "Range",     3.5,  2.0,  6.0, 0.5, "b"));
        addSetting(Setting.range("cps",        "Target CPS", 10.0,  5,  16,   1));
        addSetting(Setting.range("jitter",     "Jitter",     8.0,  0,  20,   1, "%"));
        addSetting(Setting.options("pattern",  "Pattern",   "Steady", "Steady", "Burst"));
        addSetting(Setting.options("targets",  "Targets",   "Monsters", "Monsters", "Players+Monsters"));
        addSetting(Setting.range("minHp",      "Min HP",     0.0,  0,  40,   2, "hp"));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.interactionManager == null) {
            clickTimer = 0;
            return;
        }
        if (client.currentScreen != null || !client.player.isAlive() || client.player.isUsingItem()) {
            clickTimer = 0;
            burstClicks = 0;
            burstDelay = 0;
            return;
        }

        // Hold mode: only fire while the attack key is held.
        if ("Hold".equals(getStringSetting("mode")) && !client.options.keyAttack.isPressed()) {
            clickTimer = 0;
            burstClicks = 0;
            burstDelay = 0;
            return;
        }

        // ── Crosshair check: only attack what you are already looking at ──
        LivingEntity target = crosshairTarget(client);
        if (target == null) {
            clickTimer = 0;
            burstClicks = 0;
            burstDelay = 0;
            return;
        }

        // Burst pattern: 2-4 fast clicks, then a short pause.
        if ("Burst".equals(getStringSetting("pattern")) && burstDelay > 0) {
            burstDelay--;
            return;
        }

        clickTimer++;
        if (clickTimer < nextInterval) return;
        clickTimer = 0;

        double baseCps = getDoubleSetting("cps");
        double jitter = getDoubleSetting("jitter") / 100.0;

        // Slowly drift CPS for human feel.
        currentCps += (RANDOM.nextDouble() - 0.5) * jitter * 4.0;
        currentCps = Math.max(baseCps * (1.0 - jitter), Math.min(baseCps * (1.0 + jitter), currentCps));

        double effectiveCps = Math.max(1, currentCps);
        nextInterval = Math.max(1, (int) Math.round(20.0 / effectiveCps) + RANDOM.nextInt(5) - 2);
        if (nextInterval < 1) nextInterval = 1;

        ((MinecraftClientInvoker) client).invokeDoAttack();

        if ("Burst".equals(getStringSetting("pattern"))) {
            burstClicks++;
            if (burstClicks >= 2 + RANDOM.nextInt(3)) {
                burstClicks = 0;
                burstDelay = 3 + RANDOM.nextInt(4);
            }
        }
    }

    /**
     * Returns the enemy under the crosshair that passes the range, target
     * type and minimum-health checks, or null.
     */
    private LivingEntity crosshairTarget(MinecraftClient client) {
        if (client.result == null || client.result.type != BlockHitResult.Type.ENTITY) {
            return null;
        }
        if (!(client.result.entity instanceof LivingEntity)) {
            return null;
        }
        LivingEntity living = (LivingEntity) client.result.entity;
        if (!living.isAlive() || living.isInvisible()) {
            return null;
        }

        boolean targetPlayers = "Players+Monsters".equals(getStringSetting("targets"));
        boolean isMonster = living instanceof MobEntity;
        boolean isPlayer = living instanceof PlayerEntity;
        if (!isMonster && (!targetPlayers || !isPlayer)) {
            return null;
        }

        // Range check.
        double maxDist = getDoubleSetting("range");
        double dx = living.x - client.player.x;
        double dy = living.y - client.player.y;
        double dz = living.z - client.player.z;
        if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) {
            return null;
        }

        // Min HP: don't waste swings on nearly-dead mobs.
        double minHp = getDoubleSetting("minHp");
        if (minHp > 0 && living.getHealth() < minHp) {
            return null;
        }

        return living;
    }
}

package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * WTap — taps the W key briefly after every attack to reset sprint,
 * dealing extra knockback on the enemy. Only taps while already sprinting.
 */
public class WTapModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private int tapTimer = 0;
    private int tapDuration = 0;
    private int cooldownTicks = 0;
    private boolean attacked = false;

    public WTapModule() {
        super("WTap", "Taps W after each hit to reset sprint — extra knockback on enemies.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("chance", "Chance", 80.0, 50, 100, 5, "%"));
        addSetting(Setting.range("tapTicks", "Tap duration", 2.0, 1, 4, 1, "t"));
        addSetting(Setting.range("cooldownMs", "Cooldown", 400.0, 200, 800, 50, "ms"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
        }

        // Detect when player attacks
        boolean nowAttacking = client.options.keyAttack.isDown();
        if (!attacked && nowAttacking && client.hitResult != null
                && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY
                && client.player.getAttackStrengthScale(0.0F) >= 0.9F) {
            attacked = true;
            if (cooldownTicks <= 0 && client.player.isSprinting()) {
                double chance = getDoubleSetting("chance") / 100.0;
                if (RANDOM.nextDouble() < chance) {
                    tapTimer = (int) getDoubleSetting("tapTicks");
                    tapDuration = tapTimer;
                    cooldownTicks = (int) Math.round(getDoubleSetting("cooldownMs") / 50.0)
                            + RANDOM.nextInt(3) - 1;
                }
            }
        }
        if (!nowAttacking) {
            attacked = false;
        }

        if (tapTimer > 0) {
            // Release W during the tap
            client.options.keyUp.setDown(false);
            tapTimer--;
        }
        if (tapTimer == 0 && tapDuration > 0) {
            tapDuration = 0;
        }
    }

    private void reset() {
        tapTimer = 0;
        tapDuration = 0;
        cooldownTicks = 0;
        attacked = false;
    }

    @Override
    public void onDisable() {
        reset();
    }
}
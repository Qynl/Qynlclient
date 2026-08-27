package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;

/**
 * StrafeAssist — auto-strafes left/right in combat with randomized intervals.
 * Never strafes while idle; keeps sprint alive while strafing.
 */
public class StrafeAssistModule extends Module {
    private static final RandomSource RANDOM = RandomSource.create();

    private boolean strafing = false;
    private boolean strafeLeft = false;
    private int strafeTicks = 0;
    private int interval = 0;
    private int holdSkip = 0;

    public StrafeAssistModule() {
        super("StrafeAssist", "Auto-strafes left/right in combat with humanized intervals. Keeps sprint alive.",
                Category.COMBAT);
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.range("intervalMs", "Strafe interval", 350.0, 200, 800, 50, "ms"));
        addSetting(Setting.range("skipChance", "Hold skip", 10.0, 0, 25, 5, "%"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset(client);
            return;
        }
        var player = client.player;

        // Strafe whenever enabled — no combat or movement preconditions.
        // The user wants constant strafing once toggled: the interval pacing
        // provides the rhythm, and pausing only for death keeps it simple
        // and predictable. Holding any direction still wins (the input
        // override respects the player's own backpedal).

        if (strafing) {
            if (strafeTicks > 0) {
                strafeTicks--;
                // Keep sprint alive while strafing
                if (!player.isSprinting() && player.getFoodData().getFoodLevel() > 6
                        && !player.isCrouching()) {
                    player.setSprinting(true);
                }
                return;
            }
            // Strafe complete — release
            reset(client);
            interval = (int) Math.round(getDoubleSetting("intervalMs") / 50.0)
                    + RANDOM.nextInt((int) Math.round(getDoubleSetting("intervalMs") / 100.0));
            return;
        }

        if (interval > 0) {
            interval--;
            return;
        }

        // Hold skip: occasionally pause before next strafe
        if (holdSkip > 0) {
            holdSkip--;
            interval = (int) Math.round(getDoubleSetting("intervalMs") / 50.0);
            return;
        }

        // Random hold skip
        double skipChance = getDoubleSetting("skipChance") / 100.0;
        if (RANDOM.nextDouble() < skipChance) {
            holdSkip = 2 + RANDOM.nextInt(8);
            return;
        }

        // Start new strafe
        strafeLeft = RANDOM.nextBoolean();
        strafing = true;
        com.qynl.client.util.FeatureFeed.report(strafeLeft ? "Strafe L" : "Strafe R");
        int baseInterval = (int) Math.round(getDoubleSetting("intervalMs") / 50.0);
        // Random strafe duration with ±30% variation
        strafeTicks = baseInterval + (int) ((RANDOM.nextDouble() - 0.5) * 0.6 * baseInterval);
        if (strafeTicks < 2) strafeTicks = 2;
    }

    public boolean shouldStrafeLeft() {
        return isEnabled() && strafing && strafeLeft;
    }

    public boolean shouldStrafeRight() {
        return isEnabled() && strafing && !strafeLeft;
    }

    private void reset(Minecraft client) {
        strafing = false;
        strafeTicks = 0;
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        reset(client);
    }
}
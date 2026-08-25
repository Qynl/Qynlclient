package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import com.qynl.client.mixin.BlinkMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * Blink — holds movement packets briefly then releases them all at once.
 * Breaks the opponent's combo by making you appear to teleport.
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>Hold</b> — press the key to hold, release to burst.</li>
 *   <li><b>Auto</b> — automatically holds and releases in bursts with
 *       randomized timing. Hardened with burst fake lag (never a constant rate)
 *       and only activates on the ground while moving.</li>
 * </ul>
 */public class BlinkModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();
	private static BlinkModule instance;

	private boolean holding = false;
    private int autoTimer = 0;
    private int holdDuration = 0;
    private int gapTicks = 0;	public BlinkModule() {
		super("Blink", "Holds movement packets and releases in bursts — breaks enemy combos.",
				Category.UTILITY);
		instance = this;
        bindKey(GLFW.GLFW_KEY_UNKNOWN);
        addSetting(Setting.options("mode", "Mode", "Auto", "Auto", "Hold"));
        addSetting(Setting.range("holdMs", "Hold duration", 400.0, 100, 1000, 50, "ms"));
        addSetting(Setting.range("gapMs", "Gap between", 600.0, 200, 1200, 50, "ms"));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (holding) flushPackets(client);
            return;
        }
        var player = client.player;

        String mode = getStringSetting("mode");

        if ("Hold".equals(mode)) {
            // Manual hold: press keybind to toggle
            holding = true;
            // Blink is released when module is toggled off (onDisable)
            return;
        }

        // Auto mode
        if (player.isDeadOrDying() || player.isInWater() || player.isInLava()
                || player.onClimbable() || client.screen != null) {
            holding = false;
            autoTimer = 0;
            holdDuration = 0;
            gapTicks = 0;
            return;
        }

        // Only blink on ground while moving
        if (!player.onGround()) {
            holding = false;
            return;
        }
        double dx = player.getX() - player.xOld;
        double dz = player.getZ() - player.zOld;
        if (dx * dx + dz * dz < 0.0001) {
            holding = false;
            return;
        }

        if (holding) {
            holdDuration--;
            if (holdDuration <= 0) {
                // Release burst — flush held packets
                flushPackets(client);
                holding = false;
                gapTicks = (int) Math.round(getDoubleSetting("gapMs") / 50.0)
                        + RANDOM.nextInt(Math.max(1, (int) Math.round(getDoubleSetting("gapMs") / 100.0)));
            }
            return;
        }

        if (gapTicks > 0) {
            gapTicks--;
            return;
        }

        // Start new hold
        holding = true;
        holdDuration = (int) Math.round(getDoubleSetting("holdMs") / 50.0)
                + RANDOM.nextInt(6) - 3;
        if (holdDuration < 2) holdDuration = 2;
    }	public boolean isHolding() {
		return isEnabled() && holding;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

    private void flushPackets(Minecraft client) {
        if (client.getConnection() != null && client.getConnection().getConnection() != null) {
            BlinkMixin.releasePackets(client.getConnection().getConnection());
        }
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        flushPackets(client);
        holding = false;
        autoTimer = 0;
        holdDuration = 0;
        gapTicks = 0;
    }
}
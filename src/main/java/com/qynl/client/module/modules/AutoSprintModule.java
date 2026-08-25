package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * AutoSprint — sprints automatically while moving.
 * Randomized start delay and occasional missed starts for human-like pattern.
 */
public class AutoSprintModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();
	private int startDelay = 0;
	private int postAttackWindow = 0;

	public AutoSprintModule() {
		super("AutoSprint", "Automatically sprint while you move — with humanized random delays.",
				Category.COMBAT);
		bindKey(GLFW.GLFW_KEY_Y);
		addSetting(Setting.options("mode", "Mode", "Always", "Always", "Forward"));
		addSetting(Setting.range("missChance", "Miss start", 8.0, 0, 25, 5, "%"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null) return;
		var player = client.player;
		if (player.isSprinting()) {
			startDelay = 0;
			postAttackWindow = 0;
			return;
		}

		// Post-attack re-engage window: after attacking, sprint again quickly
		if (client.options.keyAttack.isDown() && player.getAttackStrengthScale(0.0F) < 0.5F) {
			postAttackWindow = 4;
		}

		boolean moving = "Always".equals(getStringSetting("mode"))
				? (player.input.forwardImpulse > 0.0F || player.input.leftImpulse != 0.0F)
				: player.input.forwardImpulse > 0.0F;
		boolean canSprint = player.getFoodData().getFoodLevel() > 6
				&& !player.isCrouching()
				&& !player.isUsingItem()
				&& !player.isPassenger();
		if (!moving || !canSprint) {
			startDelay = 0;
			return;
		}

		// Randomized start delay: 1-4 ticks of natural delay before sprint begins
		if (postAttackWindow > 0) {
			postAttackWindow--;
			player.setSprinting(true);
			return;
		}

		if (startDelay > 0) {
			startDelay--;
			return;
		}

		// Occasional missed start
		double missChance = getDoubleSetting("missChance") / 100.0;
		if (RANDOM.nextDouble() < missChance) {
			startDelay = 2 + RANDOM.nextInt(6);
			return;
		}

		// Random initial delay
		startDelay = RANDOM.nextInt(4);
		if (startDelay == 0) {
			player.setSprinting(true);
		}
	}
}
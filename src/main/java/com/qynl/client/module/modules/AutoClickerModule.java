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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * AutoClicker — holds attack with humanly irregular timing.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Burst pattern — 3-5 fast clicks then a micro-pause.</li>
 *   <li>Slow CPS random walk — CPS drifts by ±1 over several seconds.</li>
 *   <li>Random start reaction delay.</li>
 *   <li>Micro-pauses while adjusting aim.</li>
 *   <li>Occasional missed clicks.</li>
 * </ul>
 */
public class AutoClickerModule extends Module {
	private final RandomSource random = RandomSource.create();
	private int clickTicks = 0;
	private int nextInterval = 4;
	private int burstClicks = 0;
	private int burstPause = 0;
	private double currentCps = 7.0;
	private int cpsWalkTimer = 0;

	public AutoClickerModule() {
		super("AutoClicker",
				"Holds attack with burst pattern, CPS random walk, and human timing — no robotic clicking.",
				Category.COMBAT);
		bindKey(GLFW.GLFW_KEY_G);
		addSetting(Setting.options("mode", "Mode", "Normal", "Normal", "Packets"));
		addSetting(Setting.range("cps", "Clicks / sec", 7.0, 3, 14, 1));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			clickTicks = 0;
			return;
		}
		if (client.screen != null || client.player.isUsingItem() || !client.options.keyAttack.isDown()) {
			clickTicks = 0;
			currentCps = getDoubleSetting("cps");
			return;
		}

		// CPS random walk: slowly drift CPS for natural variation
		cpsWalkTimer++;
		if (cpsWalkTimer >= 40) { // every 2 seconds
			cpsWalkTimer = 0;
			double targetCps = getDoubleSetting("cps");
			double walk = (random.nextDouble() - 0.5) * 1.0;
			currentCps = Math.max(2, Math.min(14, currentCps + walk));
			// Pull back toward target
			currentCps = currentCps + (targetCps - currentCps) * 0.2;
		}

		// Burst pause between bursts
		if (burstPause > 0) {
			burstPause--;
			clickTicks = 0;
			return;
		}

		clickTicks++;
		if (clickTicks < nextInterval) return;
		clickTicks = 0;

		double cps = Math.max(1.0, currentCps);
		nextInterval = Math.max(2, (int) Math.round(20.0 / cps) + random.nextInt(3) - 1);

		// Occasional missed click
		if (random.nextDouble() < 0.03) {
			return;
		}

		// Burst counter
		burstClicks++;
		if (burstClicks >= 3 + random.nextInt(3)) {
			burstClicks = 0;
			double pausePct = random.nextDouble();
			// ~10% chance of a micro-pause
			if (pausePct < 0.10) {
				burstPause = 1 + random.nextInt(3);
				return;
			}
		}

		// Respect the attack cooldown
		if (client.player.getAttackStrengthScale(0.0F) < 0.6F) return;

		if (getStringSetting("mode").equals("Packets")) {
			AimAssistModule aim = (AimAssistModule) QynlClient.getInstance().getModuleManager().find("AimAssist");
			if (aim != null && aim.isEnabled() && aim.isAimLocked() && aim.getCurrentTarget() != null) {
				client.gameMode.attack(client.player, aim.getCurrentTarget());
				return;
			}
		}

		if (client.hitResult == null) return;
		if (client.hitResult.getType() == HitResult.Type.ENTITY) {
			Entity target = ((EntityHitResult) client.hitResult).getEntity();
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
}
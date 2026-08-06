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
 * AutoClicker — you only have to hold the attack button and it keeps
 * attacking/mining for you. The pauses between clicks are randomized and it
 * always respects the attack cooldown, so it never clicks with the perfectly
 * regular, machine-like timing anti-cheat systems look for. Great for players
 * who cannot click fast or for long periods.
 *
 * <p>Two modes, chosen in the Settings screen:</p>
 * <ul>
 *   <li><b>Normal</b> (default) — clicks whatever is under your crosshair.</li>
 *   <li><b>Packets</b> — clicks whatever AimAssist is aiming at (works with
 *       AimAssist in Packets mode, where the camera doesn't move).</li>
 * </ul>
 */
public class AutoClickerModule extends Module {
	private final RandomSource random = RandomSource.create();
	private int clickTicks = 0;
	private int nextInterval = 4;

	public AutoClickerModule() {
		super("AutoClicker",
				"Hold the attack button and it attacks and mines for you — with humanly irregular timing.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_G);
		addSetting(Setting.options("mode", "Mode", "Normal", "Normal", "Packets"));
		addSetting(Setting.range("cps", "Clicks / sec", 6.0, 3, 12, 1));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			clickTicks = 0;
			return;
		}
		if (client.screen != null || client.player.isUsingItem() || !client.options.keyAttack.isDown()) {
			clickTicks = 0;
			return;
		}

		clickTicks++;
		if (clickTicks < nextInterval) {
			return;
		}
		clickTicks = 0;

		double cps = getDoubleSetting("cps");
		if (cps < 1) {
			cps = 6;
		}
		// Human jitter: the next click comes ±1 tick sooner or later than average.
		nextInterval = Math.max(2, (int) Math.round(20.0 / cps) + random.nextInt(3) - 1);

		// Respect the attack cooldown so this never out-performs a normal player.
		if (client.player.getAttackStrengthScale(0.0F) < 0.6F) {
			return;
		}

		if (getStringSetting("mode").equals("Packets")) {
			AimAssistModule aim = (AimAssistModule) QynlClient.getInstance().getModuleManager().find("AimAssist");
			if (aim != null && aim.isEnabled() && aim.isAimLocked() && aim.getCurrentTarget() != null) {
				client.gameMode.attack(client.player, aim.getCurrentTarget());
				return;
			}
		}

		if (client.hitResult == null) {
			return;
		}
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

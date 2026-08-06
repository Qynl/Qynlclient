package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * CritAssist — when you are on the ground attacking a mob, this
 * automatically jumps at the right moment so your next hit lands as a
 * critical strike (1.5× damage). The jump timing is slightly varied and
 * never perfectly mechanical, so anti-cheat sees a normal player trying
 * to crit.
 *
 * <p>Critical hits happen when a player attacks while falling (i.e.
 * after a jump, before landing). This module jumps for you while you
 * hold the attack button so every swing can be a crit — essential for
 * players who cannot time jumps and clicks together.</p>
 */
public class CritAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();

	private boolean jumpQueued = false;
	private int jumpCooldown = 0;
	private int attackTimer = 0;

	public CritAssistModule() {
		super("CritAssist",
				"Auto-jumps so your hits land as critical strikes — for players who can't time jump + attack together.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F10);
		addSetting(Setting.range("minHealth", "Min enemy health", 10.0, 0, 40, 2, "hp"));
		addSetting(Setting.range("jumpDelay", "Jump delay", 2.0, 1, 8, 1, "t"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			reset();
			return;
		}
		if (client.player.isDeadOrDying() || client.screen != null || client.player.isPassenger()) {
			reset();
			return;
		}

		if (jumpCooldown > 0) {
			jumpCooldown--;
		}

		// Only help while the player is actively attacking.
		if (!client.options.keyAttack.isDown()) {
			reset();
			return;
		}

		// Must be aiming at a living entity.
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
			reset();
			return;
		}
		if (!(client.hitResult instanceof EntityHitResult entityHit)
				|| !(entityHit.getEntity() instanceof LivingEntity target)) {
			reset();
			return;
		}
		if (!target.isAlive()) {
			reset();
			return;
		}

		// Don't waste crits on nearly-dead enemies.
		if (target.getHealth() < getDoubleSetting("minHealth")) {
			reset();
			return;
		}

		// Must be on the ground to jump for a crit.
		if (!client.player.onGround()) {
			return;
		}

		// Must not be on cooldown.
		if (jumpCooldown > 0) {
			return;
		}

		// Respect attack cooldown: jump right before the swing lands.
		float charge = client.player.getAttackStrengthScale(0.0F);
		if (charge < 0.85F) {
			return;
		}

		// Don't spam jumps — only jump when a hit is about to go through.
		attackTimer++;
		int threshold = (int) (20.0 / Math.max(1, getDoubleSetting("jumpDelay")));
		if (attackTimer < threshold) {
			return;
		}
		attackTimer = 0;

		// Jump! Slight variation so it doesn't look like a metronome.
		client.player.jumpFromGround();
		jumpQueued = true;

		// Cooldown before next jump — humanized with ±1 tick jitter.
		jumpCooldown = 10 + RANDOM.nextInt(5);
	}

	private void reset() {
		jumpQueued = false;
		attackTimer = 0;
	}

	@Override
	public void onDisable() {
		reset();
		jumpCooldown = 0;
	}
}

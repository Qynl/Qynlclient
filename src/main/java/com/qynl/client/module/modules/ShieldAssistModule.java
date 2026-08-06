package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

/**
 * ShieldAssist — real blockhit assist for disabled players.
 *
 * <p>In 1.8.9-style combat you spam attack then quickly block when the
 * enemy attacks. In modern Minecraft, this automatically raises your
 * shield for a brief moment when a nearby hostile is about to hit you,
 * then releases immediately so you can keep attacking.</p>
 *
 * <p>Two modes:</p>
 * <ul>
 *   <li><b>BlockHit</b> — quick shield tap (like a parry). Detects enemy
 *       swing in melee range, blocks for 2–5 ticks, then releases.</li>
 *   <li><b>Hold</b> — holds shield longer while enemies are close, good
 *       for players who can't react fast enough at all.</li>
 * </ul>
 *
 * <p>Humanization: reaction delay before blocking, slight variation in
 * hold duration, cooldown after release so it never looks robotic.</p>
 */
public class ShieldAssistModule extends Module {
	private static final RandomSource RANDOM = RandomSource.create();

	private boolean forcingUse = false;
	private int reactionTicks = 0;
	private int holdTicks = 0;
	private int cooldownTicks = 0;
	private boolean blockedThisSwing = false;

	public ShieldAssistModule() {
		super("ShieldAssist",
				"Auto-blocks when an enemy attacks — quick tap (BlockHit) or hold. Human-timed so anti-cheat sees a normal player.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_F9);
		addSetting(Setting.options("mode", "Mode", "BlockHit", "BlockHit", "Hold"));
		addSetting(Setting.range("reactionMs", "Reaction delay", 180.0, 50, 350, 10, "ms"));
		addSetting(Setting.range("blockTicks", "Block duration", 4.0, 2, 12, 1, "t"));
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.gameMode == null) {
			reset();
			return;
		}
		if (client.player.isDeadOrDying() || client.screen != null) {
			reset();
			return;
		}

		// Cooldown after releasing shield — prevents spam-blocking.
		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		// While shield is up, count down and release when done.
		if (forcingUse) {
			if (holdTicks > 0) {
				holdTicks--;
				if (!client.options.keyUse.isDown()) {
					client.options.keyUse.setDown(true);
				}
				return;
			}
			releaseShield(client);
			// BlockHit mode: short cooldown (can attack quickly).
			// Hold mode: longer cooldown (stays safe).
			cooldownTicks = "BlockHit".equals(getStringSetting("mode"))
					? 4 + RANDOM.nextInt(4)
					: 8 + RANDOM.nextInt(6);
			return;
		}

		// Must have a shield in offhand or mainhand.
		boolean hasShield = client.player.getOffhandItem().getItem() instanceof ShieldItem
				|| client.player.getMainHandItem().getItem() instanceof ShieldItem;
		if (!hasShield) {
			return;
		}

		// Don't interfere with player's own right-click.
		if (client.options.keyUse.isDown() || client.player.isUsingItem()) {
			return;
		}

		// Reaction delay: wait before blocking.
		if (reactionTicks > 0) {
			reactionTicks--;
			return;
		}

		// Detect enemy swing — only block when an attack is actually coming.
		LivingEntity attacker = findAttackingEnemy(client);
		if (attacker == null) {
			blockedThisSwing = false;
			return;
		}

		// Don't block the same swing twice.
		if (blockedThisSwing) {
			return;
		}

		// Only auto-block while fighting (attack held) OR in Hold mode.
		boolean isBlockHit = "BlockHit".equals(getStringSetting("mode"));
		if (isBlockHit && !client.options.keyAttack.isDown()) {
			return;
		}

		// Raise shield!
		client.options.keyUse.setDown(true);
		forcingUse = true;
		blockedThisSwing = true;
		holdTicks = (int) getDoubleSetting("blockTicks") + RANDOM.nextInt(3) - 1;
		if (holdTicks < 2) holdTicks = 2;
	}

	/**
	 * Find a hostile entity that is actively attacking the player.
	 * Uses a combination of distance, facing direction, and the entity's
	 * swing timer to detect when an attack is imminent.
	 */
	private LivingEntity findAttackingEnemy(Minecraft client) {
		if (client.player == null || client.level == null) return null;
		var player = client.player;
		double searchRadius = "BlockHit".equals(getStringSetting("mode")) ? 3.0 : 4.5;

		AABB searchBox = player.getBoundingBox().inflate(searchRadius);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;

		for (var entity : client.level.getEntities(player, searchBox, e ->
				e instanceof LivingEntity && e.isAlive()
						&& (e instanceof Monster || e instanceof Player)
						&& e != player)) {
			if (!(entity instanceof LivingEntity living)) continue;

			double dist = living.distanceTo(player);
			if (dist > searchRadius) continue;

			// Check: is this entity facing us? (about to attack)
			var toPlayer = player.position().subtract(living.position()).normalize();
			var lookDir = living.getLookAngle();
			double dot = lookDir.dot(toPlayer);

			// Also check if the entity is swinging (active attack)
			boolean isSwinging = living.swinging;

			// BlockHit mode: need clear attack signal — close + facing + swinging.
			// Hold mode: more permissive — close + facing is enough.
			if ("BlockHit".equals(getStringSetting("mode"))) {
				if (dist <= 3.0 && dot > 0.5 && isSwinging) {
					if (dist < bestDist) { bestDist = dist; best = living; }
				}
				// Even without swinging: if very close and facing, it's about to hit.
				if (dist <= 2.5 && dot > 0.6 && best == null) {
					// Start reaction timer — the attack is coming.
					double ms = getDoubleSetting("reactionMs");
					reactionTicks = Math.max(1, (int) Math.round(ms / 50.0));
					bestDist = dist; best = living;
				}
			} else {
				// Hold mode: block whenever a threat is close and facing us.
				if (dist <= 3.5 && dot > 0.2) {
					if (dist < bestDist) { bestDist = dist; best = living; }
				}
			}
		}

		if (best != null && reactionTicks == 0) {
			double ms = getDoubleSetting("reactionMs");
			reactionTicks = Math.max(1, (int) Math.round(ms / 50.0) + RANDOM.nextInt(3) - 1);
		}

		return best;
	}

	private void releaseShield(Minecraft client) {
		if (client.options != null) {
			client.options.keyUse.setDown(false);
		}
		forcingUse = false;
		holdTicks = 0;
	}

	private void reset() {
		Minecraft client = Minecraft.getInstance();
		releaseShield(client);
		reactionTicks = 0;
		cooldownTicks = 0;
		blockedThisSwing = false;
	}

	@Override
	public void onDisable() {
		reset();
	}
}

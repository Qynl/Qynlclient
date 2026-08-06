package com.qynl.client.module.modules;

import com.qynl.client.QynlClient;
import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import com.qynl.client.util.HumanAim;
import com.qynl.client.util.SilentAim;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AimAssist — while you hold the attack button, your aim is gently guided
 * toward the nearest hostile entity near your crosshair.
 *
 * <p>The {@link HumanAim} engine makes every correction look like the hand
 * of a normal player (mouse-grid steps, reaction delay, wandering aim point,
 * easing curves, configurable strength).</p>
 *
 * <p>Settings:</p>
 * <ul>
 *   <li><b>Mode</b> — Rotations (camera moves) or Packets (server-only).</li>
 *   <li><b>Strength</b> — how aggressively the aim pulls (30–150%).</li>
 *   <li><b>Max angle</b> — only aim at targets within this many degrees from crosshair.</li>
 *   <li><b>Max distance</b> — only aim at targets within this range.</li>
 *   <li><b>Target</b> — Monsters only, or Players too (PvP assist).</li>
 * </ul>
 */
public class AimAssistModule extends Module {
	private final HumanAim humanAim = new HumanAim();

	public AimAssistModule() {
		super("AimAssist",
				"Gently guides your aim toward the nearest hostile while you attack — humanized for anti-cheat safety.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_O);
		addSetting(Setting.options("mode", "Mode", "Rotations", "Rotations", "Packets"));
		addSetting(Setting.range("strength", "Strength", 100.0, 30, 150, 5, "%"));
		addSetting(Setting.range("maxAngle", "Max angle", 30.0, 10, 90, 5, "°"));
		addSetting(Setting.range("maxDist", "Max distance", 7.0, 3, 12, 0.5, "b"));
		addSetting(Setting.options("target", "Target", "Monsters", "Monsters", "Players+Monsters"));
	}

	@Override
	public void onEnable() {
		applySettings();
	}

	@Override
	public void onDisable() {
		SilentAim.clear();
	}

	private void applySettings() {
		double strength = getDoubleSetting("strength") / 100.0;
		humanAim.setStrength(strength);
	}

	@Override
	public void onTick(Minecraft client) {
		applySettings();

		Entity target = null;
		if (client.player != null && client.level != null && client.screen == null
				&& client.options.keyAttack.isDown() && !client.player.isSpectator()) {
			target = findTarget(client);
		}
		humanAim.update(client, target);

		if (target == null) {
			SilentAim.clear();
			return;
		}

		if (getStringSetting("mode").equals("Packets")) {
			float[] silent = humanAim.isArmed()
					? humanAim.stepTowards(client, client.player.getYRot(), client.player.getXRot())
					: null;
			if (silent != null) {
				SilentAim.set(silent[0], silent[1]);
				attackSilentTarget(client, target);
			} else {
				SilentAim.clear();
			}
		} else if (humanAim.isArmed()) {
			float[] next = humanAim.stepTowards(client, client.player.getYRot(), client.player.getXRot());
			if (next != null) {
				client.player.setYRot(next[0]);
				client.player.setXRot(next[1]);
				client.player.yHeadRot = next[0];
				client.player.yHeadRotO = next[0];
				client.player.yRotO = next[0];
				client.player.xRotO = next[1];
			}
		}
	}

	private void attackSilentTarget(Minecraft client, Entity target) {
		if (client.gameMode == null) return;
		AutoClickerModule clicker = (AutoClickerModule) QynlClient.getInstance()
				.getModuleManager().find("AutoClicker");
		if (clicker != null && clicker.isEnabled()
				&& "Packets".equals(clicker.getStringSetting("mode"))) {
			return;
		}
		if (client.hitResult instanceof EntityHitResult) return;
		if (client.player.getAttackStrengthScale(0.0F) >= 0.9F
				&& client.player.canInteractWithEntity(target, 1.0)) {
			client.gameMode.attack(client.player, target);
			client.player.resetAttackStrengthTicker();
		}
	}

	public Entity getCurrentTarget() {
		return humanAim.getTarget();
	}

	public boolean isAimLocked() {
		return humanAim.isArmed();
	}

	private Entity findTarget(Minecraft client) {
		double maxDist = getDoubleSetting("maxDist");
		double maxAngle = getDoubleSetting("maxAngle");
		boolean targetPlayers = "Players+Monsters".equals(getStringSetting("target"));

		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getLookAngle();

		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity == client.player) continue;

			boolean isMonster = entity instanceof Monster;
			boolean isPlayer = entity instanceof Player;
			if (!isMonster && (!targetPlayers || !isPlayer)) continue;
			if (entity instanceof LivingEntity living
					&& (!living.isAlive() || living.isInvisibleTo(client.player))) continue;

			Vec3 delta = entity.getBoundingBox().getCenter().subtract(eye);
			double dist = delta.length();
			if (dist > maxDist || dist < 0.01) continue;

			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(delta.normalize()), -1.0, 1.0)));
			if (angle > maxAngle) continue;

			// Score: prioritize closer targets, slightly weighted by angle.
			// Players get slight priority in mixed mode (closer = more threatening).
			double score = dist * 0.8 + angle * 0.2;
			if (isPlayer) score -= 0.5; // slight player priority
			if (score < bestScore) {
				bestScore = score;
				best = entity;
			}
		}
		return best;
	}
}

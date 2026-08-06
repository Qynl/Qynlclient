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
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AimAssist — while you hold the attack button, your aim is gently guided
 * toward the nearest monster near your crosshair. It never locks on and never
 * snaps: the {@link HumanAim} engine makes every correction look like the hand
 * of a normal player (mouse-grid steps, reaction delay, wandering aim point,
 * easing curves), so server anti-cheat systems don't confuse accessibility
 * with cheating.
 *
 * <p>Two modes, chosen in the in-game Settings screen:</p>
 * <ul>
 *   <li><b>Rotations</b> (default) — the camera itself is eased toward the target.</li>
 *   <li><b>Packets</b> — your camera stays exactly where you point it, but the
 *       server sees the aimed rotation in the movement packets, so your hits
 *       land on the target without the view moving.</li>
 * </ul>
 */
public class AimAssistModule extends Module {
	private static final double MAX_DISTANCE = 7.0;
	private static final double MAX_ANGLE = 25.0; // degrees from crosshair

	private final HumanAim humanAim = new HumanAim();

	public AimAssistModule() {
		super("AimAssist",
				"Gently guides your aim toward the nearest monster while you attack — humanized so anti-cheat sees a normal hand.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_O);
		addSetting(Setting.options("mode", "Mode", "Rotations", "Rotations", "Packets"));
	}

	@Override
	public void onDisable() {
		SilentAim.clear();
	}

	@Override
	public void onTick(Minecraft client) {
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
			// Server-side aim: swap the rotation only while the movement packet
			// is built; the camera itself never moves.
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
			// Visual assist: ease the camera itself toward the target.
			float[] next = humanAim.stepTowards(client, client.player.getYRot(), client.player.getXRot());
			if (next != null) {
				client.player.setYRot(next[0]);
				client.player.setXRot(next[1]);
				// Keep the head/body rotation in sync so the camera follows even
				// in views driven by yHeadRot (e.g. while riding).
				client.player.yHeadRot = next[0];
				client.player.yHeadRotO = next[0];
				client.player.yRotO = next[0];
				client.player.xRotO = next[1];
			}
		}
	}

	/**
	 * Packets mode is only useful if hits actually land on the target, since
	 * the camera never moves. While the attack button is held, attack the
	 * silently-aimed target directly — with vanilla attack-cooldown cadence
	 * and only when the target is within the reach the server will accept.
	 */
	private void attackSilentTarget(Minecraft client, Entity target) {
		if (client.gameMode == null) {
			return;
		}
		// Let AutoClicker (Packets mode) do the clicking if it is already set
		// up for it, so the two modules never double-attack the same target.
		AutoClickerModule clicker = (AutoClickerModule) QynlClient.getInstance()
				.getModuleManager().find("AutoClicker");
		if (clicker != null && clicker.isEnabled()
				&& "Packets".equals(clicker.getStringSetting("mode"))) {
			return;
		}
		// If the crosshair is already on an entity, the vanilla attack loop
		// handles that click — don't send a second attack packet.
		if (client.hitResult instanceof EntityHitResult) {
			return;
		}
		// Attack at the same cadence vanilla uses, and only within the reach
		// the server will accept (entityInteractionRange + 1).
		if (client.player.getAttackStrengthScale(0.0F) >= 0.9F
				&& client.player.canInteractWithEntity(target, 1.0)) {
			client.gameMode.attack(client.player, target);
			client.player.resetAttackStrengthTicker();
		}
	}

	/** Target currently held by the humanized aim (used by AutoClicker in Packets mode). */
	public Entity getCurrentTarget() {
		return humanAim.getTarget();
	}

	/** Whether the humanized aim has finished its reaction delay and is actively locked. */
	public boolean isAimLocked() {
		return humanAim.isArmed();
	}

	private Entity findTarget(Minecraft client) {
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3 eye = client.player.getEyePosition();
		Vec3 look = client.player.getLookAngle();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof Monster monster)) {
				continue;
			}
			if (!monster.isAlive() || monster.isInvisibleTo(client.player)) {
				continue;
			}
			Vec3 delta = monster.getBoundingBox().getCenter().subtract(eye);
			double dist = delta.length();
			if (dist > MAX_DISTANCE || dist < 0.01) {
				continue;
			}
			double angle = Math.toDegrees(Math.acos(Mth.clamp(look.dot(delta.normalize()), -1.0, 1.0)));
			if (angle > MAX_ANGLE) {
				continue;
			}
			double score = dist + angle / 100.0;
			if (score < bestScore) {
				bestScore = score;
				best = monster;
			}
		}
		return best;
	}
}

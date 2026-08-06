package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * AimAssist — while you hold the attack button, your view is gently eased
 * toward the nearest monster near your crosshair. It never locks on and
 * never snaps: it just takes the strain out of the last bit of aiming,
 * which is a huge help when fine mouse control is hard.
 */
public class AimAssistModule extends Module {
	private static final double MAX_DISTANCE = 7.0;
	private static final double MAX_ANGLE = 25.0; // degrees from crosshair
	private static final float TURN_SPEED = 1.5F; // degrees per tick (30°/s, gentle)

	public AimAssistModule() {
		super("AimAssist", "Gently eases your aim toward the nearest monster while you attack.",
				Category.ASSIST);
		bindKey(GLFW.GLFW_KEY_O);
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null || client.screen != null) {
			return;
		}
		// Only help while the player is actually trying to attack.
		if (!client.options.keyAttack.isDown()) {
			return;
		}
		var player = client.player;
		if (player.isSpectator()) {
			return;
		}
		Entity target = findTarget(client);
		if (target == null) {
			return;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
		double dist = delta.length();
		if (dist < 0.001) {
			return;
		}

		double yawTo = Math.toDegrees(Math.atan2(-delta.x, delta.z));
		double pitchTo = Math.toDegrees(Math.asin(-delta.y / dist));

		float yaw = player.getYRot();
		float pitch = player.getXRot();
		float newYaw = yaw + Mth.clamp((float) Mth.wrapDegrees(yawTo - yaw), -TURN_SPEED, TURN_SPEED);
		float newPitch = Mth.clamp(pitch + (float) Mth.clamp(pitchTo - pitch, -TURN_SPEED, TURN_SPEED),
				-90.0F, 90.0F);

		player.setYRot(newYaw);
		player.setXRot(newPitch);
		// Keep the interpolated rotation in sync so the view glides, not snaps.
		player.yRotO = newYaw;
		player.xRotO = newPitch;
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

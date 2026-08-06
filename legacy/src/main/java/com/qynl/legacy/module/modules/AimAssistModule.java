package com.qynl.legacy.module.modules;

import com.qynl.legacy.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.Monster;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Keyboard;

/**
 * AimAssist — while you hold the attack button near a monster, your aim is
 * gently eased toward it.  No snap, no lock — just a gentle pull, like a
 * friend nudging your hand toward the target.
 */
public class AimAssistModule extends Module {
	private static final double MAX_DIST = 7.0;
	private static final double MAX_ANGLE = 30.0;

	public AimAssistModule() {
		super("AimAssist",
				"Gently guides your aim toward the nearest monster while you attack.",
				Keyboard.KEY_X);
	}

	@Override
	public void onTick(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		if (!client.options.keyAttack.isPressed()) return;

		Entity target = findTarget(client);
		if (target == null) return;

		Vec3d eye = client.player.getClientEyePos();
		Vec3d center = target.getEntityBoundingBox().getCenter();
		Vec3d delta = center.subtract(eye);
		double dist = delta.length();
		if (dist < 0.01) return;

		double yawTo = Math.toDegrees(Math.atan2(-delta.xCoord, delta.zCoord));
		double pitchTo = Math.toDegrees(Math.asin(-delta.yCoord / dist));

		double yawDelta = MathHelper.wrapDegrees(yawTo - client.player.yaw);
		double pitchDelta = MathHelper.clamp(pitchTo - client.player.pitch, -90.0, 90.0);

		double speed = 1.6; // degrees per tick — slow enough to look human
		double stepYaw = MathHelper.clamp(yawDelta, -speed, speed);
		double stepPitch = MathHelper.clamp(pitchDelta, -speed, speed);

		client.player.yaw = (float) MathHelper.wrapDegrees(client.player.yaw + stepYaw);
		client.player.pitch = MathHelper.clamp(client.player.pitch + (float) stepPitch, -90.0F, 90.0F);
	}

	private Entity findTarget(MinecraftClient client) {
		Entity best = null;
		double bestScore = Double.MAX_VALUE;
		Vec3d eye = client.player.getClientEyePos();
		Vec3d look = client.player.getLookVec();

		for (Entity e : client.world.getEntities()) {
			if (!(e instanceof Monster)) continue;
			if (e.isDead || !e.isEntityAlive()) continue;
			if (e.isInvisibleToPlayer(client.player)) continue;

			Vec3d delta = e.getEntityBoundingBox().getCenter().subtract(eye);
			double dist = delta.length();
			if (dist > MAX_DIST || dist < 0.01) continue;

			double angle = Math.toDegrees(Math.acos(
					MathHelper.clamp(look.dotProduct(delta.normalize()), -1.0, 1.0)));
			if (angle > MAX_ANGLE) continue;

			double score = dist + angle / 100.0;
			if (score < bestScore) {
				bestScore = score;
				best = e;
			}
		}
		return best;
	}
}

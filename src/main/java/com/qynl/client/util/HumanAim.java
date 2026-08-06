package com.qynl.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The "humanized" aim engine behind AimAssist.
 *
 * <p>Pure magnetic aim looks inhuman: it snaps to the exact center, tracks
 * perfectly and never hesitates. That is exactly what server anti-cheat
 * systems flag. This engine deliberately makes the assist look like the
 * hand of a normal player:</p>
 *
 * <ul>
 *   <li><b>Mouse grid (GCD)</b> — every correction is rounded to whole mouse
 *       pixels, the same grid a real mouse produces.</li>
 *   <li><b>Human inaccuracy</b> — aims at a slightly random point on the
 *       body, not the exact center, and that point wanders.</li>
 *   <li><b>Curves instead of straight lines</b> — turn speed eases in
 *       and out instead of moving at a constant rate.</li>
 *   <li><b>Reaction delay</b> — assistance only starts 150–300 ms after the
 *       target appears, and backs off when you move the mouse yourself.</li>
 *   <li><b>Configurable strength</b> — turn speed scales with the user's
 *       chosen aggressiveness, from gentle guidance to firm assist.</li>
 * </ul>
 */
public final class HumanAim {
	private final RandomSource random = RandomSource.create();

	private Entity target;
	private int reactionTicks;
	private int overrideTicks;
	private double lastMouseX;
	private double lastMouseY;
	private double aimYawOffset;
	private double aimPitchOffset;
	private int wanderTimer;
	private double wanderPhase;

	// Configurable parameters set by AimAssistModule
	private double strength = 1.0;       // 0.3–2.0: how aggressive the pull is
	private double maxTurnSpeed = 1.9;   // base turn speed, scaled by strength
	private int reactionDelayTicks = 5;  // base reaction delay before aim engages

	/** Called every tick with the current target (null when none). */
	public void update(Minecraft client, Entity newTarget) {
		double mouseX = client.mouseHandler.xpos();
		double mouseY = client.mouseHandler.ypos();
		double move = Math.hypot(mouseX - lastMouseX, mouseY - lastMouseY);
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (move > 4.0) {
			overrideTicks = 6;
		} else if (overrideTicks > 0) {
			overrideTicks--;
		}

		if (newTarget != target) {
			target = newTarget;
			reactionTicks = target != null ? reactionDelayTicks + random.nextInt(3) : 0;
			wanderTimer = 0;
			if (target != null) {
				reWander();
			}
		} else if (target != null) {
			if (reactionTicks > 0) {
				reactionTicks--;
			}
			if (--wanderTimer <= 0) {
				wanderTimer = 3 + random.nextInt(6);
				reWander();
			}
		}
	}

	/** Configure how aggressively the aim pulls. 1.0 = default, 0.5 = gentle, 2.0 = firm. */
	public void setStrength(double strength) {
		this.strength = Mth.clamp(strength, 0.3, 2.5);
		this.maxTurnSpeed = 1.3 + this.strength * 0.8;
	}

	/** How many ticks before aim locks on after target switch (3 = ~150ms). */
	public void setReactionTicks(int ticks) {
		this.reactionDelayTicks = Mth.clamp(ticks, 1, 12);
	}

	public boolean isArmed() {
		return target != null && reactionTicks <= 0 && overrideTicks <= 0;
	}

	public Entity getTarget() {
		return target;
	}

	private void reWander() {
		// Wander amount scales inversely with strength — stronger aim wanders less.
		double wanderScale = 1.0 / Math.max(0.5, strength);
		aimYawOffset = (random.nextDouble() - 0.5) * 3.0 * wanderScale;
		aimPitchOffset = (random.nextDouble() - 0.5) * 4.0 * wanderScale;
	}

	/**
	 * Compute the rotation the player should have this tick.
	 *
	 * @return {yaw, pitch} or null when there is nothing to aim at.
	 */
	public float[] stepTowards(Minecraft client, float currentYaw, float currentPitch) {
		if (target == null || client.player == null) {
			return null;
		}
		Vec3 eye = client.player.getEyePosition();
		Vec3 delta = target.getBoundingBox().getCenter().subtract(eye);
		double dist = delta.length();
		if (dist < 0.01) {
			return null;
		}
		double yawTo = Math.toDegrees(Math.atan2(-delta.x, delta.z)) + aimYawOffset;
		double pitchTo = Math.toDegrees(Math.asin(-delta.y / dist)) + aimPitchOffset;

		// Mouse grid: smallest step a real mouse can make at current sensitivity.
		double sens = client.options.sensitivity().get();
		double f = sens * 0.6 + 0.2;
		double gcdStep = (f * f * f) * 8.0 * 0.15;

		double yawDelta = Mth.wrapDegrees(yawTo - currentYaw);
		double pitchDelta = Mth.clamp(pitchTo - currentPitch, -90.0, 90.0);

		// Turn speed: scales with strength. Fast when far, gentle when close.
		double speed = maxTurnSpeed * strength;
		double easeYaw = speed * (0.35 + 0.65 * Math.min(1.0, Math.abs(yawDelta) / 20.0));
		double easePitch = speed * (0.35 + 0.65 * Math.min(1.0, Math.abs(pitchDelta) / 20.0));

		double stepYaw = Mth.clamp(yawDelta, -easeYaw, easeYaw);
		double stepPitch = Mth.clamp(pitchDelta, -easePitch, easePitch);

		// Snap to mouse grid — always move at least one pixel when needed.
		if (gcdStep > 1e-6) {
			double snappedYaw = Math.round(stepYaw / gcdStep) * gcdStep;
			if (Math.abs(stepYaw) > 1e-4 && Math.abs(snappedYaw) < gcdStep * 0.5) {
				snappedYaw = Math.signum(stepYaw) * gcdStep;
			}
			double snappedPitch = Math.round(stepPitch / gcdStep) * gcdStep;
			if (Math.abs(stepPitch) > 1e-4 && Math.abs(snappedPitch) < gcdStep * 0.5) {
				snappedPitch = Math.signum(stepPitch) * gcdStep;
			}
			stepYaw = snappedYaw;
			stepPitch = snappedPitch;
		}

		// Tremor so crosshair never rests perfectly still.
		double wobble = Math.sin(wanderPhase) * 0.04 * (1.0 / Math.max(0.5, strength));
		wanderPhase += 0.5 + random.nextDouble() * 0.3;

		return new float[]{
				(float) Mth.wrapDegrees(currentYaw + stepYaw + wobble),
				Mth.clamp(currentPitch + (float) stepPitch, -90.0F, 90.0F)
		};
	}
}

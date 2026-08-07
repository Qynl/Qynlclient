package com.qynl.client.module.modules;

import com.qynl.client.module.Category;
import com.qynl.client.module.Module;
import com.qynl.client.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;
import org.lwjgl.glfw.GLFW;

/**
 * FlyAssist — smooth flight without needing fly permission.
 *
 * <p>Three modes:</p>
 * <ul>
 *   <li><b>Silent</b> (default) — velocity-only flight: no abilities packet,
 *       no "flying" state.  Vertical speed is capped at jump speed and
 *       movement packets are spoofed as {@code onGround} (see
 *       {@link com.qynl.client.mixin.FlyGroundSpoofMixin}), so servers see
 *       a normal jumping/walking pattern rather than sustained flight.
 *       Best-effort — aggressive anti-cheat may still catch it.</li>
 *   <li><b>Smooth</b> — same velocity physics, no packet spoofing.  Good on
 *       servers where flight is allowed (creative, allow-flight) and you
 *       just want buttery movement instead of vanilla's abrupt one.</li>
 *   <li><b>Vanilla</b> — toggles real creative flight via the abilities
 *       packet.  Only works where the server grants flight permission;
 *       the other two modes need no permission at all.</li>
 * </ul>
 *
 * <p>Movement is computed every tick: horizontal input is converted relative
 * to camera yaw with smooth acceleration, the Y axis is driven by
 * jump/sneak with optional auto-hover, and speed / vertical strength are
 * both adjustable.</p>
 */
public class FlyAssistModule extends Module {
	private static FlyAssistModule instance;

	/** True while Vanilla mode is forcing the player's abilities. */
	private boolean touchedAbilities;

	public FlyAssistModule() {
		super("FlyAssist",
				"Smooth flight without fly permission — Silent mode spoofs grounded packets.",
				Category.ASSIST);
		instance = this;
		bindKey(GLFW.GLFW_KEY_G);
		addSetting(Setting.options("mode", "Mode", "Silent", "Silent", "Smooth", "Vanilla"));
		addSetting(Setting.range("speed", "Speed", 1.0, 0.5, 3.0, 0.1, "x"));
		addSetting(Setting.range("vertical", "Vertical", 1.0, 0.4, 2.5, 0.1, "x"));
		addSetting(Setting.options("hover", "Auto-hover", "On", "On", "Off"));
		addSetting(Setting.options("spoof", "Spoof ground", "On", "On", "Off"));
	}

	public static FlyAssistModule getInstance() {
		return instance;
	}

	public static boolean isActive() {
		return instance != null && instance.isEnabled();
	}

	/** True while movement packets should be spoofed as grounded (Silent fly). */
	public static boolean shouldSpoofGround() {
		if (instance == null || !instance.isEnabled()) {
			return false;
		}
		if ("Vanilla".equals(instance.getStringSetting("mode"))) {
			return false;
		}
		return "On".equals(instance.getStringSetting("spoof"));
	}

	@Override
	public void onDisable() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && touchedAbilities) {
			try {
				client.player.getAbilities().flying = false;
			} catch (Throwable ignored) {
			}
		}
		touchedAbilities = false;
	}

	@Override
	public void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		var player = client.player;

		// Don't engage while the player is riding, spectating, in water/lava,
		// on a ladder, dead, or with a screen open.
		if (player.isPassenger() || player.isSpectator() || player.isDeadOrDying()
				|| player.isInWater() || player.isInLava() || player.onClimbable()
				|| client.screen != null) {
			return;
		}

		if ("Vanilla".equals(getStringSetting("mode"))) {
			tickVanilla(client);
		} else {
			tickVelocity(client);
		}
	}

	// ── Vanilla mode: real (permission-gated) creative flight ──────────

	private void tickVanilla(Minecraft client) {
		var player = client.player;
		Abilities abilities = player.getAbilities();
		boolean changed = false;

		if (!abilities.mayfly) {
			abilities.mayfly = true;
			touchedAbilities = true;
			changed = true;
		}
		if (!abilities.flying) {
			abilities.flying = true;
			touchedAbilities = true;
			changed = true;
		}
		if (changed && player.connection != null) {
			player.connection.send(new ServerboundPlayerAbilitiesPacket(abilities));
		}
	}

	// ── Silent / Smooth mode: velocity flight, no permission needed ────

	private void tickVelocity(Minecraft client) {
		boolean silent = "Silent".equals(getStringSetting("mode"));
		double speed = getDoubleSetting("speed");
		double vertical = getDoubleSetting("vertical");
		boolean hover = "On".equals(getStringSetting("hover"));

		// Input: 1 / 0 / -1 per axis.
		double forward = (client.options.keyUp.isDown() ? 1 : 0)
				- (client.options.keyDown.isDown() ? 1 : 0);
		double strafe = (client.options.keyRight.isDown() ? 1 : 0)
				- (client.options.keyLeft.isDown() ? 1 : 0);

		// Normalise diagonals so S+A moves at full speed, not √2× it.
		double len = Math.sqrt(forward * forward + strafe * strafe);
		if (len > 0) {
			forward /= len;
			strafe /= len;
		}

		var player = client.player;

		// Vanilla fly-style world direction from input + camera yaw
		// (0° faces +Z: W at yaw 0 → +Z, D → +X).
		double yawRad = Math.toRadians(player.getYRot());
		double sin = Math.sin(yawRad);
		double cos = Math.cos(yawRad);

		double targetSpeed = 0.54 * speed; // ≈ creative fly speed in blocks/tick
		double targetX = (-forward * sin + strafe * cos) * targetSpeed;
		double targetZ = (forward * cos + strafe * sin) * targetSpeed;

		var cur = player.getDeltaMovement();

		// Smooth acceleration toward the target — feels like creative fly
		// instead of teleport-y input snaps.
		final double accel = 0.15;
		double newX = cur.x + (targetX - cur.x) * accel;
		double newZ = cur.z + (targetZ - cur.z) * accel;

		// Vertical: jump = up, sneak = down, else hover in place.
		boolean up = client.options.keyJump.isDown();
		boolean down = client.options.keyShift.isDown();
		double vSpeed = 0.12 * vertical;
		double newY;

		if (silent && up) {
			// Climb at most at jump speed so the server reads a jump arc,
			// never sustained flight.
			newY = Math.min(0.30, cur.y + 0.10);
		} else if (up) {
			newY = cur.y + vSpeed;
			if (newY > 0.40) {
				newY = 0.40;
			}
		} else if (down) {
			newY = -vSpeed;
		} else if (hover) {
			// Cancel gravity smoothly so the player hangs instead of falling.
			newY = cur.y + (0.0 - cur.y) * 0.35;
		} else {
			newY = cur.y;
		}

		player.setDeltaMovement(newX, newY, newZ);

		// Prevent fall damage — we're flying, not falling.
		player.fallDistance = 0.0F;
	}
}

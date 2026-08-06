package com.qynl.client.util;

/**
 * Stateless bridge between AimAssist (packet mode) and
 * {@code LocalPlayer.sendPosition}.
 *
 * <p>In packet mode the player's own camera is never moved. Instead, while the
 * aim is armed, {@code LocalPlayer.sendPosition()} briefly swaps the rotation
 * to the silently-aimed values before the movement packet is built and then
 * restores the visual rotation right after. The server therefore sees a
 * natural, aimed look direction while the player keeps full control of the
 * camera.</p>
 */
public final class SilentAim {
	private static boolean armed;
	private static float visualYaw;
	private static float visualPitch;
	private static float silentYaw;
	private static float silentPitch;

	private SilentAim() {
	}

	/** Capture the current visual rotation before the packet is sent. */
	public static void beginSession(float visualYaw, float visualPitch) {
		SilentAim.visualYaw = visualYaw;
		SilentAim.visualPitch = visualPitch;
	}

	public static void set(float yaw, float pitch) {
		silentYaw = yaw;
		silentPitch = pitch;
		armed = true;
	}

	public static void clear() {
		armed = false;
	}

	public static boolean isArmed() {
		return armed;
	}

	public static float getVisualYaw() {
		return visualYaw;
	}

	public static float getVisualPitch() {
		return visualPitch;
	}

	public static float getSilentYaw() {
		return silentYaw;
	}

	public static float getSilentPitch() {
		return silentPitch;
	}
}

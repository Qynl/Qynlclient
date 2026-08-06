package com.qynl.client189;

/**
 * Stateless bridge between AimAssist (Silent mode) and the movement-packet
 * mixin. In Silent mode the player's camera is never moved — the server
 * receives the aimed rotation through the outgoing packet while the camera
 * stays exactly where the player is looking.
 */
public final class SilentAim {
    private static boolean armed;
    private static float visualYaw;
    private static float visualPitch;
    private static float silentYaw;
    private static float silentPitch;

    private SilentAim() {}

    /** Remember the player's real camera rotation before the packet swap. */
    public static void captureVisual(float visualYaw, float visualPitch) {
        SilentAim.visualYaw = visualYaw;
        SilentAim.visualPitch = visualPitch;
    }

    public static void set(float yaw, float pitch) {
        silentYaw = yaw;
        silentPitch = pitch;
        armed = true;
    }

    public static void clear() { armed = false; }
    public static boolean isArmed() { return armed; }
    public static float getVisualYaw() { return visualYaw; }
    public static float getVisualPitch() { return visualPitch; }
    public static float getSilentYaw() { return silentYaw; }
    public static float getSilentPitch() { return silentPitch; }
}

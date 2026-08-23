package com.qynl.client189;

/**
 * Stateless bridge between rotation-spoofing modules (AimAssist Silent mode,
 * Scaffold placement) and the movement-packet mixin. The server receives the
 * spoofed rotation through the outgoing packet while the camera stays exactly
 * where the player is looking.
 *
 * <p>Semantics are <b>one-shot</b>: the mixin applies the armed rotation to
 * the next movement packet and clears the arm. AimAssist re-arms every tick
 * while aiming; Scaffold arms once per placement tick — they can coexist
 * without conflict because whichever arms last before the packet wins.</p>
 */
public final class SilentAim {
    private static boolean armed;
    private static boolean visualCaptured;
    private static float visualYaw;
    private static float visualPitch;
    private static float silentYaw;
    private static float silentPitch;

    private SilentAim() {}

    /** Remember the player's real camera rotation before the packet swap. */
    public static void captureVisual(float visualYaw, float visualPitch) {
        SilentAim.visualYaw = visualYaw;
        SilentAim.visualPitch = visualPitch;
        visualCaptured = true;
    }

    /** Arm the spoofed rotation for the next movement packet. */
    public static void set(float yaw, float pitch) {
        silentYaw = yaw;
        silentPitch = pitch;
        armed = true;
    }

    /** Arm with a pre-captured visual (Scaffold sets the camera to the spoof
     *  for one tick; the mixin restores this captured rotation afterwards). */
    public static void spoofForTick(float realYaw, float realPitch, float spoofYaw, float spoofPitch) {
        captureVisual(realYaw, realPitch);
        set(spoofYaw, spoofPitch);
    }

    public static void clear() {
        armed = false;
        visualCaptured = false;
    }

    public static boolean isArmed() { return armed; }
    public static boolean hasCapturedVisual() { return visualCaptured; }
    public static float getVisualYaw() { return visualYaw; }
    public static float getVisualPitch() { return visualPitch; }
    public static float getSilentYaw() { return silentYaw; }
    public static float getSilentPitch() { return silentPitch; }
}

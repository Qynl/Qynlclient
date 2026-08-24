package com.qynl.client189.access;

/**
 * Injected onto the runtime {@code PlayerMoveC2SPacket} class by the agent so
 * the silent-aim hook can spoof the yaw/pitch/onGround of outgoing movement
 * packets. Replaces the old Mixin accessor; the injected implementations write
 * the obfuscated fields directly.
 */
public interface IPlayerMoveAccess {
    void qynlSetYaw(float yaw);

    void qynlSetPitch(float pitch);

    void qynlSetOnGround(boolean onGround);

    boolean qynlGetOnGround();
}

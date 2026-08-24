package com.qynl.client189;

import com.qynl.injector.agent.TinyMappings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based access to private/protected game members, resolved by
 * name from the bundled yarn mappings at runtime.
 *
 * <p>This replaces the old ASM-injected accessor interfaces. A Java agent in
 * attach mode cannot add interfaces or methods to already-loaded classes
 * (retransformation only permits method-body changes), so every accessor is
 * a plain reflective lookup instead — which works identically in both launch
 * ({@code -javaagent}) and attach mode.</p>
 *
 * <p>The class is remapped like the rest of the client, so
 * {@code MinecraftClient.class}, {@code KeyBinding.class} and
 * {@code PlayerMoveC2SPacket.class} are the obfuscated classes at runtime;
 * only the member names need to come from the mappings. Lookups are cached.</p>
 */
public final class ReflectionAccess {

    private static Method ATTACK;
    private static Field KEY_PRESSED;
    private static Field KEY_CODE;
    private static Field MOVE_YAW;
    private static Field MOVE_PITCH;

    private ReflectionAccess() {
    }

    /** Invokes the private {@code MinecraftClient.doAttack()} (full vanilla attack path). */
    public static void minecraftDoAttack(MinecraftClient client) {
        if (ATTACK == null) {
            try {
                ATTACK = MinecraftClient.class.getDeclaredMethod(
                        TinyMappings.get().mapMethod("net/minecraft/client/MinecraftClient", "doAttack", "()V"));
                ATTACK.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("[Qyn-L] doAttack lookup failed", e);
            }
        }
        try {
            ATTACK.invoke(client);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Qyn-L] doAttack reflection failed", e);
        }
    }

    public static void keyBindingSetPressed(KeyBinding binding, boolean pressed) {
        if (KEY_PRESSED == null) {
            try {
                KEY_PRESSED = KeyBinding.class.getDeclaredField(
                        TinyMappings.get().mapField("net/minecraft/client/options/KeyBinding", "pressed"));
                KEY_PRESSED.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("[Qyn-L] KeyBinding.pressed lookup failed", e);
            }
        }
        try {
            KEY_PRESSED.setBoolean(binding, pressed);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Qyn-L] KeyBinding.pressed reflection failed", e);
        }
    }

    public static void keyBindingSetCode(KeyBinding binding, int code) {
        if (KEY_CODE == null) {
            try {
                KEY_CODE = KeyBinding.class.getDeclaredField(
                        TinyMappings.get().mapField("net/minecraft/client/options/KeyBinding", "code"));
                KEY_CODE.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("[Qyn-L] KeyBinding.code lookup failed", e);
            }
        }
        try {
            KEY_CODE.setInt(binding, code);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Qyn-L] KeyBinding.code reflection failed", e);
        }
    }

    public static void playerMoveSetYaw(PlayerMoveC2SPacket packet, float yaw) {
        if (MOVE_YAW == null) {
            try {
                MOVE_YAW = PlayerMoveC2SPacket.class.getDeclaredField(
                        TinyMappings.get().mapField("net/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket", "yaw"));
                MOVE_YAW.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("[Qyn-L] PlayerMoveC2SPacket.yaw lookup failed", e);
            }
        }
        try {
            MOVE_YAW.setFloat(packet, yaw);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Qyn-L] PlayerMoveC2SPacket.yaw reflection failed", e);
        }
    }

    public static void playerMoveSetPitch(PlayerMoveC2SPacket packet, float pitch) {
        if (MOVE_PITCH == null) {
            try {
                MOVE_PITCH = PlayerMoveC2SPacket.class.getDeclaredField(
                        TinyMappings.get().mapField("net/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket", "pitch"));
                MOVE_PITCH.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("[Qyn-L] PlayerMoveC2SPacket.pitch lookup failed", e);
            }
        }
        try {
            MOVE_PITCH.setFloat(packet, pitch);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("[Qyn-L] PlayerMoveC2SPacket.pitch reflection failed", e);
        }
    }
}

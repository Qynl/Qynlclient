package com.qynl.client.agent;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import java.util.Locale;

/**
 * Explicit input surface for an agent. Input is gated to single-player worlds
 * so the adapter cannot silently become a multiplayer automation client.
 */
public final class AgentInput {
    private AgentInput() {}

    public static boolean allowed(MinecraftClient client) {
        return client != null && client.player != null && client.world != null && client.isInSingleplayer();
    }

    public static boolean setMovement(MinecraftClient client, String direction, boolean pressed) {
        if (!allowed(client) || direction == null) return false;

        KeyBinding binding = switch (direction.toLowerCase(Locale.ROOT)) {
            case "forward", "w" -> client.options.forwardKey;
            case "back", "backward", "s" -> client.options.backKey;
            case "left", "a" -> client.options.leftKey;
            case "right", "d" -> client.options.rightKey;
            case "jump", "space" -> client.options.jumpKey;
            case "sneak", "shift" -> client.options.sneakKey;
            case "sprint" -> client.options.sprintKey;
            default -> null;
        };

        if (binding == null) return false;
        KeyBinding.setKeyPressed(binding.getBoundKey(), pressed);
        return true;
    }

    public static void releaseAll(MinecraftClient client) {
        if (!allowed(client)) return;
        KeyBinding.unpressAll();
    }

    public record LookDelta(double yaw, double pitch) {}
}
